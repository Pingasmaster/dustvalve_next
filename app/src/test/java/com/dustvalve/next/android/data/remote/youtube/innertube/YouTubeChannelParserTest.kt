package com.dustvalve.next.android.data.remote.youtube.innertube

import com.dustvalve.next.android.domain.model.TrackSource
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class YouTubeChannelParserTest {

    private val parser = YouTubeChannelParser()
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    @Test fun `parses real WEB channel-videos response into Tracks`() {
        val page = parser.parse(
            Fixtures.load("channel_web_videos.json"),
            channelId = "UCX6OQ3DkcsbYNE6H8uQQuVA",
        )
        assertThat(page.tracks).isNotEmpty()
        assertThat(page.channelName).isNotNull()
        with(page.tracks.first()) {
            assertThat(id).startsWith("yt_")
            assertThat(title).isNotEmpty()
            assertThat(streamUrl).startsWith("https://www.youtube.com/watch?v=")
            assertThat(source).isEqualTo(TrackSource.YOUTUBE)
            assertThat(albumId).isEqualTo("yt_channel_UCX6OQ3DkcsbYNE6H8uQQuVA")
            assertThat(artistUrl).isEqualTo("https://www.youtube.com/channel/UCX6OQ3DkcsbYNE6H8uQQuVA")
        }
    }

    @Test fun `surfaces continuation token for richGrid`() {
        val page = parser.parse(
            Fixtures.load("channel_web_videos.json"),
            channelId = "UCX6OQ3DkcsbYNE6H8uQQuVA",
        )
        // Real channel responses always end with continuationItemRenderer.
        assertThat(page.continuation).isNotNull()
    }

    @Test fun `parseContinuation walks appendContinuationItemsAction`() {
        val cont = json.parseToJsonElement(
            """
            {"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[
              {"richItemRenderer":{"content":{"videoRenderer":{
                "videoId":"contvid0001",
                "title":{"runs":[{"text":"Continued"}]},
                "lengthText":{"simpleText":"4:56"},
                "thumbnail":{"thumbnails":[{"url":"https://t/x","width":480}]}
              }}}},
              {"continuationItemRenderer":{"continuationEndpoint":{
                "continuationCommand":{"token":"NEXT_CHAN"}
              }}}
            ]}}]}
            """.trimIndent()
        )
        val page = parser.parseContinuation(cont, "UCabcdefghijklmnopqrstuv", channelName = "Some Chan", startIndex = 11)
        assertThat(page.tracks).hasSize(1)
        with(page.tracks.first()) {
            assertThat(id).isEqualTo("yt_contvid0001")
            assertThat(title).isEqualTo("Continued")
            // 4:56 -> 296 sec
            assertThat(duration).isEqualTo(296f)
            assertThat(trackNumber).isEqualTo(11)
            assertThat(artist).isEqualTo("Some Chan")
        }
        assertThat(page.continuation).isEqualTo("NEXT_CHAN")
    }

    @Test fun `parses h_mm_ss length text`() {
        val cont = json.parseToJsonElement(
            """
            {"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[
              {"richItemRenderer":{"content":{"videoRenderer":{
                "videoId":"longvid0001",
                "title":{"runs":[{"text":"L"}]},
                "lengthText":{"simpleText":"1:02:03"},
                "thumbnail":{"thumbnails":[]}
              }}}}
            ]}}]}
            """.trimIndent()
        )
        val page = parser.parseContinuation(cont, "UC", channelName = "C", startIndex = 1)
        // 1*3600 + 2*60 + 3 = 3723
        assertThat(page.tracks.first().duration).isEqualTo(3723f)
    }

    @Test fun `empty channel returns empty page`() {
        val empty = json.parseToJsonElement("""{"contents":{}}""")
        val page = parser.parse(empty, "UC")
        assertThat(page.tracks).isEmpty()
        assertThat(page.channelName).isNull()
    }
}
