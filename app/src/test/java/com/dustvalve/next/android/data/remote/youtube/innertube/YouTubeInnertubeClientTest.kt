package com.dustvalve.next.android.data.remote.youtube.innertube

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class YouTubeInnertubeClientTest {

    private lateinit var server: MockWebServer
    private lateinit var visitor: YouTubeVisitorDataFetcher
    private lateinit var client: YouTubeInnertubeClient
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        visitor = mockk()
        coEvery { visitor.get() } returns YouTubeVisitorDataFetcher.VisitorConfig(
            visitorData = "MY_VISITOR_DATA_TOKEN",
            clientVersion = "2.20260421.00.00",
        )
        client = TestableInnertubeClient(
            sharedOkHttpClient = OkHttpClient(),
            visitorDataFetcher = visitor,
            mockBaseUrl = server.url("/").toString(),
        )
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun playerOk(): String = """
        {
          "streamingData": {
            "adaptiveFormats": [
              {"mimeType":"audio/webm; codecs=\"opus\"","bitrate":128000,"url":"https://r1.example/audio.webm"}
            ]
          },
          "videoDetails": {"title":"X","author":"A","lengthSeconds":"10","thumbnail":{"thumbnails":[]}}
        }
    """.trimIndent()

    private fun playerNoAudio(): String = """
        {"streamingData":{"adaptiveFormats":[]}, "playabilityStatus":{"status":"OK"}}
    """.trimIndent()

    @Test fun `player sends ANDROID_VR headers and body on first attempt`() = runTest {
        server.enqueue(MockResponse().setBody(playerOk()))

        client.player(videoId = "vid12345678")
        val recorded = server.takeRequest()

        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/player?prettyPrint=false")

        val h = recorded.headers
        assertThat(h["X-YouTube-Client-Name"]).isEqualTo("28")
        assertThat(h["X-YouTube-Client-Version"]).isEqualTo("1.61.48")
        assertThat(h["X-Origin"]).isEqualTo("https://www.youtube.com")
        assertThat(h["Origin"]).isEqualTo("https://www.youtube.com")
        assertThat(h["Referer"]).isEqualTo("https://www.youtube.com")
        assertThat(h["X-Goog-Api-Format-Version"]).isEqualTo("1")
        assertThat(h["X-Goog-Visitor-Id"]).isEqualTo("MY_VISITOR_DATA_TOKEN")
        assertThat(h["User-Agent"]).contains("com.google.android.apps.youtube.vr.oculus/1.61.48")
        assertThat(h["Content-Type"]).contains("application/json")

        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        val ctxClient = body["context"]!!.jsonObject["client"]!!.jsonObject
        assertThat(ctxClient["clientName"]!!.toString()).isEqualTo("\"ANDROID_VR\"")
        assertThat(ctxClient["clientVersion"]!!.toString()).isEqualTo("\"1.61.48\"")
        assertThat(ctxClient["androidSdkVersion"]!!.toString()).isEqualTo("32")
        assertThat(ctxClient["osName"]!!.toString()).isEqualTo("\"Android\"")
        assertThat(ctxClient["visitorData"]!!.toString()).isEqualTo("\"MY_VISITOR_DATA_TOKEN\"")
        assertThat(body["videoId"]!!.toString()).isEqualTo("\"vid12345678\"")
        assertThat(body["contentCheckOk"]!!.toString()).isEqualTo("true")
        assertThat(body["racyCheckOk"]!!.toString()).isEqualTo("true")
    }

    @Test fun `player cascades to IOS when ANDROID_VR returns no audio formats`() = runTest {
        server.enqueue(MockResponse().setBody(playerNoAudio()))
        server.enqueue(MockResponse().setBody(playerOk()))

        client.player(videoId = "vid12345678")
        val first = server.takeRequest()
        val second = server.takeRequest()

        assertThat(first.headers["X-YouTube-Client-Name"]).isEqualTo("28")  // ANDROID_VR
        assertThat(second.headers["X-YouTube-Client-Name"]).isEqualTo("5")  // IOS
        assertThat(second.headers["User-Agent"]).contains("com.google.ios.youtube/21.03.1")

        val secondBody = json.parseToJsonElement(second.body.readUtf8()).jsonObject
        val ctxClient = secondBody["context"]!!.jsonObject["client"]!!.jsonObject
        assertThat(ctxClient["clientName"]!!.toString()).isEqualTo("\"IOS\"")
        assertThat(ctxClient["deviceModel"]!!.toString()).isEqualTo("\"iPhone16,2\"")
        assertThat(ctxClient["osVersion"]!!.toString()).isEqualTo("\"18.2.21C5054b\"")
    }

    @Test fun `player throws when both ANDROID_VR and IOS fail to provide audio`() = runTest {
        server.enqueue(MockResponse().setBody(playerNoAudio()))
        server.enqueue(MockResponse().setBody(playerNoAudio()))
        val ex = runCatching { client.player("vid12345678") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        assertThat(ex!!.message).contains("vid12345678")
        assertThat(ex.message).contains("IOS")
    }

    @Test fun `player non-2xx is treated as a failed client and falls through`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        server.enqueue(MockResponse().setBody(playerOk()))
        client.player("vid12345678")
        // If we got here, IOS recovered after ANDROID_VR's 500.
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test fun `search uses WEB client and sends query and params`() = runTest {
        server.enqueue(MockResponse().setBody("""{"contents":{}}"""))

        client.search(query = "daft punk", params = "EgIQAQ")
        val recorded = server.takeRequest()

        assertThat(recorded.path).isEqualTo("/search?prettyPrint=false")
        assertThat(recorded.headers["X-YouTube-Client-Name"]).isEqualTo("1")
        assertThat(recorded.headers["X-YouTube-Client-Version"]).isEqualTo("2.20260421.00.00")
        assertThat(recorded.headers["User-Agent"]).contains("Mozilla/5.0")

        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertThat(body["query"]!!.toString()).isEqualTo("\"daft punk\"")
        assertThat(body["params"]!!.toString()).isEqualTo("\"EgIQAQ\"")
        assertThat(body["context"]!!.jsonObject["client"]!!.jsonObject["clientName"]!!.toString())
            .isEqualTo("\"WEB\"")
    }

    @Test fun `search omits params when not supplied`() = runTest {
        server.enqueue(MockResponse().setBody("""{"contents":{}}"""))
        client.search(query = "q")
        val recorded = server.takeRequest()
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertThat(body.containsKey("params")).isFalse()
    }

    @Test fun `browse channelId uses WEB client`() = runTest {
        server.enqueue(MockResponse().setBody("""{"contents":{}}"""))
        client.browse(browseId = "UCabcdefghijklmnopqrstuv", params = "EgZ2aWRlb3PyBgQKAjoA")
        val recorded = server.takeRequest()
        assertThat(recorded.headers["X-YouTube-Client-Name"]).isEqualTo("1") // WEB
        assertThat(recorded.headers["X-Origin"]).isEqualTo("https://www.youtube.com")
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertThat(body["browseId"]!!.toString()).isEqualTo("\"UCabcdefghijklmnopqrstuv\"")
        assertThat(body["params"]!!.toString()).isEqualTo("\"EgZ2aWRlb3PyBgQKAjoA\"")
    }

    @Test fun `browse VL playlist uses MWEB client and m subdomain`() = runTest {
        server.enqueue(MockResponse().setBody("""{"contents":{}}"""))
        client.browse(browseId = "VLPLabcdefghijkl")
        val recorded = server.takeRequest()
        assertThat(recorded.headers["X-YouTube-Client-Name"]).isEqualTo("2") // MWEB
        assertThat(recorded.headers["X-Origin"]).isEqualTo("https://m.youtube.com")
        assertThat(recorded.headers["Referer"]).isEqualTo("https://m.youtube.com")
        // Path doesn't carry the host (MockWebServer rewrites), but the request was routed.
    }

    @Test fun `browseContinuation includes continuation in URL and uses MWEB`() = runTest {
        server.enqueue(MockResponse().setBody("""{"x":1}"""))
        client.browseContinuation("CONT_TOKEN_42")
        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/browse?prettyPrint=false&continuation=CONT_TOKEN_42&type=next")
        assertThat(recorded.headers["X-YouTube-Client-Name"]).isEqualTo("2")
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertThat(body.keys).containsExactly("context")
    }

    @Test fun `next uses MWEB and includes videoId only by default`() = runTest {
        server.enqueue(MockResponse().setBody("""{"contents":{}}"""))
        client.next(videoId = "vid12345678")
        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/next?prettyPrint=false")
        assertThat(recorded.headers["X-YouTube-Client-Name"]).isEqualTo("2")
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertThat(body["videoId"]!!.toString()).isEqualTo("\"vid12345678\"")
        assertThat(body.containsKey("playlistId")).isFalse()
    }

    @Test fun `next includes playlistId when provided`() = runTest {
        server.enqueue(MockResponse().setBody("""{"contents":{}}"""))
        client.next(videoId = "vid", playlistId = "PLABC")
        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertThat(body["playlistId"]!!.toString()).isEqualTo("\"PLABC\"")
    }

    @Test fun `non-2xx on search throws IllegalStateException with code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        val ex = runCatching { client.search(query = "X") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        assertThat(ex!!.message).contains("HTTP 500")
        assertThat(ex.message).contains("oops")
    }

    @Test fun `empty body throws IllegalStateException`() = runTest {
        server.enqueue(MockResponse().setBody(""))
        val ex = runCatching { client.search(query = "X") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        assertThat(ex!!.message).contains("returned empty body")
    }

    /**
     * Subclasses YouTubeInnertubeClient so both base URLs point at the test
     * MockWebServer (m and www both go to the same host since MockWebServer
     * answers any path).
     */
    private class TestableInnertubeClient(
        sharedOkHttpClient: OkHttpClient,
        visitorDataFetcher: YouTubeVisitorDataFetcher,
        private val mockBaseUrl: String,
    ) : YouTubeInnertubeClient(sharedOkHttpClient, visitorDataFetcher) {
        override val baseUrlWww: String get() = mockBaseUrl.trimEnd('/')
        override val baseUrlM: String get() = mockBaseUrl.trimEnd('/')
    }
}
