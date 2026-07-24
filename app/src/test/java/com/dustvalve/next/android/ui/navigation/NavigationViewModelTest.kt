package com.dustvalve.next.android.ui.navigation

import androidx.lifecycle.SavedStateHandle
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

    private fun viewModel(
        active: Set<MusicProvider>,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): NavigationViewModel {
        every { providerState.activeProviders } returns flowOf(active)
        return NavigationViewModel(providerState, ytRepo, sniffer, savedStateHandle)
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

    @Test fun `scheme-less dotted text is never sniffed`() = runTest(testDispatcher) {
        val vm = viewModel(setOf(MusicProvider.LOCAL, MusicProvider.BANDCAMP))
        vm.unsupportedLinkEvents.test {
            vm.openLink("will.i.am")
            advanceUntilIdle()
            awaitItem()
            cancelAndConsumeRemainingEvents()
        }
        coVerify(exactly = 0) { sniffer.sniff(any()) }
    }

    @Test fun `explicit https input still reaches the sniffer`() = runTest(testDispatcher) {
        coEvery { sniffer.sniff(any()) } returns null
        val vm = viewModel(setOf(MusicProvider.LOCAL))
        vm.openLink("https://music.customdomain.com/album/x")
        advanceUntilIdle()

        coVerify(exactly = 1) { sniffer.sniff("https://music.customdomain.com/album/x") }
    }

    @Test fun `sniffer failure surfaces unsupported event instead of crashing`() = runTest(testDispatcher) {
        coEvery { sniffer.sniff(any()) } throws IllegalStateException("boom")
        val vm = viewModel(setOf(MusicProvider.LOCAL))
        vm.unsupportedLinkEvents.test {
            vm.openLink("https://music.customdomain.com/album/x")
            advanceUntilIdle()
            awaitItem()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test fun `stack overflow drops oldest detail entry but keeps the tab home`() = runTest(testDispatcher) {
        val vm = viewModel(setOf(MusicProvider.LOCAL))
        advanceUntilIdle()

        // 25 pushes past MAX_STACK_DEPTH (20).
        for (i in 1..25) {
            vm.navigateTo(NavDestination.AlbumDetail("https://example.com/album/a$i"))
        }

        val stack = vm.backStack.value
        assertThat(stack.size).isEqualTo(20)
        // Index 0 must remain the tab's home destination, never a detail page.
        assertThat(stack.first()).isEqualTo(NavDestination.LocalHome)
        assertThat(stack.last()).isEqualTo(NavDestination.AlbumDetail("https://example.com/album/a25"))
    }

    @Test fun `state survives a save-restore round trip`() = runTest(testDispatcher) {
        val handle = SavedStateHandle()
        val vm = viewModel(setOf(MusicProvider.LOCAL, MusicProvider.BANDCAMP, MusicProvider.YOUTUBE), handle)
        advanceUntilIdle()

        vm.navigateTo(NavDestination.BandcampHome)
        vm.navigateTo(
            NavDestination.ArtistDetail(
                url = "https://foo.bandcamp.com",
                sourceId = "bandcamp",
                name = "Foo | Bar",
                imageUrl = null,
            ),
        )
        vm.navigateTo(NavDestination.YouTubeHome)
        vm.navigateTo(NavDestination.PlaylistDetail("pl_42"))
        vm.expandPlayer()
        advanceUntilIdle()

        // Fresh VM with the same handle = process death + restore.
        val restored = viewModel(setOf(MusicProvider.LOCAL, MusicProvider.BANDCAMP, MusicProvider.YOUTUBE), handle)
        advanceUntilIdle()

        assertThat(restored.currentTab.value).isEqualTo(BottomNavItem.YOUTUBE)
        assertThat(restored.backStack.value)
            .containsExactly(NavDestination.YouTubeHome, NavDestination.PlaylistDetail("pl_42"))
            .inOrder()
        assertThat(restored.showFullPlayer.value).isTrue()

        // The Bandcamp tab's stack (incl. the pipe-containing name hint) survives too.
        restored.navigateTo(NavDestination.BandcampHome)
        assertThat(restored.backStack.value)
            .containsExactly(
                NavDestination.BandcampHome,
                NavDestination.ArtistDetail(
                    url = "https://foo.bandcamp.com",
                    sourceId = "bandcamp",
                    name = "Foo | Bar",
                    imageUrl = null,
                ),
            )
            .inOrder()
    }

    @Test fun `destination encoding round trips every shape`() {
        val destinations = listOf(
            NavDestination.LocalHome,
            NavDestination.BandcampHome,
            NavDestination.YouTubeHome,
            NavDestination.Library,
            NavDestination.Settings,
            NavDestination.AccountLogin,
            NavDestination.YouTubeMusicLogin,
            NavDestination.AlbumDetail("https://a.bandcamp.com/album/x%7Cy|z"),
            NavDestination.ArtistDetail("https://a.bandcamp.com", "bandcamp", "Na|me", "https://img/pic.jpg"),
            NavDestination.ArtistDetail("https://youtube.com/channel/UC1", "youtube", null, null),
            NavDestination.PlaylistDetail("pl|1"),
            NavDestination.CollectionDetail("https://youtube.com/playlist?list=L", "youtube", "Mix | 2024"),
        )
        for (dest in destinations) {
            val decoded = NavigationViewModel.decodeDestination(NavigationViewModel.encodeDestination(dest))
            assertThat(decoded).isEqualTo(dest)
        }
    }

    @Test fun `garbage saved destination decodes to null`() {
        assertThat(NavigationViewModel.decodeDestination("nonsense|x")).isNull()
        assertThat(NavigationViewModel.decodeDestination("album")).isNull()
        assertThat(NavigationViewModel.decodeDestination("")).isNull()
    }
}
