package com.dustvalve.next.android.data.remote.youtube.innertube

import com.dustvalve.next.android.domain.model.TrackSource
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class YouTubePlaylistParserTest {

    private val parser = YouTubePlaylistParser()
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    @Test fun `parses real MWEB playlist response into Tracks`() {
        val page = parser.parse(
            Fixtures.load("playlist_mweb.json"),
            playlistId = "PLKBWcWelelBCDoz6LpsUd2Qj6dCl9OmMZ",
        )
        assertThat(page.tracks).isNotEmpty()
        assertThat(page.title).isNotNull()
        // First track sanity
        with(page.tracks.first()) {
            assertThat(id).startsWith("yt_")
            assertThat(title).isNotEmpty()
            assertThat(artist).isNotEmpty()
            assertThat(duration).isGreaterThan(0f)
            assertThat(streamUrl).startsWith("https://www.youtube.com/watch?v=")
            assertThat(source).isEqualTo(TrackSource.YOUTUBE)
            assertThat(albumId).isEqualTo("yt_playlist_PLKBWcWelelBCDoz6LpsUd2Qj6dCl9OmMZ")
        }
        // Track numbers should be sequential
        page.tracks.forEachIndexed { idx, t ->
            assertThat(t.trackNumber).isEqualTo(idx + 1)
        }
    }

    @Test fun `extracts title from pageHeaderRenderer`() {
        val page = parser.parse(
            Fixtures.load("playlist_mweb.json"),
            playlistId = "PLKBWcWelelBCDoz6LpsUd2Qj6dCl9OmMZ",
        )
        assertThat(page.title).isEqualTo("RICK ASTLEY SONG PLAYLIST.")
    }

    @Test fun `parseContinuation walks appendContinuationItemsAction`() {
        val cont = json.parseToJsonElement(
            """
            {"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[
              {"playlistVideoRenderer":{
                "videoId":"vidcontid001",
                "title":{"runs":[{"text":"Cont Title"}]},
                "shortBylineText":{"runs":[{"text":"Cont Artist"}]},
                "lengthSeconds":"123",
                "thumbnail":{"thumbnails":[{"url":"https://t/x","width":120}]}
              }},
              {"continuationItemRenderer":{"continuationEndpoint":{
                "continuationCommand":{"token":"NEXT_CONT"}
              }}}
            ]}}]}
            """.trimIndent()
        )
        val page = parser.parseContinuation(cont, "PL_X", startIndex = 22)
        assertThat(page.tracks).hasSize(1)
        with(page.tracks.first()) {
            assertThat(id).isEqualTo("yt_vidcontid001")
            assertThat(title).isEqualTo("Cont Title")
            assertThat(artist).isEqualTo("Cont Artist")
            assertThat(duration).isEqualTo(123f)
            assertThat(trackNumber).isEqualTo(22)
            assertThat(albumId).isEqualTo("yt_playlist_PL_X")
        }
        assertThat(page.continuation).isEqualTo("NEXT_CONT")
    }

    @Test fun `empty response returns empty page`() {
        val empty = json.parseToJsonElement("""{"contents":{}}""")
        val page = parser.parse(empty, "PL_X")
        assertThat(page.tracks).isEmpty()
        assertThat(page.continuation).isNull()
    }
}
