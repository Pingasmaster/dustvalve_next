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

    private fun useCase(
        local: Boolean = false,
        bandcamp: Boolean,
        youtube: Boolean,
        soundcloud: Boolean = false,
    ): Pair<ProviderStateUseCase, SettingsDataStore> {
        val store = mockk<SettingsDataStore>(relaxed = true)
        every { store.localMusicEnabled } returns flowOf(local)
        every { store.bandcampEnabled } returns flowOf(bandcamp)
        every { store.youtubeEnabled } returns flowOf(youtube)
        every { store.soundcloudEnabled } returns flowOf(soundcloud)
        return ProviderStateUseCase(store) to store
    }

    @Test fun `no providers when all sources disabled`() = runTest {
        val (uc, _) = useCase(local = false, bandcamp = false, youtube = false)
        assertThat(uc.activeProviders.first()).isEmpty()
    }

    @Test fun `LOCAL follows localMusicEnabled`() = runTest {
        val (uc, _) = useCase(local = true, bandcamp = false, youtube = false)
        assertThat(uc.activeProviders.first()).containsExactly(MusicProvider.LOCAL)
    }

    @Test fun `enabled providers are included`() = runTest {
        val (uc, _) = useCase(local = true, bandcamp = true, youtube = true, soundcloud = true)
        assertThat(uc.activeProviders.first()).containsExactly(
            MusicProvider.LOCAL,
            MusicProvider.BANDCAMP,
            MusicProvider.YOUTUBE,
            MusicProvider.SOUNDCLOUD,
        )
    }

    @Test fun `only bandcamp enabled`() = runTest {
        val (uc, _) = useCase(local = false, bandcamp = true, youtube = false)
        assertThat(uc.activeProviders.first()).containsExactly(
            MusicProvider.BANDCAMP,
        )
    }

    @Test fun `setEnabled routes to the matching setting`() = runTest {
        val (uc, store) = useCase(local = true, bandcamp = true, youtube = true, soundcloud = true)
        uc.setEnabled(MusicProvider.BANDCAMP, false)
        coVerify(exactly = 1) { store.setBandcampEnabled(false) }
        uc.setEnabled(MusicProvider.YOUTUBE, true)
        coVerify(exactly = 1) { store.setYoutubeEnabled(true) }
        uc.setEnabled(MusicProvider.SOUNDCLOUD, true)
        coVerify(exactly = 1) { store.setSoundcloudEnabled(true) }
        uc.setEnabled(MusicProvider.LOCAL, false)
        coVerify(exactly = 1) { store.setLocalMusicEnabled(false) }
    }
}
