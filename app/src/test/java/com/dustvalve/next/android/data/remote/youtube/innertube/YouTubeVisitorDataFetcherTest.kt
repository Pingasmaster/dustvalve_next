package com.dustvalve.next.android.data.remote.youtube.innertube

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class YouTubeVisitorDataFetcherTest {

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

    @Test fun `extracts visitorData and clientVersion from ytcfg`() = runTest {
        primary.enqueue(MockResponse().setBody(
            """<script>ytcfg.set({"INNERTUBE_CONTEXT":{"client":{"visitorData":"VD_VALUE_42","clientName":"WEB"}},"INNERTUBE_CLIENT_VERSION":"2.20260999.99.99"});</script>"""
        ))

        val cfg = fetcher.get()
        assertThat(cfg.visitorData).isEqualTo("VD_VALUE_42")
        assertThat(cfg.clientVersion).isEqualTo("2.20260999.99.99")

        val request = primary.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        val cookie = request.headers["Cookie"] ?: ""
        assertThat(cookie).contains("SOCS=CAI")
        assertThat(cookie).contains("CONSENT=YES+1")
    }

    @Test fun `landing GET sends navigation-style headers`() = runTest {
        primary.enqueue(MockResponse().setBody(
            """<script>ytcfg.set({"VISITOR_DATA":"x","INNERTUBE_CLIENT_VERSION":"2.0.0"});</script>"""
        ))
        fetcher.get()
        val request = primary.takeRequest()
        assertThat(request.headers["Sec-Fetch-Mode"]).isEqualTo("navigate")
        assertThat(request.headers["Upgrade-Insecure-Requests"]).isEqualTo("1")
        assertThat(request.headers["Accept"]).contains("text/html")
    }

    @Test fun `caches across calls`() = runTest {
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
        assertThat(fetcher.get().visitorData).isEqualTo("second")
        assertThat(primary.requestCount).isEqualTo(2)
    }

    @Test fun `falls back to default clientVersion when ytcfg missing it`() = runTest {
        primary.enqueue(MockResponse().setBody(
            """<script>ytcfg.set({"VISITOR_DATA":"x"});</script>"""
        ))
        val cfg = fetcher.get()
        assertThat(cfg.visitorData).isEqualTo("x")
        assertThat(cfg.clientVersion).isEqualTo(
            YouTubeVisitorDataFetcher.DEFAULT_CLIENT_VERSION
        )
    }

    @Test fun `falls back to secondary URL when primary has no ytcfg`() = runTest {
        primary.enqueue(MockResponse().setBody("<html>browser deprecated stub</html>"))
        fallback.enqueue(MockResponse().setBody(
            """<script>ytcfg.set({"VISITOR_DATA":"fallback_vd","INNERTUBE_CLIENT_VERSION":"2.0"});</script>"""
        ))

        val cfg = fetcher.get()
        assertThat(cfg.visitorData).isEqualTo("fallback_vd")
        assertThat(primary.requestCount).isEqualTo(1)
        assertThat(fallback.requestCount).isEqualTo(1)
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
        assertThat(msg).contains("head=")
    }

    @Test fun `falls back when primary returns non-2xx`() = runTest {
        primary.enqueue(MockResponse().setResponseCode(503))
        fallback.enqueue(MockResponse().setBody(
            """<script>ytcfg.set({"VISITOR_DATA":"after_503"});</script>"""
        ))
        val cfg = fetcher.get()
        assertThat(cfg.visitorData).isEqualTo("after_503")
    }

    private class TestableFetcher(
        client: OkHttpClient,
        primaryUrl: String,
        fallbackUrl: String,
    ) : YouTubeVisitorDataFetcher(client) {
        override val landingUrl: String = primaryUrl
        override val fallbackLandingUrl: String = fallbackUrl
    }
}
