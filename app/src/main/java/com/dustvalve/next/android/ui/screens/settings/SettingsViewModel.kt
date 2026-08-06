package com.dustvalve.next.android.ui.screens.settings

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.R
import com.dustvalve.next.android.cache.StorageTracker
import com.dustvalve.next.android.data.asset.AssetEvictionPolicy
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.model.AccountState
import com.dustvalve.next.android.domain.model.CacheInfo
import com.dustvalve.next.android.domain.model.YouTubeMusicAccountState
import com.dustvalve.next.android.domain.repository.AccountRepository
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.LocalMusicRepository
import com.dustvalve.next.android.domain.repository.RecentSearchRepository
import com.dustvalve.next.android.update.AppUpdateController
import com.dustvalve.next.android.update.UpdateUiState
import com.dustvalve.next.android.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class SettingsUiState(
    val accountState: AccountState = AccountState(),
    val cacheInfo: CacheInfo? = null,
    val themeMode: String = "system",
    val dynamicColor: Boolean = true,
    val storageLimitIndex: Int = 3, // default 2 GB
    val autoDownloadCollection: Boolean = true,
    val autoDownloadFutureContent: Boolean = false,
    val bandcampSignOutSuccess: Boolean = false,
    val ytmAccountState: YouTubeMusicAccountState = YouTubeMusicAccountState(),
    val ytmSignOutSuccess: Boolean = false,
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
    private val accountRepository: AccountRepository,
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
            accountRepository = accountRepository,
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
            try {
                settingsDataStore.setThemeMode(mode)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setDynamicColor(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setOledBlack(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setOledBlack(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setAlbumArtTheme(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setAlbumArtTheme(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setProgressBarStyle(style: String) {
        viewModelScope.launch {
            try {
                settingsDataStore.setProgressBarStyle(style)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setProgressBarSizeDp(sizeDp: Int) {
        viewModelScope.launch {
            try {
                settingsDataStore.setProgressBarSizeDp(sizeDp)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setAutoDownloadFavorites(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setAutoDownloadFavorites(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setDownloadNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setDownloadNotificationsEnabled(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setStorageLimit(gb: Float) {
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setAutoDownloadCollection(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setAutoDownloadCollection(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setAutoDownloadFutureContent(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setAutoDownloadFutureContent(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setDownloadFormat(formatKey: String) {
        viewModelScope.launch {
            try {
                settingsDataStore.setDownloadFormat(formatKey)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setSaveDataOnMetered(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setSaveDataOnMetered(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setProgressiveDownload(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setProgressiveDownload(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setSeamlessQualityUpgrade(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setSeamlessQualityUpgrade(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun signOutBandcamp() {
        viewModelScope.launch {
            try {
                accountRepository.clearAccount()
                // Clear only Bandcamp WebView cookies so re-login starts fresh
                try {
                    val cm = android.webkit.CookieManager.getInstance()
                    cm.getCookie("https://bandcamp.com")
                        ?.split(";")
                        ?.forEach { cookie ->
                            val name = cookie.trim().split("=", limit = 2).firstOrNull()?.trim()
                            if (name != null) {
                                cm.setCookie(
                                    "https://bandcamp.com",
                                    "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=.bandcamp.com",
                                )
                            }
                        }
                    cm.flush()
                } catch (_: Exception) {
                    // CookieManager may not be initialized if WebView was never used
                }
                _uiState.update { it.copy(bandcampSignOutSuccess = true) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun clearSignOutSuccess() {
        _uiState.update { it.copy(bandcampSignOutSuccess = false) }
    }

    fun signOutYouTubeMusic() {
        viewModelScope.launch {
            try {
                accountRepository.clearYouTubeMusicAccount()
                // Clear YouTube/Google WebView cookies (domain-specific)
                try {
                    val cm = android.webkit.CookieManager.getInstance()
                    listOf("https://youtube.com", "https://music.youtube.com", "https://google.com").forEach { url ->
                        cm.getCookie(url)?.split(";")?.forEach { cookie ->
                            val name = cookie.trim().split("=", limit = 2).firstOrNull()?.trim()
                            if (name != null) {
                                val domain = url.toUri().host?.let { ".$it" } ?: return@forEach
                                cm.setCookie(url, "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=$domain")
                            }
                        }
                    }
                    cm.flush()
                } catch (_: Exception) {
                    // CookieManager may not be initialized if WebView was never used
                }
                _uiState.update { it.copy(ytmSignOutSuccess = true) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun clearYtmSignOutSuccess() {
        _uiState.update { it.copy(ytmSignOutSuccess = false) }
    }

    fun setLocalMusicEnabled(enabled: Boolean) = localMusic.setEnabled(enabled)

    fun setLocalMusicUseMediaStore(enabled: Boolean) = localMusic.setUseMediaStore(enabled)

    fun addLocalMusicFolder(uri: String) = localMusic.addFolder(uri)

    fun removeLocalMusicFolder(uri: String) = localMusic.removeFolder(uri)

    fun rescanLocalMusic() = localMusic.rescan()

    fun clearScanMessage() = localMusic.clearScanMessage()

    fun setBandcampEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setBandcampEnabled(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setYoutubeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setYoutubeEnabled(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setSoundcloudEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setSoundcloudEnabled(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setShowInlineVolumeSlider(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setShowInlineVolumeSlider(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setShowVolumeButton(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setShowVolumeButton(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setSearchHistoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setSearchHistoryEnabled(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setSearchHistorySource(source: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                when (source) {
                    "bandcamp" -> settingsDataStore.setSearchHistoryBandcamp(enabled)
                    "youtube" -> settingsDataStore.setSearchHistoryYoutube(enabled)
                    "soundcloud" -> settingsDataStore.setSearchHistorySoundcloud(enabled)
                    "local" -> settingsDataStore.setSearchHistoryLocal(enabled)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun clearAllSearchHistory() {
        viewModelScope.launch {
            try {
                recentSearchRepository.clearAllSources()
                _uiState.update {
                    it.copy(searchHistoryClearedMessage = UiText.StringResource(R.string.settings_search_history_cleared))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
            try {
                settingsDataStore.setAutoUpdateCheckEnabled(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setYoutubeDefaultSource(source: String) {
        viewModelScope.launch {
            try {
                settingsDataStore.setYoutubeDefaultSource(source)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setPlayerDebugOverlay(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setPlayerDebugOverlay(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setKeepScreenOnInApp(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setKeepScreenOnInApp(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setKeepScreenOnWhilePlaying(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setKeepScreenOnWhilePlaying(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun removeAllDownloads() {
        viewModelScope.launch {
            try {
                downloadRepository.clearAll()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setKeepLocalSort(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setKeepLocalSort(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setKeepLocalFilters(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setKeepLocalFilters(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }
}
