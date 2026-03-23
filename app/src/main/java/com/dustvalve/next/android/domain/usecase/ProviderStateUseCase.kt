package com.dustvalve.next.android.domain.usecase

import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.model.MusicProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderStateUseCase @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) {
    val activeProviders: Flow<Set<MusicProvider>> = combine(
        settingsDataStore.bandcampEnabled,
        settingsDataStore.youtubeEnabled,
        settingsDataStore.spotifyEnabled,
    ) { bc, yt, sp ->
        buildSet {
            add(MusicProvider.LOCAL)
            if (bc) add(MusicProvider.BANDCAMP)
            if (yt) add(MusicProvider.YOUTUBE)
            if (sp) add(MusicProvider.SPOTIFY)
        }
    }
}
