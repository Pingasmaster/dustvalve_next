package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.local.db.dao.YouTubeMusicHomeCacheDao
import com.dustvalve.next.android.data.local.db.entity.YouTubeMusicHomeCacheEntity
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeInnertubeClient
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlayerParser
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicInnertubeClient
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicParser
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicSearchParser
import com.dustvalve.next.android.domain.model.YouTubeMusicHomeFeed
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Before
import org.junit.Test

/**
 * Verifies the YouTube Music home-cache behaviour. The home feed is
 * editorial (changes daily) but never blocks the UI — cached snapshots
 * always emit first; background refresh is best-effort and silent.
 */
class YouTubeMusicRepositoryCacheTest {

    private lateinit var client: YouTubeMusicInnertubeClient
    private lateinit var parser: YouTubeMusicParser
    private lateinit var searchParser: YouTubeMusicSearchParser
    private lateinit var ytClient: YouTubeInnertubeClient
    private lateinit var ytPlayerParser: YouTubePlayerParser
    private lateinit var homeCache: YouTubeMusicHomeCacheDao
    private lateinit var repo: YouTubeMusicRepositoryImpl

    @Before fun setUp() {
        client = mockk(relaxed = true)
        parser = mockk(relaxed = true)
        searchParser = mockk(relaxed = true)
        ytClient = mockk(relaxed = true)
        ytPlayerParser = mockk(relaxed = true)
        homeCache = mockk(relaxed = true)
        repo = YouTubeMusicRepositoryImpl(
            client, parser, searchParser, ytClient, ytPlayerParser,
            homeCache,
        )
    }

    @Test fun `getHome cache hit returns cached feed without browsing`() = runTest {
        // Cached payload — empty JSON object is fine; parser is mocked.
        coEvery { homeCache.getByKey("home") } returns YouTubeMusicHomeCacheEntity(
            key = "home",
            feedJson = "{}",
            cachedAt = System.currentTimeMillis(),  // Fresh — no background refresh.
        )
        val cachedFeed = YouTubeMusicHomeFeed(chips = emptyList(), shelves = emptyList())
        every { parser.parseHome(any()) } returns cachedFeed

        val feed = repo.getHome()

        assertThat(feed).isSameInstanceAs(cachedFeed)
        coVerify(exactly = 0) { client.browse(any(), any()) }
    }

    @Test fun `getHome cache miss fetches and persists`() = runTest {
        coEvery { homeCache.getByKey("home") } returns null
        val networkResponse: JsonObject = buildJsonObject { /* arbitrary */ }
        coEvery { client.browse(browseId = "FEmusic_home") } returns networkResponse
        val freshFeed = YouTubeMusicHomeFeed(chips = emptyList(), shelves = emptyList())
        every { parser.parseHome(networkResponse) } returns freshFeed

        val feed = repo.getHome()

        assertThat(feed).isSameInstanceAs(freshFeed)
        // Cache write attempted with key "home".
        val captured = slot<YouTubeMusicHomeCacheEntity>()
        coVerify { homeCache.insert(capture(captured)) }
        assertThat(captured.captured.key).isEqualTo("home")
    }

    @Test fun `getMoodHome cache hit keys by mood param`() = runTest {
        val moodParams = "MoodChillVibes"
        coEvery { homeCache.getByKey("mood:$moodParams") } returns YouTubeMusicHomeCacheEntity(
            key = "mood:$moodParams",
            feedJson = "{}",
        )
        val cachedFeed = YouTubeMusicHomeFeed(chips = emptyList(), shelves = emptyList())
        every { parser.parseHome(any()) } returns cachedFeed

        val feed = repo.getMoodHome(moodParams)

        assertThat(feed).isSameInstanceAs(cachedFeed)
        coVerify(exactly = 0) { client.browse(any(), any()) }
    }

    @Test fun `cache write failure does not break user-facing call`() = runTest {
        coEvery { homeCache.getByKey(any()) } returns null
        coEvery { client.browse(any(), any()) } returns JsonObject(emptyMap())
        coEvery { client.browse(any()) } returns JsonObject(emptyMap())
        every { parser.parseHome(any()) } returns
            YouTubeMusicHomeFeed(chips = emptyList(), shelves = emptyList())
        coEvery { homeCache.insert(any()) } throws RuntimeException("disk full")

        // Should not throw.
        repo.getHome()
    }
}
