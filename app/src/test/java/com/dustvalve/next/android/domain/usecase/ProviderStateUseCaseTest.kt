package com.dustvalve.next.android.domain.usecase

import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.model.MusicProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProviderStateUseCaseTest {

    private fun useCase(bandcamp: Boolean, youtube: Boolean): Pair<ProviderStateUseCase, SettingsDataStore> {
        val store = mockk<SettingsDataStore>(relaxed = true)
        every { store.bandcampEnabled } returns flowOf(bandcamp)
        every { store.youtubeEnabled } returns flowOf(youtube)
        return ProviderStateUseCase(store) to store
    }

    @Test fun `LOCAL is always active`() = runTest {
        val (uc, _) = useCase(bandcamp = false, youtube = false)
        assertThat(uc.activeProviders.first()).containsExactly(MusicProvider.LOCAL)
    }

    @Test fun `enabled providers are included`() = runTest {
        val (uc, _) = useCase(bandcamp = true, youtube = true)
        assertThat(uc.activeProviders.first()).containsExactly(
            MusicProvider.LOCAL,
            MusicProvider.BANDCAMP,
            MusicProvider.YOUTUBE,
        )
    }

    @Test fun `only bandcamp enabled`() = runTest {
        val (uc, _) = useCase(bandcamp = true, youtube = false)
        assertThat(uc.activeProviders.first()).containsExactly(
            MusicProvider.LOCAL,
            MusicProvider.BANDCAMP,
        )
    }

    @Test fun `setEnabled routes to the matching setting`() = runTest {
        val (uc, store) = useCase(bandcamp = true, youtube = true)
        uc.setEnabled(MusicProvider.BANDCAMP, false)
        coVerify(exactly = 1) { store.setBandcampEnabled(false) }
        uc.setEnabled(MusicProvider.YOUTUBE, true)
        coVerify(exactly = 1) { store.setYoutubeEnabled(true) }
    }

    @Test fun `setEnabled ignores LOCAL`() = runTest {
        val (uc, store) = useCase(bandcamp = true, youtube = true)
        uc.setEnabled(MusicProvider.LOCAL, false)
        coVerify(exactly = 0) { store.setBandcampEnabled(any()) }
        coVerify(exactly = 0) { store.setYoutubeEnabled(any()) }
    }
}
