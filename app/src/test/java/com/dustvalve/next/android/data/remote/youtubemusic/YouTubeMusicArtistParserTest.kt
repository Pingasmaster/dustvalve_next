package com.dustvalve.next.android.data.remote.youtubemusic

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class YouTubeMusicArtistParserTest {

    private val parser = YouTubeMusicArtistParser()
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `parses Topic artist Top songs albums and linked official channel`() {
        // Catalog: yt-topic-artist-ytm-browse
        val root = json.parseToJsonElement(
            javaClass.classLoader!!
                .getResourceAsStream("fixtures/ytmusic/artist_topic_radiohead.json")!!
                .bufferedReader()
                .readText(),
        )
        val page = parser.parse(root, "UCtopicchannel00000000001")

        assertThat(page.name).isEqualTo("Radiohead")
        assertThat(page.avatarUrl).isNotNull()
        assertThat(page.avatarUrl!!).contains("googleusercontent.com")
        assertThat(page.songs.map { it.id }).containsExactly("yt_songVideo001", "yt_songVideo002").inOrder()
        assertThat(page.songs.first().title).isEqualTo("Karma Police")
        assertThat(page.songs.first().artUrl).isNotEmpty()
        assertThat(page.albums).hasSize(1)
        assertThat(page.albums.first().browseId).isEqualTo("MPREb_okcomputer01")
        assertThat(page.albums.first().title).isEqualTo("OK Computer")
        assertThat(page.albums.first().year).isEqualTo("1997")
        assertThat(page.linkedChannelId).isEqualTo("UCofficialchannel00000001")
        assertThat(page.songsMoreEndpoint).isNull()
    }

    @Test fun `extracts Top songs Show all bottomEndpoint`() {
        val root = json.parseToJsonElement(
            """
            {"header":{"musicImmersiveHeaderRenderer":{"title":{"runs":[{"text":"Artist"}]}}},
             "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
               "sectionListRenderer":{"contents":[
                 {"musicShelfRenderer":{
                   "title":{"runs":[{"text":"Top songs"}]},
                   "bottomEndpoint":{"browseEndpoint":{"browseId":"VLplaylistTopSongs"}},
                   "contents":[
                     {"musicResponsiveListItemRenderer":{
                       "playlistItemData":{"videoId":"songVideo001"},
                       "flexColumns":[
                         {"musicResponsiveListItemFlexColumnRenderer":{
                           "text":{"runs":[{"text":"Song One"}]}
                         }}
                       ]
                     }}
                   ]
                 }}
               ]}
             }}}]}}}
            """.trimIndent(),
        )
        val page = parser.parse(root, "UCartist000000000000001")
        assertThat(page.songsMoreEndpoint?.browseId).isEqualTo("VLplaylistTopSongs")
        assertThat(page.songs).hasSize(1)
    }

    @Test fun `parseSongList reads Show all shelf contents`() {
        val root = json.parseToJsonElement(
            """
            {"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
              "sectionListRenderer":{"contents":[
                {"musicShelfRenderer":{"contents":[
                  {"musicResponsiveListItemRenderer":{
                    "playlistItemData":{"videoId":"fullSong0001"},
                    "flexColumns":[
                      {"musicResponsiveListItemFlexColumnRenderer":{
                        "text":{"runs":[{"text":"Full Song"}]}
                      }}
                    ]
                  }},
                  {"musicResponsiveListItemRenderer":{
                    "playlistItemData":{"videoId":"fullSong0002"},
                    "flexColumns":[
                      {"musicResponsiveListItemFlexColumnRenderer":{
                        "text":{"runs":[{"text":"Full Song Two"}]}
                      }}
                    ]
                  }}
                ]}}
              ]}
            }}}]}}}
            """.trimIndent(),
        )
        val songs = parser.parseSongList(root, "UCartist000000000000001", "Artist")
        assertThat(songs.map { it.id }).containsExactly("yt_fullSong0001", "yt_fullSong0002").inOrder()
    }
}
