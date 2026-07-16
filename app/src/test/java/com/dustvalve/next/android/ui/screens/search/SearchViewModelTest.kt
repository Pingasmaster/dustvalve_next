package com.dustvalve.next.android.ui.screens.search

import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.RecentSearchDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
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
    private lateinit var recentSearchDao: RecentSearchDao
    private lateinit var trackDao: TrackDao
    private lateinit var favoriteDao: FavoriteDao
    private lateinit var settings: SettingsDataStore

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        search = mockk()
        albumDetail = mockk()
        recentSearchDao = mockk(relaxed = true)
        trackDao = mockk(relaxed = true)
        favoriteDao = mockk(relaxed = true)
        settings = mockk(relaxed = true)
        every { recentSearchDao.getRecent(any(), any()) } returns flowOf(emptyList())
        every { settings.searchHistoryEnabled } returns flowOf(true)
        every { settings.searchHistoryBandcamp } returns flowOf(true)
        every { settings.localMusicEnabled } returns flowOf(false)
        coEvery { trackDao.searchLocalTracks(any()) } returns emptyList()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SearchViewModel(
        search,
        albumDetail,
        recentSearchDao,
        trackDao,
        favoriteDao,
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
        coVerify { recentSearchDao.insert(match { it.query == "beatles" && it.source == "bandcamp" }) }
        coVerify { recentSearchDao.deleteOld(source = "bandcamp", keepCount = 20) }
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
        coVerify(exactly = 0) { recentSearchDao.insert(any()) }
        job.cancel()
    }

    @Test fun `loadMore appends deduplicated results and bumps page`() = runTest(dispatcher) {
        coEvery { search.invoke(any(), 1, any()) } returns listOf(result("a"), result("b"))
        coEvery { search.invoke(any(), 2, any()) } returns listOf(result("b"), result("c"))
        val vm = vm()
        vm.onQueryChange("beat")
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertThat(vm.uiState.value.results.map { it.url }).containsExactly("a", "b", "c").inOrder()
        assertThat(vm.uiState.value.page).isEqualTo(3)
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
        coEvery { trackDao.searchLocalTracks("beat") } returns listOf(
            TrackEntity(
                id = "l1", albumId = "al", title = "beat it", artist = "MJ",
                trackNumber = 1, duration = 60f, streamUrl = null, artUrl = "",
                albumTitle = "Thriller", source = "local",
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

    @Test fun `recent search management delegates to the dao`() = runTest(dispatcher) {
        val vm = vm()
        vm.removeRecentSearch("beatles")
        vm.clearRecentSearches()
        advanceUntilIdle()
        coVerify { recentSearchDao.delete("beatles", "bandcamp") }
        coVerify { recentSearchDao.clearAll("bandcamp") }
    }
}
