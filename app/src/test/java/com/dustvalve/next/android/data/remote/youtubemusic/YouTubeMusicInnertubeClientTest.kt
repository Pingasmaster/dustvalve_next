package com.dustvalve.next.android.data.remote.youtubemusic

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class YouTubeMusicInnertubeClientTest {

    private lateinit var server: MockWebServer
    private lateinit var visitor: YouTubeMusicVisitorDataFetcher
    private lateinit var client: YouTubeMusicInnertubeClient
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        visitor = mockk()
        coEvery { visitor.get() } returns YouTubeMusicVisitorDataFetcher.VisitorConfig(
            visitorData = "MY_VISITOR_DATA_TOKEN",
            clientVersion = "1.20260417.03.00",
        )
        client = TestableInnertubeClient(OkHttpClient(), visitor, server.url("/").toString())
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `browse sends required headers and visitor data in body`() = runTest {
        server.enqueue(MockResponse().setBody("""{"contents":{}}"""))

        client.browse(browseId = "FEmusic_home")
        val recorded = server.takeRequest()

        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/browse?prettyPrint=false")

        val h = recorded.headers
        assertThat(h["Origin"]).isEqualTo("https://music.youtube.com")
        assertThat(h["X-Origin"]).isEqualTo("https://music.youtube.com")
        assertThat(h["Referer"]).isEqualTo("https://music.youtube.com/")
        assertThat(h["X-YouTube-Client-Name"]).isEqualTo("67")
        assertThat(h["X-YouTube-Client-Version"]).isEqualTo("1.20260417.03.00")
        assertThat(h["X-Goog-Api-Format-Version"]).isEqualTo("1")
        assertThat(h["X-Goog-Visitor-Id"]).isEqualTo("MY_VISITOR_DATA_TOKEN")
        assertThat(h["Cookie"]).isEqualTo("SOCS=CAI")
        assertThat(h["Content-Type"]).contains("application/json")

        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        val client = body["context"]!!.jsonObject["client"]!!.jsonObject
        assertThat(client["clientName"]!!.toString()).isEqualTo("\"WEB_REMIX\"")
        assertThat(client["clientVersion"]!!.toString()).isEqualTo("\"1.20260417.03.00\"")
        assertThat(client["visitorData"]!!.toString()).isEqualTo("\"MY_VISITOR_DATA_TOKEN\"")
        assertThat(client["hl"]!!.toString()).isEqualTo("\"en\"")
        assertThat(client["gl"]!!.toString()).isEqualTo("\"US\"")
        assertThat(body["browseId"]!!.toString()).isEqualTo("\"FEmusic_home\"")
        assertThat(body.containsKey("params")).isFalse()
    }

    @Test fun `browse includes params when supplied`() = runTest {
        server.enqueue(MockResponse().setBody("""{"contents":{}}"""))

        client.browse(browseId = "FEmusic_home", params = "PARAMS_X")
        val recorded = server.takeRequest()
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertThat(body["params"]!!.toString()).isEqualTo("\"PARAMS_X\"")
    }

    @Test fun `search posts to search endpoint with query and params`() = runTest {
        server.enqueue(MockResponse().setBody("""{"contents":{}}"""))

        client.search(query = "daft punk", params = "EgWKAQII")
        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/search?prettyPrint=false")

        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertThat(body["query"]!!.toString()).isEqualTo("\"daft punk\"")
        assertThat(body["params"]!!.toString()).isEqualTo("\"EgWKAQII\"")
    }

    @Test fun `non-success response throws IllegalStateException with code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        val ex = runCatching { client.browse(browseId = "X") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        assertThat(ex!!.message).contains("HTTP 500")
        assertThat(ex.message).contains("oops")
    }

    @Test fun `empty body throws IllegalStateException`() = runTest {
        server.enqueue(MockResponse().setBody(""))
        val ex = runCatching { client.browse(browseId = "X") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        assertThat(ex!!.message).contains("returned empty body")
    }

    @Test fun `browseContinuation appends continuation to URL and includes context only`() = runTest {
        server.enqueue(MockResponse().setBody("""{"x":1}"""))
        client.browseContinuation("CONT_TOKEN")
        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/browse?prettyPrint=false&continuation=CONT_TOKEN&type=next")

        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertThat(body.keys).containsExactly("context")
    }

    /**
     * Subclasses YouTubeMusicInnertubeClient so the BASE_URL points at the
     * test MockWebServer instead of music.youtube.com.
     */
    private class TestableInnertubeClient(
        sharedOkHttpClient: OkHttpClient,
        visitorDataFetcher: YouTubeMusicVisitorDataFetcher,
        private val mockBaseUrl: String,
    ) : YouTubeMusicInnertubeClient(sharedOkHttpClient, visitorDataFetcher) {
        // We override only the URL by constructing the real OkHttp request via
        // reflection-free means: shadow the BASE_URL by overriding the post()
        // entrypoints. Easier: re-implement browse/search/continuation against
        // mockBaseUrl. But that duplicates request shape. Best instead: open
        // the BASE_URL via a protected hook in the parent. See implementation
        // change in YouTubeMusicInnertubeClient.
        override val baseUrl: String get() = mockBaseUrl.trimEnd('/')
    }
}
