package com.dustvalve.next.android.data.remote.youtube.innertube

import com.dustvalve.next.android.domain.model.TrackSource
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class YouTubePlaylistParserTest {

    private val parser = YouTubePlaylistParser()
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Test fun `parses real MWEB playlist response into Tracks`() {
        val page = parser.parse(
            Fixtures.load("playlist_mweb.json"),
            playlistId = "PLKBWcWelelBCDoz6LpsUd2Qj6dCl9OmMZ",
        )
        assertThat(page.tracks).isNotEmpty()
        assertThat(page.title).isNotNull()
        assertThat(page.coverUrl).isNotNull()
        assertThat(page.coverUrl!!).contains("ytimg.com")
        assertThat(page.coverUrl!!).contains("hq720")
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

    @Test fun `extracts cover from OLAK playlistHeaderBanner heroPlaylistThumbnailRenderer`() {
        // YTM album audio playlists (OLAK5uy_...) use the legacy MWEB header
        // shape: playlistHeaderBanner.heroPlaylistThumbnailRenderer, not the
        // pageHeaderViewModel.heroImage path used by ordinary playlists.
        val root = json.parseToJsonElement(
            """
            {
              "header": {
                "playlistHeaderRenderer": {
                  "title": {"runs":[{"text":"Album Title"}]},
                  "playlistHeaderBanner": {
                    "heroPlaylistThumbnailRenderer": {
                      "thumbnail": {
                        "thumbnails": [
                          {"url":"https://i9.ytimg.com/s_p/OLAK5uy_x/mqdefault.jpg?sqp=1","width":180,"height":180},
                          {"url":"https://i9.ytimg.com/s_p/OLAK5uy_x/maxresdefault.jpg?sqp=1","width":1200,"height":1200}
                        ]
                      }
                    }
                  }
                }
              },
              "contents": {}
            }
            """.trimIndent(),
        )
        val page = parser.parse(root, "OLAK5uy_x")
        assertThat(page.title).isEqualTo("Album Title")
        assertThat(page.coverUrl).isEqualTo(
            "https://i9.ytimg.com/s_p/OLAK5uy_x/maxresdefault.jpg",
        )
    }

    @Test fun `parses modern lockupViewModel playlist rows with continuationItemViewModel`() {
        // Catalog: yt-playlist-lockup-rows. Live MWEB/WEB playlist browse
        // dropped playlistVideoRenderer for lockupViewModel; without this
        // path CollectionDetail opens with title/cover but zero tracks.
        val root = json.parseToJsonElement(
            """
            {
              "header": {
                "pageHeaderRenderer": {
                  "pageTitle": "Lockup Playlist",
                  "content": {
                    "pageHeaderViewModel": {
                      "heroImage": {
                        "contentPreviewImageViewModel": {
                          "image": {
                            "sources": [
                              {"url":"https://i.ytimg.com/vi/abc12345678/hq720.jpg","width":720,"height":404}
                            ]
                          }
                        }
                      }
                    }
                  }
                }
              },
              "contents": {
                "singleColumnBrowseResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [{
                            "itemSectionRenderer": {
                              "contents": [
                                {
                                  "lockupViewModel": {
                                    "contentId": "vidlockup001",
                                    "contentType": "LOCKUP_CONTENT_TYPE_VIDEO",
                                    "contentImage": {
                                      "thumbnailViewModel": {
                                        "image": {
                                          "sources": [
                                            {"url":"https://i.ytimg.com/vi/vidlockup001/hqdefault.jpg","width":480,"height":360}
                                          ]
                                        },
                                        "overlays": [{
                                          "thumbnailBottomOverlayViewModel": {
                                            "badges": [{"thumbnailBadgeViewModel":{"text":"3:55"}}]
                                          }
                                        }]
                                      }
                                    },
                                    "metadata": {
                                      "lockupMetadataViewModel": {
                                        "title": {"content": "Lockup Track"},
                                        "metadata": {
                                          "contentMetadataViewModel": {
                                            "metadataRows": [{
                                              "metadataParts": [{"text":{"content":"Lockup Artist"}}]
                                            }]
                                          }
                                        },
                                        "image": {
                                          "decoratedAvatarViewModel": {
                                            "rendererContext": {
                                              "commandContext": {
                                                "onTap": {
                                                  "innertubeCommand": {
                                                    "browseEndpoint": {"browseId":"UClockupartist01"}
                                                  }
                                                }
                                              }
                                            }
                                          }
                                        }
                                      }
                                    }
                                  }
                                },
                                {
                                  "continuationItemViewModel": {
                                    "continuationCommand": {
                                      "innertubeCommand": {
                                        "continuationCommand": {"token":"LOCKUP_CONT"}
                                      }
                                    }
                                  }
                                }
                              ]
                            }
                          }]
                        }
                      }
                    }
                  }]
                }
              }
            }
            """.trimIndent(),
        )
        val page = parser.parse(root, "PLlockup")
        assertThat(page.title).isEqualTo("Lockup Playlist")
        assertThat(page.coverUrl).contains("hq720")
        assertThat(page.tracks).hasSize(1)
        with(page.tracks.first()) {
            assertThat(id).isEqualTo("yt_vidlockup001")
            assertThat(title).isEqualTo("Lockup Track")
            assertThat(artist).isEqualTo("Lockup Artist")
            assertThat(artistUrl).isEqualTo("https://www.youtube.com/channel/UClockupartist01")
            assertThat(duration).isEqualTo(235f)
            assertThat(artUrl).contains("hq720")
        }
        assertThat(page.continuation).isEqualTo("LOCKUP_CONT")
    }

    @Test fun `parseContinuation walks lockupViewModel appendContinuationItems`() {
        val cont = json.parseToJsonElement(
            """
            {"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[
              {"lockupViewModel":{
                "contentId":"vidcontlock1",
                "contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
                "contentImage":{"thumbnailViewModel":{"image":{"sources":[{"url":"https://i.ytimg.com/vi/vidcontlock1/hqdefault.jpg","width":120}]}}},
                "metadata":{"lockupMetadataViewModel":{"title":{"content":"Cont Lockup"}}}
              }},
              {"continuationItemViewModel":{"continuationCommand":{"innertubeCommand":{
                "continuationCommand":{"token":"NEXT_LOCKUP"}
              }}}}
            ]}}]}
            """.trimIndent(),
        )
        val page = parser.parseContinuation(cont, "PL_X", startIndex = 22)
        assertThat(page.tracks).hasSize(1)
        assertThat(page.tracks.first().id).isEqualTo("yt_vidcontlock1")
        assertThat(page.tracks.first().trackNumber).isEqualTo(22)
        assertThat(page.continuation).isEqualTo("NEXT_LOCKUP")
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
            """.trimIndent(),
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

    @Test fun `extractMixSeedVideoId decodes seeded mix families`() {
        assertThat(parser.extractMixSeedVideoId("RDdQw4w9WgXcQ")).isEqualTo("dQw4w9WgXcQ")
        assertThat(parser.extractMixSeedVideoId("RDAMVMdQw4w9WgXcQ")).isEqualTo("dQw4w9WgXcQ")
        assertThat(parser.extractMixSeedVideoId("RDMMdQw4w9WgXcQ")).isEqualTo("dQw4w9WgXcQ")
    }

    @Test fun `extractMixSeedVideoId returns null for seedless mix families`() {
        // RDCLAK suffixes are opaque playlist tokens, NOT videoIds. The old
        // substring(6, 17) fabricated an 11-char "seed" that always passed
        // the charset check and sent garbage videoIds to /next.
        assertThat(parser.extractMixSeedVideoId("RDCLAK5uy_n20FRYQXNt1p1wS55Nj2r14IouO5weaYU")).isNull()
        assertThat(parser.extractMixSeedVideoId("RDGMEMYH9CUrFNJS4mrRH8FcQ")).isNull()
        assertThat(parser.extractMixSeedVideoId("RDEMabcdefghijklm")).isNull()
        assertThat(parser.extractMixSeedVideoId("RDAMPLOLAK5uy_abcdefg")).isNull()
    }

    @Test fun `isSeedlessMixId recognizes RDGMEM RDEM RDCLAK`() {
        assertThat(parser.isSeedlessMixId("RDGMEMYH9CUrFNJS4mrRH8FcQ")).isTrue()
        assertThat(parser.isSeedlessMixId("RDEMabcdefghijklm")).isTrue()
        assertThat(parser.isSeedlessMixId("RDCLAK5uy_n20FRYQXNt1p1wS55Nj2r14IouO5weaYU")).isTrue()
        assertThat(parser.isSeedlessMixId("RDdQw4w9WgXcQ")).isFalse()
        assertThat(parser.isSeedlessMixId("RDAMVMdQw4w9WgXcQ")).isFalse()
    }

    @Test fun `extractMixSeedVideoId returns null for non-mix ids`() {
        assertThat(parser.extractMixSeedVideoId("PLKBWcWelelBCDoz6LpsUd2Qj6dCl9OmMZ")).isNull()
        assertThat(parser.extractMixSeedVideoId("OLAK5uy_abcdefg")).isNull()
    }
}
