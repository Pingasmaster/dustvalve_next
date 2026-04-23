package com.dustvalve.next.android.data.remote.youtube.innertube

import com.dustvalve.next.android.domain.model.SearchResultType
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class YouTubeNextParserTest {

    private val parser = YouTubeNextParser()
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    @Test fun `parses real MWEB next response into related video results`() {
        val items = parser.parse(Fixtures.load("next_mweb.json"))
        assertThat(items).isNotEmpty()
        items.forEach {
            assertThat(it.type).isEqualTo(SearchResultType.YOUTUBE_TRACK)
            assertThat(it.url).startsWith("https://www.youtube.com/watch?v=")
            assertThat(it.name).isNotEmpty()
        }
    }

    @Test fun `parses videoWithContextRenderer fields`() {
        val r = json.parseToJsonElement(
            """
            {"contents":{"singleColumnWatchNextResults":{"results":{"results":{"contents":[
              {"itemSectionRenderer":{"contents":[
                {"videoWithContextRenderer":{
                  "videoId":"abc12345678",
                  "headline":{"runs":[{"text":"Some Vid"}]},
                  "shortBylineText":{"runs":[{"text":"Some Channel"}]},
                  "thumbnail":{"thumbnails":[{"url":"https://t/x","width":120}]}
                }}
              ]}}
            ]}}}}}
            """.trimIndent()
        )
        val items = parser.parse(r)
        assertThat(items).hasSize(1)
        with(items.first()) {
            assertThat(name).isEqualTo("Some Vid")
            assertThat(url).isEqualTo("https://www.youtube.com/watch?v=abc12345678")
            assertThat(artist).isEqualTo("Some Channel")
            assertThat(imageUrl).isEqualTo("https://t/x")
        }
    }

    @Test fun `parses desktop compactVideoRenderer shape`() {
        val r = json.parseToJsonElement(
            """
            {"contents":{"twoColumnWatchNextResults":{"secondaryResults":{"secondaryResults":{"results":[
              {"compactVideoRenderer":{
                "videoId":"cvr12345678",
                "title":{"runs":[{"text":"CVR Vid"}]},
                "shortBylineText":{"runs":[{"text":"X"}]},
                "thumbnail":{"thumbnails":[{"url":"https://t/y","width":120}]}
              }}
            ]}}}}}
            """.trimIndent()
        )
        val items = parser.parse(r)
        assertThat(items).hasSize(1)
        assertThat(items.first().name).isEqualTo("CVR Vid")
    }

    @Test fun `skips non-video sections silently`() {
        val r = json.parseToJsonElement(
            """
            {"contents":{"singleColumnWatchNextResults":{"results":{"results":{"contents":[
              {"slimVideoMetadataSectionRenderer":{}},
              {"itemSectionRenderer":{"contents":[
                {"videoMetadataCarouselViewModel":{}}
              ]}}
            ]}}}}}
            """.trimIndent()
        )
        val items = parser.parse(r)
        assertThat(items).isEmpty()
    }

    @Test fun `empty root returns empty list`() {
        val items = parser.parse(json.parseToJsonElement("""{}"""))
        assertThat(items).isEmpty()
    }
}
