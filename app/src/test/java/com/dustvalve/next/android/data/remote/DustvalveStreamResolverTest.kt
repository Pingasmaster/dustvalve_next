package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.Track
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Before
import org.junit.Test

class DustvalveStreamResolverTest {

    private lateinit var setup: TlsTestServer.Setup
    private lateinit var resolver: DustvalveStreamResolver

    @Before fun setUp() {
        setup = TlsTestServer.start()
        resolver = DustvalveStreamResolver(setup.client)
    }

    @After fun tearDown() {
        setup.server.shutdown()
    }

    private fun baseTrack(
        id: String = "t1",
        title: String = "Song",
        trackNumber: Int = 1,
        streamUrl: String? = null,
    ) = Track(
        id = id, albumId = "al", title = title, artist = "A",
        trackNumber = trackNumber, duration = 100f, streamUrl = streamUrl,
        artUrl = "", albumTitle = "Alb",
    )

    @Test fun `already set streamUrl short-circuits`() = runTest {
        val t = baseTrack(streamUrl = "https://a/b.mp3")
        val result = resolver.resolveStreamUrl(t, albumPageUrl = null)
        assertThat(result).isEqualTo("https://a/b.mp3")
        assertThat(setup.server.requestCount).isEqualTo(0)
    }

    @Test fun `null album page url with null stream url returns null`() = runTest {
        val t = baseTrack(streamUrl = null)
        assertThat(resolver.resolveStreamUrl(t, albumPageUrl = null)).isNull()
    }

    @Test fun `title match preferred over track number`() = runTest {
        val html = """<html><script>var TralbumData = {
          "trackinfo":[
            {"track_num":1,"title":"Other","file":{"mp3-128":"https://stream/other.mp3"}},
            {"track_num":5,"title":"Song","file":{"mp3-128":"https://stream/song.mp3"}}
          ]
        };</script></html>"""
        setup.server.enqueue(MockResponse().setBody(html))

        val url = resolver.resolveStreamUrl(
            baseTrack(title = "Song", trackNumber = 1),
            albumPageUrl = setup.url("/album/x"),
        )
        assertThat(url).isEqualTo("https://stream/song.mp3")
    }

    @Test fun `title match is case insensitive`() = runTest {
        val html = """<html><script>var TralbumData = {
          "trackinfo":[
            {"track_num":1,"title":"Some Song","file":{"mp3-128":"https://stream/song.mp3"}}
          ]
        };</script></html>"""
        setup.server.enqueue(MockResponse().setBody(html))

        val url = resolver.resolveStreamUrl(
            baseTrack(title = "SOME SONG"),
            albumPageUrl = setup.url("/album/x"),
        )
        assertThat(url).isEqualTo("https://stream/song.mp3")
    }

    @Test fun `fallback to track number when title missing`() = runTest {
        val html = """<html><script>var TralbumData = {
          "trackinfo":[
            {"track_num":2,"title":"Different","file":{"mp3-128":"https://stream/two.mp3"}}
          ]
        };</script></html>"""
        setup.server.enqueue(MockResponse().setBody(html))

        val url = resolver.resolveStreamUrl(
            baseTrack(title = "MissingTitle", trackNumber = 2),
            albumPageUrl = setup.url("/album/x"),
        )
        assertThat(url).isEqualTo("https://stream/two.mp3")
    }

    @Test fun `returns null when no TralbumData found`() = runTest {
        setup.server.enqueue(MockResponse().setBody("<html>nothing here</html>"))
        val url = resolver.resolveStreamUrl(
            baseTrack(),
            albumPageUrl = setup.url("/album/x"),
        )
        assertThat(url).isNull()
    }

    @Test fun `http error throws IOException`() = runTest {
        setup.server.enqueue(MockResponse().setResponseCode(500))
        try {
            resolver.resolveStreamUrl(baseTrack(), albumPageUrl = setup.url("/album/x"))
            error("expected IOException")
        } catch (e: java.io.IOException) {
            assertThat(e.message).contains("500")
        }
    }
}
