package com.dustvalve.next.android.domain.usecase

import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.model.MusicProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderStateUseCase @Inject constructor(private val settingsDataStore: SettingsDataStore) {
    val activeProviders: Flow<Set<MusicProvider>> = combine(
        settingsDataStore.localMusicEnabled,
        settingsDataStore.bandcampEnabled,
        settingsDataStore.youtubeEnabled,
        settingsDataStore.soundcloudEnabled,
    ) { local, bc, yt, sc ->
        buildSet {
            if (local) add(MusicProvider.LOCAL)
            if (bc) add(MusicProvider.BANDCAMP)
            if (yt) add(MusicProvider.YOUTUBE)
            if (sc) add(MusicProvider.SOUNDCLOUD)
        }
    }

    /** Enable or disable a provider, including LOCAL (local music source). */
    suspend fun setEnabled(provider: MusicProvider, enabled: Boolean) {
        when (provider) {
            MusicProvider.BANDCAMP -> settingsDataStore.setBandcampEnabled(enabled)
            MusicProvider.YOUTUBE -> settingsDataStore.setYoutubeEnabled(enabled)
            MusicProvider.SOUNDCLOUD -> settingsDataStore.setSoundcloudEnabled(enabled)
            MusicProvider.LOCAL -> settingsDataStore.setLocalMusicEnabled(enabled)
        }
    }
}
