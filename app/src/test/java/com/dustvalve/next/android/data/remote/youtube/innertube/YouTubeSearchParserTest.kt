package com.dustvalve.next.android.data.remote.youtube.innertube

import com.dustvalve.next.android.domain.model.SearchResultType
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class YouTubeSearchParserTest {

    private val parser = YouTubeSearchParser()
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    @Test fun `parses real WEB search response with videos channels and playlists`() {
        val page = parser.parse(Fixtures.load("search_daft_punk_web.json"))
        // The fixture should yield at least one video
        val videos = page.items.filter { it.type == SearchResultType.YOUTUBE_TRACK }
        assertThat(videos).isNotEmpty()
        videos.forEach {
            assertThat(it.url).startsWith("https://www.youtube.com/watch?v=")
            assertThat(it.name).isNotEmpty()
        }
        // ...at least one channel
        val channels = page.items.filter { it.type == SearchResultType.YOUTUBE_ARTIST }
        assertThat(channels).isNotEmpty()
        channels.forEach {
            assertThat(it.url).startsWith("https://www.youtube.com/channel/")
        }
    }

    @Test fun `parses lockupViewModel playlist results`() {
        val page = parser.parse(Fixtures.load("search_daft_punk_playlists_web.json"))
        val playlists = page.items.filter { it.type == SearchResultType.YOUTUBE_PLAYLIST }
        assertThat(playlists).isNotEmpty()
        playlists.forEach {
            assertThat(it.url).startsWith("https://www.youtube.com/playlist?list=")
            assertThat(it.name).isNotEmpty()
        }
    }

    @Test fun `surfaces continuation token when present`() {
        val page = parser.parse(Fixtures.load("search_daft_punk_web.json"))
        // The real-world WEB response always contains a continuationItemRenderer
        // as the second section. Token should be non-null.
        assertThat(page.continuation).isNotNull()
        assertThat(page.continuation!!).isNotEmpty()
    }

    @Test fun `empty contents returns empty page`() {
        val empty = json.parseToJsonElement(
            """
            {"contents":{"twoColumnSearchResultsRenderer":{"primaryContents":{
              "sectionListRenderer":{"contents":[]}
            }}}}
            """.trimIndent()
        )
        val out = parser.parse(empty)
        assertThat(out.items).isEmpty()
        assertThat(out.continuation).isNull()
    }

    @Test fun `unrecognized root returns empty page`() {
        val empty = json.parseToJsonElement("""{"foo":"bar"}""")
        val out = parser.parse(empty)
        assertThat(out.items).isEmpty()
        assertThat(out.continuation).isNull()
    }

    @Test fun `videoRenderer maps to YOUTUBE_TRACK with watch URL`() {
        val v = json.parseToJsonElement(
            """
            {"contents":{"twoColumnSearchResultsRenderer":{"primaryContents":{
              "sectionListRenderer":{"contents":[
                {"itemSectionRenderer":{"contents":[
                  {"videoRenderer":{
                    "videoId":"abcd1234567",
                    "title":{"runs":[{"text":"My Video"}]},
                    "ownerText":{"runs":[{"text":"Some Channel"}]},
                    "thumbnail":{"thumbnails":[{"url":"https://t.example/x","width":120}]}
                  }}
                ]}}
              ]}
            }}}}
            """.trimIndent()
        )
        val out = parser.parse(v)
        assertThat(out.items).hasSize(1)
        with(out.items.first()) {
            assertThat(type).isEqualTo(SearchResultType.YOUTUBE_TRACK)
            assertThat(name).isEqualTo("My Video")
            assertThat(url).isEqualTo("https://www.youtube.com/watch?v=abcd1234567")
            assertThat(artist).isEqualTo("Some Channel")
            assertThat(imageUrl).isEqualTo("https://t.example/x")
        }
    }

    @Test fun `channelRenderer maps to YOUTUBE_ARTIST with channel URL`() {
        val v = json.parseToJsonElement(
            """
            {"contents":{"twoColumnSearchResultsRenderer":{"primaryContents":{
              "sectionListRenderer":{"contents":[
                {"itemSectionRenderer":{"contents":[
                  {"channelRenderer":{
                    "channelId":"UCabcdefghijklmnopqrstuv",
                    "title":{"simpleText":"Some Artist"},
                    "thumbnail":{"thumbnails":[{"url":"https://t.example/c","width":88}]}
                  }}
                ]}}
              ]}
            }}}}
            """.trimIndent()
        )
        val out = parser.parse(v)
        assertThat(out.items).hasSize(1)
        with(out.items.first()) {
            assertThat(type).isEqualTo(SearchResultType.YOUTUBE_ARTIST)
            assertThat(name).isEqualTo("Some Artist")
            assertThat(url).isEqualTo("https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv")
        }
    }

    @Test fun `unknown row types are skipped silently`() {
        val v = json.parseToJsonElement(
            """
            {"contents":{"twoColumnSearchResultsRenderer":{"primaryContents":{
              "sectionListRenderer":{"contents":[
                {"itemSectionRenderer":{"contents":[
                  {"shelfRenderer":{}},
                  {"adSlotRenderer":{}},
                  {"gridShelfViewModel":{}}
                ]}}
              ]}
            }}}}
            """.trimIndent()
        )
        val out = parser.parse(v)
        assertThat(out.items).isEmpty()
    }
}
