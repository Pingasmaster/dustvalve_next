package com.dustvalve.next.android.data.remote.youtubemusic

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test

class YouTubeMusicAlbumResolverTest {

    private lateinit var client: YouTubeMusicInnertubeClient
    private lateinit var resolver: YouTubeMusicAlbumResolver
    private val json = Json { ignoreUnknownKeys = true }

    @Before fun setUp() {
        client = mockk()
        resolver = YouTubeMusicAlbumResolver(client)
    }

    @Test fun `prefers urlCanonical OLAK over related carousel playlistId`() = runTest {
        coEvery { client.browse("MPREb_album1") } returns json.parseToJsonElement(
            """
            {"microformat":{"microformatDataRenderer":{
              "urlCanonical":"https://music.youtube.com/playlist?list=OLAK5uy_canonical01"
            }},
             "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
               "sectionListRenderer":{"contents":[
                 {"musicCarouselShelfRenderer":{"contents":[
                   {"musicTwoRowItemRenderer":{
                     "navigationEndpoint":{"watchPlaylistEndpoint":{
                       "playlistId":"OLAK5uy_relatedWrong"
                     }}
                   }}
                 ]}}
               ]}
             }}}]}}}
            """.trimIndent(),
        )
        assertThat(resolver.resolveAudioPlaylistId("MPREb_album1"))
            .isEqualTo("OLAK5uy_canonical01")
    }

    @Test fun `prefers first track watchEndpoint playlistId over related carousel`() = runTest {
        coEvery { client.browse("MPREb_album2") } returns json.parseToJsonElement(
            """
            {"contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":{
              "sectionListRenderer":{"contents":[
                {"musicPlaylistShelfRenderer":{"contents":[
                  {"musicResponsiveListItemRenderer":{
                    "playlistItemData":{"videoId":"track0000001"},
                    "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{
                      "text":{"runs":[{"text":"Track","navigationEndpoint":{"watchEndpoint":{
                        "videoId":"track0000001","playlistId":"OLAK5uy_firstTrack"
                      }}}]}
                    }}]
                  }}
                ]}},
                {"musicCarouselShelfRenderer":{"contents":[
                  {"musicTwoRowItemRenderer":{
                    "title":{"runs":[{"text":"Related"}]},
                    "navigationEndpoint":{"watchPlaylistEndpoint":{
                      "playlistId":"OLAK5uy_relatedWrong"
                    }}
                  }}
                ]}}
              ]}
            }}}}
            """.trimIndent(),
        )
        assertThat(resolver.resolveAudioPlaylistId("MPREb_album2"))
            .isEqualTo("OLAK5uy_firstTrack")
    }

    @Test fun `prefers audioPlaylistId in album header`() = runTest {
        coEvery { client.browse("MPREb_album3") } returns json.parseToJsonElement(
            """
            {"header":{"musicDetailHeaderRenderer":{
              "menu":{"menuRenderer":{"items":[
                {"watchPlaylistEndpoint":{"audioPlaylistId":"OLAK5uy_fromAudio"}}
              ]}}
            }},
             "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
               "sectionListRenderer":{"contents":[
                 {"musicCarouselShelfRenderer":{"contents":[
                   {"musicTwoRowItemRenderer":{
                     "navigationEndpoint":{"watchPlaylistEndpoint":{
                       "playlistId":"OLAK5uy_relatedWrong"
                     }}
                   }}
                 ]}}
               ]}
             }}}]}}}
            """.trimIndent(),
        )
        assertThat(resolver.resolveAudioPlaylistId("MPREb_album3"))
            .isEqualTo("OLAK5uy_fromAudio")
    }
}
