package com.dustvalve.next.android.ui.screens.search

import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.LocalMusicRepository
import com.dustvalve.next.android.domain.repository.RecentSearchRepository
import com.dustvalve.next.android.domain.usecase.GetAlbumDetailUseCase
import com.dustvalve.next.android.domain.usecase.SearchDustvalveUseCase
import com.dustvalve.next.android.util.UiText
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var search: SearchDustvalveUseCase
    private lateinit var albumDetail: GetAlbumDetailUseCase
    private lateinit var recentSearchRepository: RecentSearchRepository
    private lateinit var localMusicRepository: LocalMusicRepository
    private lateinit var settings: SettingsDataStore

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        search = mockk()
        albumDetail = mockk()
        recentSearchRepository = mockk(relaxed = true)
        localMusicRepository = mockk(relaxed = true)
        settings = mockk(relaxed = true)
        every { recentSearchRepository.getRecent(any(), any()) } returns flowOf(emptyList())
        every { settings.searchHistoryEnabled } returns flowOf(true)
        every { settings.searchHistoryBandcamp } returns flowOf(true)
        every { settings.localMusicEnabled } returns flowOf(false)
        coEvery { localMusicRepository.searchLocalTracks(any()) } returns emptyList()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SearchViewModel(
        search,
        albumDetail,
        recentSearchRepository,
        localMusicRepository,
        settings,
        dispatcher,
    )

    private fun result(url: String, type: SearchResultType = SearchResultType.ALBUM) = SearchResult(
        type = type,
        name = url,
        url = url,
        imageUrl = null,
        artist = null,
        album = null,
        genre = null,
        releaseDate = null,
    )

    @Test fun `query change debounces before searching`() = runTest(dispatcher) {
        coEvery { search.invoke(any(), any(), any()) } returns listOf(result("a"))
        val vm = vm()
        vm.onQueryChange("beat")
        advanceTimeBy(200L)
        // Still inside the debounce window: no search yet.
        coVerify(exactly = 0) { search.invoke(any(), any(), any()) }
        advanceUntilIdle()
        coVerify(exactly = 1) { search.invoke("beat", 1, null) }
        assertThat(vm.uiState.value.results).hasSize(1)
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test fun `rapid typing only fires one search`() = runTest(dispatcher) {
        coEvery { search.invoke(any(), any(), any()) } returns emptyList()
        val vm = vm()
        vm.onQueryChange("b")
        advanceTimeBy(100L)
        vm.onQueryChange("be")
        advanceTimeBy(100L)
        vm.onQueryChange("bea")
        advanceUntilIdle()
        coVerify(exactly = 1) { search.invoke("bea", 1, null) }
    }

    @Test fun `blank query clears results without searching`() = runTest(dispatcher) {
        coEvery { search.invoke(any(), any(), any()) } returns listOf(result("a"))
        val vm = vm()
        vm.onQueryChange("beat")
        advanceUntilIdle()
        assertThat(vm.uiState.value.results).isNotEmpty()

        vm.onQueryChange("")
        advanceUntilIdle()
        assertThat(vm.uiState.value.results).isEmpty()
        assertThat(vm.uiState.value.hasMore).isTrue()
        assertThat(vm.uiState.value.error).isNull()
        coVerify(exactly = 1) { search.invoke(any(), any(), any()) }
    }

    @Test fun `onSearch saves recent search and searches immediately`() = runTest(dispatcher) {
        coEvery { search.invoke(any(), any(), any()) } returns emptyList()
        val vm = vm()
        vm.onQueryChange("  beatles ")
        vm.onSearch()
        advanceUntilIdle()
        coVerify { recentSearchRepository.add("beatles", "bandcamp", trim = true) }
        coVerify(exactly = 1) { search.invoke(any(), any(), any()) }
    }

    @Test fun `recent search is not saved when history is disabled`() = runTest(dispatcher) {
        every { settings.searchHistoryEnabled } returns flowOf(false)
        coEvery { search.invoke(any(), any(), any()) } returns emptyList()
        val vm = vm()
        // Subscribe so the WhileSubscribed stateIn actually collects the flow.
        val job = launch { vm.searchHistoryEnabled.collect {} }
        advanceUntilIdle()

        vm.onQueryChange("beatles")
        vm.onSearch()
        advanceUntilIdle()
        coVerify(exactly = 0) { recentSearchRepository.add(any(), any(), any()) }
        job.cancel()
    }

    @Test fun `loadMore does nothing after page 1 because Bandcamp has no pagination`() = runTest(dispatcher) {
        coEvery { search.invoke(any(), 1, any()) } returns listOf(result("a"), result("b"))
        val vm = vm()
        vm.onQueryChange("beat")
        advanceUntilIdle()
        assertThat(vm.uiState.value.hasMore).isFalse()

        vm.loadMore()
        advanceUntilIdle()
        coVerify(exactly = 1) { search.invoke(any(), any(), any()) }
        assertThat(vm.uiState.value.results).hasSize(2)
    }

    @Test fun `loadMore does nothing when hasMore is false`() = runTest(dispatcher) {
        coEvery { search.invoke(any(), 1, any()) } returns emptyList()
        val vm = vm()
        vm.onQueryChange("beat")
        advanceUntilIdle()
        assertThat(vm.uiState.value.hasMore).isFalse()

        vm.loadMore()
        advanceUntilIdle()
        coVerify(exactly = 1) { search.invoke(any(), any(), any()) }
    }

    @Test fun `search failure surfaces error and stops loading`() = runTest(dispatcher) {
        coEvery { search.invoke(any(), any(), any()) } throws IOException("offline")
        val vm = vm()
        vm.onQueryChange("beat")
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isEqualTo(UiText.DynamicString("offline"))
        assertThat(vm.uiState.value.isLoading).isFalse()

        vm.clearError()
        assertThat(vm.uiState.value.error).isNull()
    }

    @Test fun `type filter resets results and re-searches with the type`() = runTest(dispatcher) {
        coEvery { search.invoke(any(), any(), any()) } returns listOf(result("a"))
        val vm = vm()
        vm.onQueryChange("beat")
        advanceUntilIdle()

        coEvery { search.invoke(any(), any(), SearchResultType.ARTIST) } returns listOf(result("artist", SearchResultType.ARTIST))
        vm.onTypeSelected(SearchResultType.ARTIST)
        advanceUntilIdle()

        assertThat(vm.uiState.value.selectedType).isEqualTo(SearchResultType.ARTIST)
        assertThat(vm.uiState.value.results.single().type).isEqualTo(SearchResultType.ARTIST)
        coVerify(exactly = 1) { search.invoke("beat", 1, SearchResultType.ARTIST) }
    }

    @Test fun `local filter searches only the local db and disables paging`() = runTest(dispatcher) {
        every { settings.localMusicEnabled } returns flowOf(true)
        coEvery { localMusicRepository.searchLocalTracks("beat") } returns listOf(
            Track(
                id = "l1", albumId = "al", title = "beat it", artist = "MJ",
                trackNumber = 1, duration = 60f, streamUrl = null, artUrl = "",
                albumTitle = "Thriller", source = TrackSource.LOCAL,
            ),
        )
        val vm = vm()
        val job = launch { vm.localSearchEnabled.collect {} }
        advanceUntilIdle()

        vm.onQueryChange("beat")
        vm.onTypeSelected(SearchResultType.LOCAL_TRACK)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.results.single().url).isEqualTo("local://l1")
        assertThat(state.results.single().type).isEqualTo(SearchResultType.LOCAL_TRACK)
        assertThat(state.hasMore).isFalse()
        coVerify(exactly = 0) { search.invoke(any(), any(), SearchResultType.LOCAL_TRACK) }
        job.cancel()
    }

    @Test fun `recent search management delegates to the repository`() = runTest(dispatcher) {
        val vm = vm()
        vm.removeRecentSearch("beatles")
        vm.clearRecentSearches()
        advanceUntilIdle()
        coVerify { recentSearchRepository.remove("beatles", "bandcamp") }
        coVerify { recentSearchRepository.clear("bandcamp") }
    }

    @Test fun `resolveBandcampTrack matches title_link URL not first album track`() = runTest(dispatcher) {
        val wantedUrl = "https://artist.bandcamp.com/track/second"
        val album = com.dustvalve.next.android.domain.model.Album(
            id = "alb",
            url = "https://artist.bandcamp.com/album/x",
            title = "Album",
            artist = "Artist",
            artistUrl = "https://artist.bandcamp.com",
            artUrl = "",
            releaseDate = null,
            about = null,
            tracks = listOf(
                Track(
                    id = "1", albumId = "alb", title = "First", artist = "Artist",
                    trackNumber = 1, duration = 1f, streamUrl = "https://s/1", artUrl = "",
                    albumTitle = "Album",
                    bandcampTrackUrl = "https://artist.bandcamp.com/track/first",
                ),
                Track(
                    id = "2", albumId = "alb", title = "Second", artist = "Artist",
                    trackNumber = 2, duration = 1f, streamUrl = "https://s/2", artUrl = "",
                    albumTitle = "Album",
                    bandcampTrackUrl = wantedUrl,
                ),
            ),
            tags = emptyList(),
        )
        coEvery { albumDetail.invoke(wantedUrl) } returns album
        val vm = vm()
        val resolved = vm.resolveBandcampTrack(wantedUrl)
        assertThat(resolved?.id).isEqualTo("2")
        assertThat(resolved?.title).isEqualTo("Second")
    }

    @Test fun `resolveBandcampTrack returns null when no URL match`() = runTest(dispatcher) {
        val searchUrl = "https://artist.bandcamp.com/track/missing"
        val album = com.dustvalve.next.android.domain.model.Album(
            id = "alb",
            url = "https://artist.bandcamp.com/album/x",
            title = "Album",
            artist = "Artist",
            artistUrl = "https://artist.bandcamp.com",
            artUrl = "",
            releaseDate = null,
            about = null,
            tracks = listOf(
                Track(
                    id = "1", albumId = "alb", title = "Only", artist = "Artist",
                    trackNumber = 1, duration = 1f, streamUrl = "https://s/1", artUrl = "",
                    albumTitle = "Album",
                    bandcampTrackUrl = "https://artist.bandcamp.com/track/only",
                ),
            ),
            tags = emptyList(),
        )
        coEvery { albumDetail.invoke(searchUrl) } returns album
        val vm = vm()
        assertThat(vm.resolveBandcampTrack(searchUrl)).isNull()
    }

    @Test fun `stale loadMore is discarded after generation bump`() = runTest(dispatcher) {
        coEvery { search.invoke(any(), 1, any()) } returns listOf(result("a"), result("b"))
        coEvery { search.invoke(any(), 2, any()) } coAnswers {
            // Simulate a slow page-2 that finishes after a filter reset.
            kotlinx.coroutines.yield()
            listOf(result("stale"))
        }
        val vm = vm()
        vm.onQueryChange("beat")
        advanceUntilIdle()
        assertThat(vm.uiState.value.results.map { it.url }).containsExactly("a", "b").inOrder()

        // Start loadMore, then bump generation via type filter before page-2 lands.
        vm.loadMore()
        coEvery { search.invoke(any(), 1, SearchResultType.ARTIST) } returns listOf(
            result("artist", SearchResultType.ARTIST),
        )
        vm.onTypeSelected(SearchResultType.ARTIST)
        advanceUntilIdle()

        assertThat(vm.uiState.value.results.map { it.url }).containsExactly("artist")
        assertThat(vm.uiState.value.results.none { it.url == "stale" }).isTrue()
    }
}
