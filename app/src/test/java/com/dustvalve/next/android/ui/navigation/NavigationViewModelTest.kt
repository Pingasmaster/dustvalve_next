package com.dustvalve.next.android.ui.navigation

import app.cash.turbine.test
import com.dustvalve.next.android.data.remote.BandcampDomainSniffer
import com.dustvalve.next.android.domain.model.MusicProvider
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import com.dustvalve.next.android.domain.usecase.ProviderStateUseCase
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
class NavigationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var providerState: ProviderStateUseCase
    private lateinit var ytRepo: YouTubeRepository
    private lateinit var sniffer: BandcampDomainSniffer

    private fun viewModel(active: Set<MusicProvider>): NavigationViewModel {
        every { providerState.activeProviders } returns flowOf(active)
        return NavigationViewModel(providerState, ytRepo, sniffer)
    }

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        providerState = mockk(relaxed = true)
        ytRepo = mockk(relaxed = true)
        sniffer = mockk(relaxed = true)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `link to disabled provider raises enable dialog`() = runTest(testDispatcher) {
        val vm = viewModel(setOf(MusicProvider.LOCAL))
        vm.openLink("https://youtu.be/dQw4w9WgXcQ")
        advanceUntilIdle()

        val pending = vm.pendingLinkConfirmation.value
        assertThat(pending).isNotNull()
        assertThat(pending!!.provider).isEqualTo(MusicProvider.YOUTUBE)
    }

    @Test fun `confirming enables provider and clears pending`() = runTest(testDispatcher) {
        coEvery { ytRepo.getTrackInfo(any()) } returns mockk(relaxed = true)
        val vm = viewModel(setOf(MusicProvider.LOCAL))
        vm.openLink("https://youtu.be/dQw4w9WgXcQ")
        advanceUntilIdle()

        vm.confirmPendingLink()
        advanceUntilIdle()

        coVerify { providerState.setEnabled(MusicProvider.YOUTUBE, true) }
        assertThat(vm.pendingLinkConfirmation.value).isNull()
        assertThat(vm.currentTab.value).isEqualTo(BottomNavItem.YOUTUBE)
    }

    @Test fun `link to enabled provider executes immediately without dialog`() = runTest(testDispatcher) {
        val vm = viewModel(setOf(MusicProvider.LOCAL, MusicProvider.BANDCAMP))
        vm.openLink("https://artist.bandcamp.com/album/the-album")
        advanceUntilIdle()

        assertThat(vm.pendingLinkConfirmation.value).isNull()
        assertThat(vm.backStack.value.last()).isInstanceOf(NavDestination.AlbumDetail::class.java)
    }

    @Test fun `unsupported link emits event`() = runTest(testDispatcher) {
        val vm = viewModel(setOf(MusicProvider.LOCAL))
        vm.unsupportedLinkEvents.test {
            vm.openLink("not a link")
            advanceUntilIdle()
            awaitItem()
            cancelAndConsumeRemainingEvents()
        }
    }
}
