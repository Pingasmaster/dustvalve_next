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

    private lateinit var server: MockWebServer
    private lateinit var fetcher: TestableFetcher

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        fetcher = TestableFetcher(OkHttpClient(), server.url("/").toString())
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `extracts visitorData and clientVersion from ytcfg`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                <html><body>
                <script>ytcfg.set({"INNERTUBE_CONTEXT":{"client":{"visitorData":"VD_VALUE_42","clientName":"WEB"}},"INNERTUBE_CLIENT_VERSION":"2.20260999.99.99"});</script>
                </body></html>
                """.trimIndent()
            )
        )

        val cfg = fetcher.get()
        assertThat(cfg.visitorData).isEqualTo("VD_VALUE_42")
        assertThat(cfg.clientVersion).isEqualTo("2.20260999.99.99")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.headers["Cookie"]).isEqualTo("SOCS=CAI")
    }

    @Test fun `caches across calls`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """<script>ytcfg.set({"VISITOR_DATA":"only_once","INNERTUBE_CLIENT_VERSION":"1.0.0"});</script>"""
            )
        )

        val a = fetcher.get()
        val b = fetcher.get()
        assertThat(a.visitorData).isEqualTo("only_once")
        assertThat(b.visitorData).isEqualTo("only_once")
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test fun `invalidate forces refetch`() = runTest {
        server.enqueue(MockResponse().setBody(
            """<script>ytcfg.set({"VISITOR_DATA":"first","INNERTUBE_CLIENT_VERSION":"1.0.0"});</script>"""
        ))
        server.enqueue(MockResponse().setBody(
            """<script>ytcfg.set({"VISITOR_DATA":"second","INNERTUBE_CLIENT_VERSION":"2.0.0"});</script>"""
        ))

        assertThat(fetcher.get().visitorData).isEqualTo("first")
        fetcher.invalidate()
        assertThat(fetcher.get().visitorData).isEqualTo("second")
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test fun `falls back to default clientVersion when ytcfg missing it`() = runTest {
        server.enqueue(MockResponse().setBody(
            """<script>ytcfg.set({"VISITOR_DATA":"x"});</script>"""
        ))
        val cfg = fetcher.get()
        assertThat(cfg.visitorData).isEqualTo("x")
        assertThat(cfg.clientVersion).isEqualTo(
            YouTubeVisitorDataFetcher.DEFAULT_CLIENT_VERSION
        )
    }

    @Test fun `throws when ytcfg block missing`() = runTest {
        server.enqueue(MockResponse().setBody("<html>no config here</html>"))
        val ex = runCatching { fetcher.get() }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        assertThat(ex!!.message).contains("missing ytcfg.set block")
    }

    @Test fun `throws when ytcfg block has no visitorData`() = runTest {
        server.enqueue(MockResponse().setBody(
            """<script>ytcfg.set({"INNERTUBE_CLIENT_VERSION":"1.0.0","other":"thing"});</script>"""
        ))
        val ex = runCatching { fetcher.get() }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        assertThat(ex!!.message).contains("missing VISITOR_DATA")
    }

    @Test fun `throws on non-2xx response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val ex = runCatching { fetcher.get() }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        assertThat(ex!!.message).contains("HTTP 503")
    }

    private class TestableFetcher(
        client: OkHttpClient,
        override val landingUrl: String,
    ) : YouTubeVisitorDataFetcher(client)
}
