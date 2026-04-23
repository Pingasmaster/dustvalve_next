package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.remote.youtube.innertube.PlayerStreamInfo
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeInnertubeClient
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlayerParser
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicInnertubeClient
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicParser
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicSearchParser
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.model.Shelf
import com.dustvalve.next.android.domain.model.TileItem
import com.dustvalve.next.android.domain.model.TileKind
import com.dustvalve.next.android.domain.model.YouTubeMusicHomeFeed
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Before
import org.junit.Test

class YouTubeMusicRepositoryImplTest {

    private lateinit var client: YouTubeMusicInnertubeClient
    private lateinit var parser: YouTubeMusicParser
    private lateinit var searchParser: YouTubeMusicSearchParser
    private lateinit var ytInnertube: YouTubeInnertubeClient
    private lateinit var ytPlayerParser: YouTubePlayerParser
    private lateinit var repo: YouTubeMusicRepositoryImpl

    private val emptyJson: JsonElement = buildJsonObject {}
    private val sampleHome = YouTubeMusicHomeFeed(
        chips = emptyList(),
        shelves = listOf(Shelf.Tiles("T", listOf(
            TileItem(TileKind.PLAYLIST, "id1", "title", "sub", null)
        ))),
    )

    @Before fun setUp() {
        client = mockk()
        parser = mockk()
        searchParser = mockk()
        ytInnertube = mockk()
        ytPlayerParser = mockk()
        repo = YouTubeMusicRepositoryImpl(client, parser, searchParser, ytInnertube, ytPlayerParser)
    }

    @Test fun `resolveStreamUrl dispatches to YouTube Innertube player`() = runTest {
        coEvery { ytInnertube.player("vidvidvid12") } returns emptyJson
        every { ytPlayerParser.parsePlayerStreamInfo(emptyJson) } returns PlayerStreamInfo(
            streamUrl = "https://stream/x",
            format = AudioFormat.OPUS,
            bitrate = 128000,
            mimeType = "audio/webm; codecs=\"opus\"",
        )
        assertThat(repo.resolveStreamUrl("vidvidvid12")).isEqualTo("https://stream/x")
        coVerify { ytInnertube.player("vidvidvid12") }
    }

    @Test fun `getHome calls browse with FEmusic_home and parses response`() = runTest {
        coEvery { client.browse(browseId = "FEmusic_home", params = null) } returns emptyJson
        every { parser.parseHome(emptyJson) } returns sampleHome

        val out = repo.getHome()
        assertThat(out).isSameInstanceAs(sampleHome)
        coVerify { client.browse(browseId = "FEmusic_home", params = null) }
    }

    @Test fun `getMoodHome forwards params`() = runTest {
        coEvery { client.browse(browseId = "FEmusic_home", params = "MOOD_X") } returns emptyJson
        every { parser.parseHome(emptyJson) } returns sampleHome

        val out = repo.getMoodHome("MOOD_X")
        assertThat(out).isSameInstanceAs(sampleHome)
    }

    @Test fun `search uses songs params for null filter`() = runTest {
        val params = slot<String>()
        coEvery { client.search(query = "q", params = capture(params)) } returns emptyJson
        every { searchParser.parse(emptyJson) } returns emptyList()

        repo.search("q", null)
        assertThat(params.captured).isEqualTo("EgWKAQIIAWoMEA4QChADEAQQCRAF")
    }

    @Test fun `search uses correct params for each filter`() = runTest {
        val captured = mutableListOf<String>()
        coEvery { client.search(query = any(), params = capture(captured)) } returns emptyJson
        every { searchParser.parse(any()) } returns emptyList()

        repo.search("q", "songs")
        repo.search("q", "videos")
        repo.search("q", "albums")
        repo.search("q", "playlists")
        repo.search("q", "artists")
        repo.search("q", "unknown_filter")

        assertThat(captured).containsExactly(
            "EgWKAQIIAWoMEA4QChADEAQQCRAF",
            "EgWKAQIQAWoMEA4QChADEAQQCRAF",
            "EgWKAQIYAWoMEA4QChADEAQQCRAF",
            "EgWKAQIoAWoMEA4QChADEAQQCRAF",
            "EgWKAQIgAWoMEA4QChADEAQQCRAF",
            "EgWKAQIIAWoMEA4QChADEAQQCRAF", // unknown falls back to songs
        ).inOrder()
    }

    @Test fun `search returns parsed results`() = runTest {
        val r = SearchResult(
            type = SearchResultType.YOUTUBE_TRACK,
            name = "n", url = "u", imageUrl = null, artist = null,
            album = null, genre = null, releaseDate = null,
        )
        coEvery { client.search(query = any(), params = any()) } returns emptyJson
        every { searchParser.parse(emptyJson) } returns listOf(r)

        assertThat(repo.search("q", "songs")).containsExactly(r)
    }
}
