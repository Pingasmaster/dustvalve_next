package com.dustvalve.next.android.data.remote.soundcloud

import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.model.SoundCloudShelfKind
import com.dustvalve.next.android.domain.model.StreamPolicy
import com.dustvalve.next.android.domain.model.TrackSource
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class SoundCloudMappersTest {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `charts unwrap nested track and upgrade artwork`() {
        val root = json.parseToJsonElement(load("charts_trending.json"))
        val tracks = SoundCloudMappers.parseChartsTracks(root)
        assertThat(tracks).hasSize(2)

        val first = tracks[0]
        assertThat(first.id).isEqualTo("sc_111")
        assertThat(first.title).isEqualTo("Chart Track One")
        assertThat(first.artist).isEqualTo("Artist A")
        assertThat(first.duration).isEqualTo(180f)
        assertThat(first.streamUrl).isNull()
        assertThat(first.source).isEqualTo(TrackSource.SOUNDCLOUD)
        // Fixture lists encrypted alongside plain progressive/HLS -> BLOCKED.
        assertThat(first.streamPolicy).isEqualTo(StreamPolicy.BLOCKED)
        assertThat(first.artUrl).contains("-t500x500.")
        assertThat(first.artUrl).doesNotContain("-large.")

        // Missing artwork falls back to avatar, also upgraded.
        assertThat(tracks[1].artUrl).contains("avatars-bbb-t500x500")
        assertThat(tracks[1].streamPolicy).isEqualTo(StreamPolicy.UNKNOWN)
    }

    @Test
    fun `search maps kinds to SOUNDCLOUD result types`() {
        val root = json.parseToJsonElement(load("search_mixed.json"))
        val results = SoundCloudMappers.parseSearchResults(root)
        assertThat(results.map { it.type }).containsExactly(
            SearchResultType.SOUNDCLOUD_ARTIST,
            SearchResultType.SOUNDCLOUD_TRACK,
            SearchResultType.SOUNDCLOUD_PLAYLIST,
            SearchResultType.SOUNDCLOUD_ALBUM,
        ).inOrder()

        val track = results[1]
        assertThat(track.name).isEqualTo("Karma Police")
        assertThat(track.artist).isEqualTo("Radiohead")
        assertThat(track.imageUrl).contains("-t500x500.")

        val album = results[3]
        assertThat(album.name).isEqualTo("In Rainbows")
        assertThat(album.url).contains("/sets/in-rainbows")
    }

    @Test
    fun `mixed selections map shelf item kinds`() {
        val root = json.parseToJsonElement(load("mixed_selections.json"))
        val shelves = SoundCloudMappers.parseMixedSelections(root)
        assertThat(shelves).hasSize(1)
        assertThat(shelves[0].title).isEqualTo("Trending by genre")

        val kinds = shelves[0].items.map { it.kind }
        assertThat(kinds).containsExactly(
            SoundCloudShelfKind.PLAYLIST,
            SoundCloudShelfKind.ALBUM,
            SoundCloudShelfKind.TRACK,
            SoundCloudShelfKind.USER,
        ).inOrder()
    }

    @Test
    fun `pickBestTranscodingUrl prefers progressive over hls and skips encrypted`() {
        val track = json.parseToJsonElement(
            """
            {
              "media": {
                "transcodings": [
                  {
                    "url": "https://api-v2.soundcloud.com/media/enc",
                    "snipped": false,
                    "format": { "protocol": "cbc-encrypted-hls" },
                    "quality": "hq"
                  },
                  {
                    "url": "https://api-v2.soundcloud.com/media/hls",
                    "snipped": false,
                    "format": { "protocol": "hls" },
                    "quality": "sq"
                  },
                  {
                    "url": "https://api-v2.soundcloud.com/media/progressive",
                    "snipped": false,
                    "format": { "protocol": "progressive" },
                    "quality": "sq"
                  }
                ]
              }
            }
            """.trimIndent(),
        )
        val url = SoundCloudMappers.pickBestTranscodingUrl(track)
        assertThat(url).endsWith("/progressive")
        assertThat(SoundCloudMappers.pickBestTranscodingUrls(track)).containsExactly(
            "https://api-v2.soundcloud.com/media/progressive",
            "https://api-v2.soundcloud.com/media/hls",
        ).inOrder()
        assertThat(SoundCloudMappers.pickBestTranscodingUrls(track, progressiveOnly = true))
            .containsExactly("https://api-v2.soundcloud.com/media/progressive")
        assertThat(SoundCloudMappers.hasOnlyEncryptedTranscodings(track)).isFalse()
        assertThat(SoundCloudMappers.inferStreamPolicy(track)).isEqualTo(StreamPolicy.BLOCKED)
    }

    @Test
    fun `inferStreamPolicy marks progressive as downloadable and hls-only as stream-only`() {
        val progressive = json.parseToJsonElement(
            """
            {
              "media": {
                "transcodings": [
                  {
                    "url": "https://api-v2.soundcloud.com/media/progressive",
                    "snipped": false,
                    "format": { "protocol": "progressive" },
                    "quality": "sq"
                  },
                  {
                    "url": "https://api-v2.soundcloud.com/media/hls",
                    "snipped": false,
                    "format": { "protocol": "hls" },
                    "quality": "sq"
                  }
                ]
              }
            }
            """.trimIndent(),
        )
        assertThat(SoundCloudMappers.inferStreamPolicy(progressive))
            .isEqualTo(StreamPolicy.DOWNLOADABLE)

        val hlsOnly = json.parseToJsonElement(
            """
            {
              "media": {
                "transcodings": [
                  {
                    "url": "https://api-v2.soundcloud.com/media/hls",
                    "snipped": false,
                    "format": { "protocol": "hls" },
                    "quality": "sq"
                  }
                ]
              }
            }
            """.trimIndent(),
        )
        assertThat(SoundCloudMappers.inferStreamPolicy(hlsOnly))
            .isEqualTo(StreamPolicy.STREAM_ONLY)
    }

    @Test
    fun `hasOnlyEncryptedTranscodings detects Go-plus DRM tracks`() {
        val track = json.parseToJsonElement(
            """
            {
              "media": {
                "transcodings": [
                  {
                    "url": "https://api-v2.soundcloud.com/media/enc",
                    "snipped": false,
                    "format": { "protocol": "cbc-encrypted-hls" },
                    "quality": "hq"
                  }
                ]
              }
            }
            """.trimIndent(),
        )
        assertThat(SoundCloudMappers.pickBestTranscodingUrls(track)).isEmpty()
        assertThat(SoundCloudMappers.hasOnlyEncryptedTranscodings(track)).isTrue()
        assertThat(SoundCloudMappers.inferStreamPolicy(track)).isEqualTo(StreamPolicy.BLOCKED)
    }

    @Test
    fun `home combines charts and shelves`() {
        val charts = json.parseToJsonElement(load("charts_trending.json"))
        val mixed = json.parseToJsonElement(load("mixed_selections.json"))
        val home = SoundCloudMappers.parseHome("all-music", charts, mixed)
        assertThat(home.genre).isEqualTo("all-music")
        assertThat(home.trending).hasSize(2)
        assertThat(home.shelves).hasSize(1)
    }

    private fun load(name: String): String = checkNotNull(javaClass.classLoader!!.getResourceAsStream("fixtures/soundcloud/$name"))
        .bufferedReader()
        .use { it.readText() }
}
