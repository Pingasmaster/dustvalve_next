package com.dustvalve.next.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.R
import com.dustvalve.next.android.cache.StorageTracker
import com.dustvalve.next.android.data.asset.AssetEvictionPolicy
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.model.CacheInfo
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.LocalMusicRepository
import com.dustvalve.next.android.domain.repository.RecentSearchRepository
import com.dustvalve.next.android.update.AppUpdateController
import com.dustvalve.next.android.update.UpdateUiState
import com.dustvalve.next.android.util.UiText
import com.dustvalve.next.android.util.runCatchingUiIgnore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val cacheInfo: CacheInfo? = null,
    val themeMode: String = "system",
    val dynamicColor: Boolean = true,
    val storageLimitIndex: Int = 3, // default 2 GB
    val autoDownloadFutureContent: Boolean = false,
    val downloadFormat: String = "flac",
    val saveDataOnMetered: Boolean = true,
    val progressiveDownload: Boolean = true,
    val seamlessQualityUpgrade: Boolean = true,
    val oledBlack: Boolean = false,
    val albumArtTheme: Boolean = false,
    val progressBarStyle: String = "wavy",
    val progressBarSizeDp: Int = 24,
    val autoDownloadFavorites: Boolean = false,
    val downloadNotificationsEnabled: Boolean = true,
    val localMusicEnabled: Boolean = false,
    val localMusicFolderUris: List<String> = emptyList(),
    val localMusicUseMediaStore: Boolean = true,
    val isScanning: Boolean = false,
    val scanMessage: UiText? = null,
    val bandcampEnabled: Boolean = false,
    val youtubeEnabled: Boolean = false,
    val soundcloudEnabled: Boolean = false,
    val showInlineVolumeSlider: Boolean = false,
    val showVolumeButton: Boolean = false,
    val searchHistoryEnabled: Boolean = true,
    val searchHistoryBandcamp: Boolean = true,
    val searchHistoryYoutube: Boolean = true,
    val searchHistorySoundcloud: Boolean = true,
    val searchHistoryLocal: Boolean = true,
    val searchHistoryClearedMessage: UiText? = null,
    val playerDebugOverlay: Boolean = false,
    val youtubeDefaultSource: String = "youtube",
    val keepScreenOnInApp: Boolean = false,
    val keepScreenOnWhilePlaying: Boolean = false,
    val keepLocalSort: Boolean = false,
    val keepLocalFilters: Boolean = false,
    val updateState: UpdateUiState = UpdateUiState.Idle,
    val updateMessage: UiText? = null,
    val autoUpdateCheckEnabled: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val storageTracker: StorageTracker,
    private val assetEvictionPolicy: AssetEvictionPolicy,
    private val settingsDataStore: SettingsDataStore,
    private val localMusicRepository: LocalMusicRepository,
    private val downloadRepository: DownloadRepository,
    private val recentSearchRepository: RecentSearchRepository,
    private val appUpdateController: AppUpdateController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val localMusic = LocalMusicSettingsCoordinator(
        scope = viewModelScope,
        uiState = _uiState,
        settingsDataStore = settingsDataStore,
        localMusicRepository = localMusicRepository,
    )

    init {
        SettingsUiCollectors(
            scope = viewModelScope,
            uiState = _uiState,
            storageTracker = storageTracker,
            settingsDataStore = settingsDataStore,
        ).start()
        // Mirror the process-wide update flow into our SettingsUiState so the
        // "Search for updates" row reflects whatever the cold-start silent
        // check (or an in-flight download) found.
        viewModelScope.launch {
            appUpdateController.state.collect { s ->
                _uiState.update { it.copy(updateState = s) }
            }
        }
        viewModelScope.launch {
            appUpdateController.messages.collect { m ->
                _uiState.update { it.copy(updateMessage = m) }
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setThemeMode(mode)
            }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setDynamicColor(enabled)
            }
        }
    }

    fun setOledBlack(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setOledBlack(enabled)
            }
        }
    }

    fun setAlbumArtTheme(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setAlbumArtTheme(enabled)
            }
        }
    }

    fun setProgressBarStyle(style: String) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setProgressBarStyle(style)
            }
        }
    }

    fun setProgressBarSizeDp(sizeDp: Int) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setProgressBarSizeDp(sizeDp)
            }
        }
    }

    fun setAutoDownloadFavorites(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setAutoDownloadFavorites(enabled)
            }
        }
    }

    fun setDownloadNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setDownloadNotificationsEnabled(enabled)
            }
        }
    }

    fun setStorageLimit(gb: Float) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                val bytes = when {
                    gb < 0f -> Long.MAX_VALUE

                    // unlimited
                    else -> (gb * 1024 * 1024 * 1024).toLong()
                }
                settingsDataStore.setStorageLimit(bytes)
                // Trim the unified pool's unpinned (auto-cached) entries down
                // to the new limit. Pinned user downloads are never evicted.
                val overage = storageTracker.getOverageBytes()
                if (overage > 0L) {
                    assetEvictionPolicy.evict(overage)
                    storageTracker.notifyChanged()
                }
            }
        }
    }

    fun setAutoDownloadFutureContent(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setAutoDownloadFutureContent(enabled)
            }
        }
    }

    fun setDownloadFormat(formatKey: String) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setDownloadFormat(formatKey)
            }
        }
    }

    fun setSaveDataOnMetered(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setSaveDataOnMetered(enabled)
            }
        }
    }

    fun setProgressiveDownload(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setProgressiveDownload(enabled)
            }
        }
    }

    fun setSeamlessQualityUpgrade(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setSeamlessQualityUpgrade(enabled)
            }
        }
    }

    fun setLocalMusicEnabled(enabled: Boolean) = localMusic.setEnabled(enabled)

    fun setLocalMusicUseMediaStore(enabled: Boolean) = localMusic.setUseMediaStore(enabled)

    fun addLocalMusicFolder(uri: String) = localMusic.addFolder(uri)

    fun removeLocalMusicFolder(uri: String) = localMusic.removeFolder(uri)

    fun rescanLocalMusic() = localMusic.rescan()

    fun clearScanMessage() = localMusic.clearScanMessage()

    fun setBandcampEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setBandcampEnabled(enabled)
            }
        }
    }

    fun setYoutubeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setYoutubeEnabled(enabled)
            }
        }
    }

    fun setSoundcloudEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setSoundcloudEnabled(enabled)
            }
        }
    }

    fun setShowInlineVolumeSlider(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setShowInlineVolumeSlider(enabled)
            }
        }
    }

    fun setShowVolumeButton(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setShowVolumeButton(enabled)
            }
        }
    }

    fun setSearchHistoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setSearchHistoryEnabled(enabled)
            }
        }
    }

    fun setSearchHistorySource(source: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                when (source) {
                    "bandcamp" -> settingsDataStore.setSearchHistoryBandcamp(enabled)
                    "youtube" -> settingsDataStore.setSearchHistoryYoutube(enabled)
                    "soundcloud" -> settingsDataStore.setSearchHistorySoundcloud(enabled)
                    "local" -> settingsDataStore.setSearchHistoryLocal(enabled)
                }
            }
        }
    }

    fun clearAllSearchHistory() {
        viewModelScope.launch {
            runCatchingUiIgnore {
                recentSearchRepository.clearAllSources()
                _uiState.update {
                    it.copy(searchHistoryClearedMessage = UiText.StringResource(R.string.settings_search_history_cleared))
                }
            }
        }
    }

    fun clearSearchHistoryClearedMessage() {
        _uiState.update { it.copy(searchHistoryClearedMessage = null) }
    }

    // --- App update --------------------------------------------------------
    //
    // Thin delegates over [AppUpdateController] so the cold-start dialog and
    // the Settings row share one source of truth for state + an in-flight
    // download. See AppUpdateController's kdoc for the design.

    fun checkForAppUpdate() = appUpdateController.checkManually()
    fun confirmAppUpdate() = appUpdateController.confirmDownload()
    fun dismissAppUpdate() = appUpdateController.dismiss()

    fun clearUpdateMessage() {
        _uiState.update { it.copy(updateMessage = null) }
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setAutoUpdateCheckEnabled(enabled)
            }
        }
    }

    fun setYoutubeDefaultSource(source: String) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setYoutubeDefaultSource(source)
            }
        }
    }

    fun setPlayerDebugOverlay(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setPlayerDebugOverlay(enabled)
            }
        }
    }

    fun setKeepScreenOnInApp(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setKeepScreenOnInApp(enabled)
            }
        }
    }

    fun setKeepScreenOnWhilePlaying(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setKeepScreenOnWhilePlaying(enabled)
            }
        }
    }

    fun removeAllDownloads() {
        viewModelScope.launch {
            runCatchingUiIgnore {
                downloadRepository.clearAll()
            }
        }
    }

    fun setKeepLocalSort(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setKeepLocalSort(enabled)
            }
        }
    }

    fun setKeepLocalFilters(enabled: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setKeepLocalFilters(enabled)
            }
        }
    }
}
