package com.dustvalve.next.android.data.remote.youtubemusic

import com.dustvalve.next.android.domain.model.SearchResultType
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class YouTubeMusicSearchParserTest {

    private val parser = YouTubeMusicSearchParser()
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    @Test fun `parses songs filter response`() {
        val results = parser.parse(Fixtures.load("search_songs.json"))
        assertThat(results).isNotEmpty()
        // All non-card rows should be tracks pointing at watch?v=
        val tracks = results.filter { it.type == SearchResultType.YOUTUBE_TRACK }
        assertThat(tracks).isNotEmpty()
        tracks.forEach {
            assertThat(it.url).startsWith("https://www.youtube.com/watch?v=")
            assertThat(it.name).isNotEmpty()
        }
    }

    @Test fun `parses videos filter via playlistItemData fallback`() {
        val results = parser.parse(Fixtures.load("search_videos.json"))
        assertThat(results).isNotEmpty()
        results.forEach {
            assertThat(it.type).isEqualTo(SearchResultType.YOUTUBE_TRACK)
            assertThat(it.url).startsWith("https://www.youtube.com/watch?v=")
        }
    }

    @Test fun `parses artists filter`() {
        val results = parser.parse(Fixtures.load("search_artists.json"))
        val artists = results.filter { it.type == SearchResultType.YOUTUBE_ARTIST }
        assertThat(artists).isNotEmpty()
        artists.forEach {
            assertThat(it.url).startsWith("https://www.youtube.com/channel/")
            assertThat(it.name).isNotEmpty()
        }
    }

    @Test fun `parses albums filter and strips VL prefix`() {
        val results = parser.parse(Fixtures.load("search_albums.json"))
        val albums = results.filter { it.type == SearchResultType.YOUTUBE_ALBUM }
        assertThat(albums).isNotEmpty()
        albums.forEach {
            assertThat(it.url).startsWith("https://www.youtube.com/playlist?list=")
            // No "VL" or "list=VL" in the resulting URL
            assertThat(it.url).doesNotContain("list=VL")
        }
    }

    @Test fun `parses playlists filter and strips VL prefix`() {
        val results = parser.parse(Fixtures.load("search_playlists.json"))
        val playlists = results.filter { it.type == SearchResultType.YOUTUBE_PLAYLIST }
        assertThat(playlists).isNotEmpty()
        playlists.forEach {
            assertThat(it.url).startsWith("https://www.youtube.com/playlist?list=")
            assertThat(it.url).doesNotContain("list=VL")
        }
    }

    @Test fun `default search response yields at least one row`() {
        val results = parser.parse(Fixtures.load("search_default.json"))
        // The default unfiltered response includes the top-result card and
        // at least one song shelf - we should pick up something.
        assertThat(results).isNotEmpty()
    }

    @Test fun `empty contents returns empty list`() {
        val empty = json.parseToJsonElement(
            """
            {"contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
              "sectionListRenderer":{"contents":[]}
            }}}]}}}
            """.trimIndent()
        )
        assertThat(parser.parse(empty)).isEmpty()
    }

    @Test fun `unrecognized root returns empty list`() {
        val empty = json.parseToJsonElement("""{"foo":"bar"}""")
        assertThat(parser.parse(empty)).isEmpty()
    }

    @Test fun `flattens itemSectionRenderer wrapping musicShelfRenderer`() {
        // Synthesize a response where a song shelf is nested in
        // itemSectionRenderer (the on-device case the user hit).
        val nested = json.parseToJsonElement(
            """
            {"contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
              "sectionListRenderer":{"contents":[
                {"itemSectionRenderer":{"contents":[
                  {"musicShelfRenderer":{
                    "title":{"runs":[{"text":"Songs"}]},
                    "contents":[
                      {"musicResponsiveListItemRenderer":{
                        "flexColumns":[
                          {"musicResponsiveListItemFlexColumnRenderer":{
                            "text":{"runs":[
                              {"text":"My Song",
                               "navigationEndpoint":{"watchEndpoint":{"videoId":"abc12345678"}}}
                            ]}
                          }},
                          {"musicResponsiveListItemFlexColumnRenderer":{
                            "text":{"runs":[
                              {"text":"Song"},{"text":" • "},{"text":"Some Artist"}
                            ]}
                          }}
                        ],
                        "navigationEndpoint":{"watchEndpoint":{"videoId":"abc12345678"}}
                      }}
                    ]
                  }}
                ]}}
              ]}
            }}}]}}}
            """.trimIndent()
        )
        val results = parser.parse(nested)
        assertThat(results).hasSize(1)
        with(results.first()) {
            assertThat(type).isEqualTo(SearchResultType.YOUTUBE_TRACK)
            assertThat(name).isEqualTo("My Song")
            assertThat(url).isEqualTo("https://www.youtube.com/watch?v=abc12345678")
            assertThat(artist).isEqualTo("Some Artist")
        }
    }
}
