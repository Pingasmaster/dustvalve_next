package com.dustvalve.next.android.ui.screens.youtube

import app.cash.turbine.test
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.model.FavoriteType
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.repository.RecentSearchRepository
import com.dustvalve.next.android.domain.repository.TrackCacheRepository
import com.dustvalve.next.android.domain.repository.YouTubeMusicRepository
import com.dustvalve.next.android.domain.repository.YouTubePlaylistResult
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
    private lateinit var recentSearchRepo: RecentSearchRepository
    private lateinit var trackCacheRepo: TrackCacheRepository

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)

        settings = mockk(relaxed = true)
        every { settings.youtubeDefaultSource } returns flowOf("youtube")
        every { settings.searchHistoryEnabled } returns flowOf(false)
        every { settings.lastYoutubeVideoId } returns flowOf(null)

        ytRepo = mockk()
        ytmRepo = mockk()
        playlistRepo = mockk(relaxed = true)
        recentSearchRepo = mockk(relaxed = true)
        trackCacheRepo = mockk(relaxed = true)

        every { recentSearchRepo.getRecent(any(), any()) } returns flowOf(emptyList())

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
            track("https://www.youtube.com/watch?v=B"),
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
            assertThat(state.hasMore).isFalse() // YTM has no pagination
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

    @Test fun `stale loadMore for the old query never appends into new results`() = runTest {
        coEvery { ytRepo.search("old", null, null) } returns Pair(
            listOf(track("https://www.youtube.com/watch?v=OLD1")),
            "T1",
        )
        coEvery { ytRepo.search("old", null, "T1") } returns Pair(
            listOf(track("https://www.youtube.com/watch?v=OLD2")),
            null,
        )
        coEvery { ytRepo.search("new", null, null) } returns Pair(
            listOf(track("https://www.youtube.com/watch?v=NEW1")),
            null,
        )

        val vm = newViewModel()
        advanceUntilIdle()

        vm.onQueryChange("old")
        advanceUntilIdle()
        assertThat(vm.uiState.value.results.map { it.url })
            .containsExactly("https://www.youtube.com/watch?v=OLD1")

        // Page-2 fetch for the OLD query is in flight when the query changes:
        // it must be cancelled, never appended into the new result list.
        vm.loadMore()
        vm.onQueryChange("new")
        advanceUntilIdle()

        assertThat(vm.uiState.value.results.map { it.url })
            .containsExactly("https://www.youtube.com/watch?v=NEW1")
        // The stale page-2 request must have been cancelled outright.
        coVerify(exactly = 0) { ytRepo.search("old", null, "T1") }
    }

    @Test fun `importPlaylist reports failure to the caller and sets the error state`() = runTest {
        coEvery { ytRepo.getPlaylistTracks(any()) } throws RuntimeException("boom")

        val vm = newViewModel()
        advanceUntilIdle()

        val imported = vm.importPlaylist("https://www.youtube.com/playlist?list=X", "Mix").await()

        assertThat(imported).isFalse()
        assertThat(vm.uiState.value.error).isNotNull()
    }

    @Test fun `importPlaylist fails when the playlist has no tracks`() = runTest {
        coEvery { ytRepo.getPlaylistTracks(any()) } returns
            YouTubePlaylistResult(tracks = emptyList(), title = "Empty Mix")

        val vm = newViewModel()
        advanceUntilIdle()

        val imported = vm.importPlaylist("https://www.youtube.com/playlist?list=RDempty", "Mix").await()

        assertThat(imported).isFalse()
        assertThat(vm.uiState.value.error).isNotNull()
        coVerify(exactly = 0) { playlistRepo.importTracksAsPlaylist(any(), any(), any(), any(), any()) }
    }

    @Test fun `importPlaylist imports via the repository with the favorite inside the transaction`() = runTest {
        val playlistUrl = "https://www.youtube.com/playlist?list=X"
        val tracks = listOf(importedTrack("yt_a"), importedTrack("yt_b"))
        coEvery { ytRepo.getPlaylistTracks(playlistUrl) } returns
            YouTubePlaylistResult(tracks = tracks, title = "Mix")
        coEvery {
            playlistRepo.importTracksAsPlaylist(any(), any(), any(), any(), any())
        } returns Playlist(id = "imported_1", name = "Mix")

        val vm = newViewModel()
        advanceUntilIdle()

        val imported = vm.importPlaylist(playlistUrl, "Mix").await()

        assertThat(imported).isTrue()
        assertThat(vm.uiState.value.error).isNull()
        coVerify(exactly = 1) {
            playlistRepo.importTracksAsPlaylist(
                name = "Mix",
                tracks = tracks,
                favoriteId = playlistUrl,
                favoriteType = FavoriteType.YOUTUBE_PLAYLIST,
                sourceUrl = playlistUrl,
            )
        }
    }

    private fun newViewModel(): YouTubeViewModel = YouTubeViewModel(
        settingsDataStore = settings,
        youtubeRepository = ytRepo,
        youtubeMusicRepository = ytmRepo,
        playlistRepository = playlistRepo,
        recentSearchRepository = recentSearchRepo,
        trackCacheRepository = trackCacheRepo,
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

    private fun importedTrack(id: String) = Track(
        id = id, albumId = "", title = id, artist = "", trackNumber = 0,
        duration = 0f, streamUrl = null, artUrl = "", albumTitle = "",
        source = TrackSource.YOUTUBE,
    )
}
