package com.dustvalve.next.android.ui.screens.bandcamp

import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.DiscoverResult
import com.dustvalve.next.android.domain.usecase.DiscoverDustvalveUseCase
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
class BandcampViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var discover: DiscoverDustvalveUseCase
    private lateinit var settings: SettingsDataStore

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        discover = mockk()
        settings = mockk(relaxed = true)
        every { settings.bandcampCustomGenres } returns flowOf(emptyList())
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `slugify normalizes names`() {
        assertThat(slugifyGenre("Hip-Hop/Rap")).isEqualTo("hip-hop-rap")
        assertThat(slugifyGenre("  Shoegaze ")).isEqualTo("shoegaze")
        assertThat(slugifyGenre("R&B / Soul")).isEqualTo("r-b-soul")
    }

    @Test fun `genre with no releases is rejected and not persisted`() = runTest(dispatcher) {
        coEvery { discover.invoke(any(), any()) } returns DiscoverResult(emptyList())
        val vm = BandcampViewModel(discover, settings)
        vm.setNewGenreText("asdfqwer")
        vm.addCustomGenre()
        advanceUntilIdle()

        assertThat(vm.uiState.value.genreError).isEqualTo(GenreError.NOT_FOUND)
        coVerify(exactly = 0) { settings.setBandcampCustomGenres(any()) }
    }

    @Test fun `real genre with releases is persisted`() = runTest(dispatcher) {
        coEvery { discover.invoke(any(), any()) } returns DiscoverResult(listOf(mockk<Album>()))
        val vm = BandcampViewModel(discover, settings)
        vm.setNewGenreText("shoegaze")
        vm.addCustomGenre()
        advanceUntilIdle()

        assertThat(vm.uiState.value.genreError).isNull()
        coVerify { settings.setBandcampCustomGenres(listOf("shoegaze")) }
    }

    @Test fun `category retry after failure clears the error and shows results`() = runTest(dispatcher) {
        var fail = true
        coEvery { discover.invoke(any(), any()) } answers {
            if (fail) throw RuntimeException("network down") else DiscoverResult(listOf(mockk<Album>()))
        }
        val vm = BandcampViewModel(discover, settings)
        vm.selectCategory("rock", "rock")
        advanceUntilIdle()

        assertThat(vm.uiState.value.categoryError).isNotNull()
        assertThat(vm.uiState.value.isCategoryLoading).isFalse()

        // Retry succeeds: the sheet must leave the error dead-end.
        fail = false
        vm.retryCategory()
        assertThat(vm.uiState.value.isCategoryLoading).isTrue()
        assertThat(vm.uiState.value.categoryError).isNull()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.categoryError).isNull()
        assertThat(state.isCategoryLoading).isFalse()
        assertThat(state.categoryAlbums).hasSize(1)
    }
}
