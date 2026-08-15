package com.dustvalve.next.android.data.remote.youtube.innertube

import com.dustvalve.next.android.domain.model.TrackSource
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class YouTubeChannelParserTest {

    private val parser = YouTubeChannelParser()
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Test fun `parses real WEB channel-videos response into Tracks`() {
        val page = parser.parse(
            Fixtures.load("channel_web_videos.json"),
            channelId = "UCX6OQ3DkcsbYNE6H8uQQuVA",
        )
        assertThat(page.tracks).isNotEmpty()
        assertThat(page.channelName).isNotNull()
        assertThat(page.avatarUrl).isNotNull()
        assertThat(page.avatarUrl!!).contains("googleusercontent.com")
        assertThat(page.avatarUrl!!).contains("=s800")
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
            """.trimIndent(),
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
            """.trimIndent(),
        )
        val page = parser.parseContinuation(cont, "UC", channelName = "C", startIndex = 1)
        // 1*3600 + 2*60 + 3 = 3723
        assertThat(page.tracks.first().duration).isEqualTo(3723f)
    }

    @Test fun `prefers the selected tab over an earlier tab with a grid`() {
        // tabRenderer.selected is a JSON boolean; comparing str("selected")
        // to "true" made this preference dead code and the fallback (first
        // tab with a grid) silently won.
        val root = json.parseToJsonElement(
            """
            {"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[
              {"tabRenderer":{"selected":false,"content":{"richGridRenderer":{"contents":[
                {"richItemRenderer":{"content":{"videoRenderer":{
                  "videoId":"decoyvid001",
                  "title":{"runs":[{"text":"Decoy From Unselected Tab"}]},
                  "thumbnail":{"thumbnails":[]}
                }}}}
              ]}}}},
              {"tabRenderer":{"selected":true,"content":{"richGridRenderer":{"contents":[
                {"richItemRenderer":{"content":{"videoRenderer":{
                  "videoId":"selectedvid1",
                  "title":{"runs":[{"text":"From Selected Tab"}]},
                  "thumbnail":{"thumbnails":[]}
                }}}}
              ]}}}}
            ]}}}
            """.trimIndent(),
        )
        val page = parser.parse(root, "UCabcdefghijklmnopqrstuv")
        assertThat(page.tracks.map { it.id }).containsExactly("yt_selectedvid1")
        assertThat(page.tracks.first().title).isEqualTo("From Selected Tab")
    }

    @Test fun `parses richItem lockupViewModel video rows`() {
        // Catalog: yt-channel-lockup-rows. Videos tab richItemRenderer now
        // wraps lockupViewModel instead of videoRenderer.
        val root = json.parseToJsonElement(
            """
            {
              "header": {"pageHeaderRenderer": {"pageTitle": "Lockup Channel"}},
              "contents": {"twoColumnBrowseResultsRenderer": {"tabs": [{
                "tabRenderer": {"selected": true, "content": {"richGridRenderer": {"contents": [
                  {"richItemRenderer": {"content": {"lockupViewModel": {
                    "contentId": "chanvid0001",
                    "contentType": "LOCKUP_CONTENT_TYPE_VIDEO",
                    "contentImage": {
                      "thumbnailViewModel": {
                        "image": {"sources": [
                          {"url":"https://i.ytimg.com/vi/chanvid0001/hqdefault.jpg","width":480,"height":360}
                        ]},
                        "overlays": [{"thumbnailBottomOverlayViewModel": {
                          "badges": [{"thumbnailBadgeViewModel": {"text": "12:34"}}]
                        }}]
                      }
                    },
                    "metadata": {"lockupMetadataViewModel": {
                      "title": {"content": "Channel Lockup Video"}
                    }}
                  }}}},
                  {"continuationItemRenderer": {"continuationEndpoint": {
                    "continuationCommand": {"token": "CHAN_LOCKUP_CONT"}
                  }}}
                ]}}}
              }]}}
            }
            """.trimIndent(),
        )
        val page = parser.parse(root, "UClockupchannel01")
        assertThat(page.channelName).isEqualTo("Lockup Channel")
        assertThat(page.tracks).hasSize(1)
        with(page.tracks.first()) {
            assertThat(id).isEqualTo("yt_chanvid0001")
            assertThat(title).isEqualTo("Channel Lockup Video")
            assertThat(artist).isEqualTo("Lockup Channel")
            assertThat(duration).isEqualTo(754f)
            assertThat(artUrl).contains("hq720")
        }
        assertThat(page.continuation).isEqualTo("CHAN_LOCKUP_CONT")
    }

    @Test fun `empty channel returns empty page`() {
        val empty = json.parseToJsonElement("""{"contents":{}}""")
        val page = parser.parse(empty, "UC")
        assertThat(page.tracks).isEmpty()
        assertThat(page.channelName).isNull()
    }
}
