package com.dustvalve.next.android.data.remote.youtubemusic

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class YouTubeMusicVisitorDataFetcherTest {

    private lateinit var primary: MockWebServer
    private lateinit var fallback: MockWebServer
    private lateinit var fetcher: TestableFetcher

    @Before fun setUp() {
        primary = MockWebServer().also { it.start() }
        fallback = MockWebServer().also { it.start() }
        fetcher = TestableFetcher(
            client = OkHttpClient(),
            primaryUrl = primary.url("/").toString(),
            fallbackUrl = fallback.url("/").toString(),
        )
    }

    @After fun tearDown() {
        primary.shutdown()
        fallback.shutdown()
    }

    @Test fun `extracts visitorData and clientVersion from primary ytcfg`() = runTest {
        primary.enqueue(MockResponse().setBody(
            """<script>ytcfg.set({"INNERTUBE_CONTEXT":{"client":{"visitorData":"VD_VALUE_42","clientName":"WEB_REMIX"}},"INNERTUBE_CLIENT_VERSION":"1.20260999.99.99"});</script>"""
        ))

        val cfg = fetcher.get()
        assertThat(cfg.visitorData).isEqualTo("VD_VALUE_42")
        assertThat(cfg.clientVersion).isEqualTo("1.20260999.99.99")

        val request = primary.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(fallback.requestCount).isEqualTo(0) // never hit
    }

    @Test fun `landing GET sends navigation-style headers and dual consent cookies`() = runTest {
        primary.enqueue(MockResponse().setBody(
            """<script>ytcfg.set({"VISITOR_DATA":"x","INNERTUBE_CLIENT_VERSION":"1.0.0"});</script>"""
        ))
        fetcher.get()
        val request = primary.takeRequest()
        assertThat(request.headers["Sec-Fetch-Mode"]).isEqualTo("navigate")
        assertThat(request.headers["Sec-Fetch-Site"]).isEqualTo("none")
        assertThat(request.headers["Sec-Fetch-Dest"]).isEqualTo("document")
        assertThat(request.headers["Upgrade-Insecure-Requests"]).isEqualTo("1")
        assertThat(request.headers["Accept"]).contains("text/html")
        // Dual consent: SOCS=CAI AND CONSENT=YES+1 sent together.
        val cookie = request.headers["Cookie"] ?: ""
        assertThat(cookie).contains("SOCS=CAI")
        assertThat(cookie).contains("CONSENT=YES+1")
    }

    @Test fun `caches across calls (only one network request)`() = runTest {
        primary.enqueue(MockResponse().setBody(
            """<script>ytcfg.set({"VISITOR_DATA":"only_once","INNERTUBE_CLIENT_VERSION":"1.0.0"});</script>"""
        ))
        val a = fetcher.get()
        val b = fetcher.get()
        assertThat(a.visitorData).isEqualTo("only_once")
        assertThat(b.visitorData).isEqualTo("only_once")
        assertThat(primary.requestCount).isEqualTo(1)
    }

    @Test fun `invalidate forces refetch`() = runTest {
        primary.enqueue(MockResponse().setBody(
            """<script>ytcfg.set({"VISITOR_DATA":"first","INNERTUBE_CLIENT_VERSION":"1.0.0"});</script>"""
        ))
        primary.enqueue(MockResponse().setBody(
            """<script>ytcfg.set({"VISITOR_DATA":"second","INNERTUBE_CLIENT_VERSION":"2.0.0"});</script>"""
        ))

        assertThat(fetcher.get().visitorData).isEqualTo("first")
        fetcher.invalidate()
        // "second" body has clientVersion "2.0.0" — NOT a 1.x WEB_REMIX
        // version, so the fetcher should fall back to DEFAULT_CLIENT_VERSION.
        val cfg2 = fetcher.get()
        assertThat(cfg2.visitorData).isEqualTo("second")
        assertThat(cfg2.clientVersion)
            .isEqualTo(YouTubeMusicVisitorDataFetcher.DEFAULT_CLIENT_VERSION)
        assertThat(primary.requestCount).isEqualTo(2)
    }

    @Test fun `falls back to default clientVersion when ytcfg missing it`() = runTest {
        primary.enqueue(MockResponse().setBody(
            """<script>ytcfg.set({"VISITOR_DATA":"x"});</script>"""
        ))
        val cfg = fetcher.get()
        assertThat(cfg.visitorData).isEqualTo("x")
        assertThat(cfg.clientVersion)
            .isEqualTo(YouTubeMusicVisitorDataFetcher.DEFAULT_CLIENT_VERSION)
    }

    @Test fun `falls back to www_youtube_com when primary has no ytcfg`() = runTest {
        // Browser-deprecated stub — primary has no ytcfg at all.
        primary.enqueue(MockResponse().setBody(
            "<html><body>Browser is deprecated</body></html>"
        ))
        // Fallback (www.youtube.com mock) returns a normal WEB ytcfg.
        fallback.enqueue(MockResponse().setBody(
            """<script>ytcfg.set({"INNERTUBE_CONTEXT":{"client":{"visitorData":"www_VD","clientName":"WEB"}},"INNERTUBE_CLIENT_VERSION":"2.20260424.01.00"});</script>"""
        ))

        val cfg = fetcher.get()
        assertThat(cfg.visitorData).isEqualTo("www_VD")
        // Critical: must use the WEB_REMIX default, NOT the scraped WEB
        // version (2.x), or YT Music's /browse will mis-classify the client.
        assertThat(cfg.clientVersion)
            .isEqualTo(YouTubeMusicVisitorDataFetcher.DEFAULT_CLIENT_VERSION)
        assertThat(primary.requestCount).isEqualTo(1)
        assertThat(fallback.requestCount).isEqualTo(1)
    }

    @Test fun `falls back when primary is non-2xx`() = runTest {
        primary.enqueue(MockResponse().setResponseCode(503))
        fallback.enqueue(MockResponse().setBody(
            """<script>ytcfg.set({"VISITOR_DATA":"vd_after_503"});</script>"""
        ))

        val cfg = fetcher.get()
        assertThat(cfg.visitorData).isEqualTo("vd_after_503")
    }

    @Test fun `error message includes diagnostics when both URLs fail`() = runTest {
        primary.enqueue(MockResponse().setBody("<html>nothing here</html>"))
        fallback.enqueue(MockResponse().setBody("<html>also nothing</html>"))

        val ex = runCatching { fetcher.get() }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        val msg = ex!!.message ?: ""
        assertThat(msg).contains("missing ytcfg.set block")
        assertThat(msg).contains("primary=HTTP 200")
        assertThat(msg).contains("fallback=HTTP 200")
        assertThat(msg).contains("head=") // first 120 chars of primary body
    }

    @Test fun `landing fetch does NOT send jar cookies (regression for stale-cookie ytcfg failure)`() = runTest {
        // A prior session left a SID cookie (or similar) in the shared jar.
        // Without the cookie-strip fix, OkHttp's BridgeInterceptor rewrites
        // the request's Cookie header to the jar's content, wiping the
        // manual SOCS=CAI value and causing YT Music to serve a signed-in /
        // consent variant whose response omits the ytcfg.set block.
        val jar = object : CookieJar {
            override fun loadForRequest(url: HttpUrl): List<Cookie> = listOf(
                Cookie.Builder()
                    .name("SID").value("stale_session_value").domain(url.host).path("/")
                    .build(),
            )
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {}
        }
        val clientWithJar = OkHttpClient.Builder().cookieJar(jar).build()
        val fetcher = TestableFetcher(
            client = clientWithJar,
            primaryUrl = primary.url("/").toString(),
            fallbackUrl = fallback.url("/").toString(),
        )

        primary.enqueue(MockResponse().setBody(
            """<script>ytcfg.set({"VISITOR_DATA":"post_fix","INNERTUBE_CLIENT_VERSION":"1.0.0"});</script>""",
        ))

        val cfg = fetcher.get()
        assertThat(cfg.visitorData).isEqualTo("post_fix")

        val request = primary.takeRequest()
        val cookie = request.headers["Cookie"] ?: ""
        assertThat(cookie).contains("SOCS=CAI")
        assertThat(cookie).doesNotContain("SID=")
    }

    private class TestableFetcher(
        client: OkHttpClient,
        primaryUrl: String,
        fallbackUrl: String,
    ) : YouTubeMusicVisitorDataFetcher(client) {
        override val landingUrl: String = primaryUrl
        override val fallbackLandingUrl: String = fallbackUrl
    }
}
