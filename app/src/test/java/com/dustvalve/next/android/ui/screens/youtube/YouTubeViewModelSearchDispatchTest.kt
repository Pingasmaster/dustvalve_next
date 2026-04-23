package com.dustvalve.next.android.ui.screens.youtube

import app.cash.turbine.test
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.RecentSearchDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.repository.YouTubeMusicRepository
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeViewModelSearchDispatchTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var settings: SettingsDataStore
    private lateinit var ytRepo: YouTubeRepository
    private lateinit var ytmRepo: YouTubeMusicRepository
    private lateinit var playlistRepo: PlaylistRepository
    private lateinit var trackDao: TrackDao
    private lateinit var database: DustvalveNextDatabase
    private lateinit var recentSearchDao: RecentSearchDao
    private lateinit var favoriteDao: FavoriteDao

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)

        settings = mockk(relaxed = true)
        every { settings.youtubeDefaultSource } returns flowOf("youtube")
        every { settings.searchHistoryEnabled } returns flowOf(false)
        every { settings.lastYoutubeVideoId } returns flowOf(null)

        ytRepo = mockk()
        ytmRepo = mockk()
        playlistRepo = mockk(relaxed = true)
        trackDao = mockk(relaxed = true)
        database = mockk(relaxed = true)
        recentSearchDao = mockk(relaxed = true)
        favoriteDao = mockk(relaxed = true)

        every { recentSearchDao.getRecent(any(), any()) } returns flowOf(emptyList())

        // Discovery feed runs at init - stub it out to a quick empty success.
        coEvery { ytRepo.search(any(), any(), any()) } returns Pair(emptyList(), null)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `YouTube source search routes through YouTubeRepository`() = runTest {
        coEvery { ytRepo.search("daft punk", null, null) } returns Pair(
            listOf(track("https://www.youtube.com/watch?v=A")),
            "next_page_token",
        )

        val vm = newViewModel()
        advanceUntilIdle()

        vm.onQueryChange("daft punk")
        advanceUntilIdle()

        vm.uiState.test {
            // Drain to current state
            var state = awaitItem()
            // onQueryChange triggers a 400ms debounce + search; advance to flush
            while (state.results.isEmpty() && state.error == null) {
                state = awaitItem()
            }
            assertThat(state.results).hasSize(1)
            assertThat(state.results.first().url).isEqualTo("https://www.youtube.com/watch?v=A")
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { ytRepo.search("daft punk", null, null) }
        coVerify(exactly = 0) { ytmRepo.search(any(), any()) }
    }

    @Test fun `YouTubeMusic source search routes through YouTubeMusicRepository`() = runTest {
        coEvery { ytmRepo.search("daft punk", null) } returns listOf(
            track("https://www.youtube.com/watch?v=B")
        )

        val vm = newViewModel()
        advanceUntilIdle()

        vm.setActiveSource(YouTubeSource.YouTubeMusic)
        // Stub the YTM home call that setActiveSource triggers
        coEvery { ytmRepo.getHome() } returns mockk(relaxed = true)
        advanceUntilIdle()

        vm.onQueryChange("daft punk")
        advanceUntilIdle()

        vm.uiState.test {
            var state = awaitItem()
            while (state.results.isEmpty() && state.error == null) {
                state = awaitItem()
            }
            assertThat(state.results).hasSize(1)
            assertThat(state.results.first().url).isEqualTo("https://www.youtube.com/watch?v=B")
            assertThat(state.hasMore).isFalse()  // YTM has no pagination
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { ytmRepo.search("daft punk", null) }
        coVerify(exactly = 0) { ytRepo.search("daft punk", any(), any()) }
    }

    @Test fun `default source from settings determines initial activeSource`() = runTest {
        every { settings.youtubeDefaultSource } returns flowOf("youtube_music")
        coEvery { ytmRepo.getHome() } returns mockk(relaxed = true)

        val vm = newViewModel()
        advanceUntilIdle()

        vm.uiState.test {
            assertThat(awaitItem().activeSource).isEqualTo(YouTubeSource.YouTubeMusic)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { ytmRepo.getHome() }
    }

    private fun newViewModel(): YouTubeViewModel = YouTubeViewModel(
        settingsDataStore = settings,
        youtubeRepository = ytRepo,
        youtubeMusicRepository = ytmRepo,
        playlistRepository = playlistRepo,
        trackDao = trackDao,
        database = database,
        recentSearchDao = recentSearchDao,
        favoriteDao = favoriteDao,
    )

    private fun track(url: String) = SearchResult(
        type = SearchResultType.YOUTUBE_TRACK,
        name = "name",
        url = url,
        imageUrl = null,
        artist = null,
        album = null,
        genre = null,
        releaseDate = null,
    )
}
