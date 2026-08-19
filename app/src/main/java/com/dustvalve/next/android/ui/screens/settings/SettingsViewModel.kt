package com.dustvalve.next.android.ui.screens.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.cache.StorageTracker
import com.dustvalve.next.android.data.asset.AssetEvictionPolicy
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.model.CacheInfo
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.LocalMusicRepository
import com.dustvalve.next.android.domain.repository.RecentSearchRepository
import com.dustvalve.next.android.player.BluetoothStabilityMode
import com.dustvalve.next.android.player.PlaybackManager
import com.dustvalve.next.android.player.QueueManager
import com.dustvalve.next.android.update.AppUpdateController
import com.dustvalve.next.android.update.UpdateUiState
import com.dustvalve.next.android.util.UiText
import com.dustvalve.next.android.util.legacyAudioPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val backgroundAutoDownload: Boolean = true,
    val seamlessQualityUpgrade: Boolean = false,
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
    val keepScreenOnWhilePlaying: Boolean = true,
    val keepLocalSort: Boolean = false,
    val keepLocalFilters: Boolean = false,
    val updateState: UpdateUiState = UpdateUiState.Idle,
    val updateMessage: UiText? = null,
    val autoUpdateCheckEnabled: Boolean = true,
    val bluetoothStabilityMode: String = BluetoothStabilityMode.STORAGE_OFF,
    val bluetoothPcmBufferMs: Int = BluetoothStabilityMode.DEFAULT_PCM_BUFFER_MS,
    val bluetoothExoBufferBoost: Boolean = true,
    val bluetoothPauseDownloadsWhilePlaying: Boolean = true,
    val bluetoothDisableFloatOutput: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext appContext: Context,
    storageTracker: StorageTracker,
    assetEvictionPolicy: AssetEvictionPolicy,
    settingsDataStore: SettingsDataStore,
    localMusicRepository: LocalMusicRepository,
    downloadRepository: DownloadRepository,
    recentSearchRepository: RecentSearchRepository,
    playbackManager: PlaybackManager,
    queueManager: QueueManager,
    private val appUpdateController: AppUpdateController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    internal val localMusic = LocalMusicSettingsCoordinator(
        scope = viewModelScope,
        uiState = _uiState,
        settingsDataStore = settingsDataStore,
        localMusicRepository = localMusicRepository,
        hasAudioPermission = {
            ContextCompat.checkSelfPermission(appContext, legacyAudioPermission()) ==
                PackageManager.PERMISSION_GRANTED
        },
    )

    internal val appearance = SettingsAppearancePrefsCoordinator(
        scope = viewModelScope,
        settingsDataStore = settingsDataStore,
    )

    internal val storageSources = SettingsStorageSourcesPrefsCoordinator(
        scope = viewModelScope,
        uiState = _uiState,
        settingsDataStore = settingsDataStore,
        storageTracker = storageTracker,
        assetEvictionPolicy = assetEvictionPolicy,
        downloadRepository = downloadRepository,
        recentSearchRepository = recentSearchRepository,
        playbackManager = playbackManager,
        queueManager = queueManager,
    )

    internal val bluetoothStability = SettingsBluetoothStabilityPrefsCoordinator(
        scope = viewModelScope,
        settingsDataStore = settingsDataStore,
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
}
