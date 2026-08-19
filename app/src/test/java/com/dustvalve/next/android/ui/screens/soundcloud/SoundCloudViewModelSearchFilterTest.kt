package com.dustvalve.next.android.ui.screens.soundcloud

import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.model.SoundCloudHomeFeed
import com.dustvalve.next.android.domain.repository.RecentSearchRepository
import com.dustvalve.next.android.domain.repository.SoundCloudRepository
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
class SoundCloudViewModelSearchFilterTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: SoundCloudRepository
    private lateinit var recentSearchRepo: RecentSearchRepository
    private lateinit var settings: SettingsDataStore

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = mockk()
        recentSearchRepo = mockk(relaxed = true)
        settings = mockk(relaxed = true)
        every { settings.searchHistoryEnabled } returns flowOf(false)
        every { settings.searchHistorySoundcloud } returns flowOf(false)
        every { recentSearchRepo.getRecent(any(), any()) } returns flowOf(emptyList())
        coEvery { repo.getHome(any()) } returns SoundCloudHomeFeed(
            genre = "all-music",
            trending = emptyList(),
            shelves = emptyList(),
        )
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `filter chips pass All Artists Albums Tracks into repository search`() = runTest(dispatcher) {
        coEvery { repo.search(any(), any()) } returns emptyList()
        coEvery { repo.search("daft punk", null) } returns listOf(trackResult("all"))
        coEvery { repo.search("daft punk", "users") } returns listOf(trackResult("users"))
        coEvery { repo.search("daft punk", "albums") } returns listOf(trackResult("albums"))
        coEvery { repo.search("daft punk", "tracks") } returns listOf(trackResult("tracks"))

        val vm = SoundCloudViewModel(repo, recentSearchRepo, settings)
        advanceUntilIdle()

        vm.onQueryChange("daft punk")
        vm.onSearch()
        advanceUntilIdle()
        assertThat(vm.uiState.value.selectedFilter).isNull()
        assertThat(vm.uiState.value.results.single().name).isEqualTo("all")
        coVerify { repo.search("daft punk", null) }

        vm.onFilterSelected("users")
        advanceUntilIdle()
        assertThat(vm.uiState.value.selectedFilter).isEqualTo("users")
        assertThat(vm.uiState.value.results.single().name).isEqualTo("users")
        coVerify { repo.search("daft punk", "users") }

        vm.onFilterSelected("albums")
        advanceUntilIdle()
        assertThat(vm.uiState.value.selectedFilter).isEqualTo("albums")
        coVerify { repo.search("daft punk", "albums") }

        vm.onFilterSelected("tracks")
        advanceUntilIdle()
        assertThat(vm.uiState.value.selectedFilter).isEqualTo("tracks")
        coVerify { repo.search("daft punk", "tracks") }

        vm.onFilterSelected(null)
        advanceUntilIdle()
        assertThat(vm.uiState.value.selectedFilter).isNull()
    }

    private fun trackResult(name: String) = SearchResult(
        type = SearchResultType.SOUNDCLOUD_TRACK,
        name = name,
        url = "https://soundcloud.com/$name",
        imageUrl = null,
        artist = null,
        album = null,
        genre = null,
        releaseDate = null,
    )
}
