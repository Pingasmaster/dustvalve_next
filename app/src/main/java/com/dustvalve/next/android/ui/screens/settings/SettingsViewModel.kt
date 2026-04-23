package com.dustvalve.next.android.ui.screens.settings

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.model.AccountState
import com.dustvalve.next.android.domain.model.CacheInfo
import com.dustvalve.next.android.domain.model.ExportableTrack
import com.dustvalve.next.android.domain.model.YouTubeMusicAccountState
import com.dustvalve.next.android.cache.StorageTracker
import com.dustvalve.next.android.data.asset.AssetEvictionPolicy
import com.dustvalve.next.android.data.local.db.dao.RecentSearchDao
import com.dustvalve.next.android.update.AppUpdateService
import com.dustvalve.next.android.domain.repository.AccountRepository
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.LocalMusicRepository
import com.dustvalve.next.android.util.UiText
import com.dustvalve.next.android.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
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
    val spotifyConnected: Boolean = false,
    val downloadFormat: String = "flac",
    val saveDataOnMetered: Boolean = true,
    val progressiveDownload: Boolean = true,
    val seamlessQualityUpgrade: Boolean = true,
    val oledBlack: Boolean = false,
    val albumArtTheme: Boolean = false,
    val progressBarStyle: String = "wavy",
    val progressBarSizeDp: Int = 24,
    val autoDownloadFavorites: Boolean = false,
    val localMusicEnabled: Boolean = false,
    val localMusicFolderUris: List<String> = emptyList(),
    val localMusicUseMediaStore: Boolean = true,
    val isScanning: Boolean = false,
    val scanMessage: UiText? = null,
    val bandcampEnabled: Boolean = false,
    val youtubeEnabled: Boolean = false,
    val spotifyEnabled: Boolean = false,
    val showInlineVolumeSlider: Boolean = false,
    val showVolumeButton: Boolean = false,
    val searchHistoryEnabled: Boolean = true,
    val searchHistoryBandcamp: Boolean = true,
    val searchHistoryYoutube: Boolean = true,
    val searchHistorySpotify: Boolean = true,
    val searchHistoryLocal: Boolean = true,
    val searchHistoryClearedMessage: UiText? = null,
    val albumCoverLongPressCarousel: Boolean = true,
    val youtubeDefaultSource: String = "youtube",
    val keepScreenOnInApp: Boolean = false,
    val keepScreenOnWhilePlaying: Boolean = false,
    val keepLocalSort: Boolean = false,
    val keepLocalFilters: Boolean = false,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exportMessage: UiText? = null,
    val updateState: UpdateUiState = UpdateUiState.Idle,
    val updateMessage: UiText? = null,
)

sealed interface UpdateUiState {
    object Idle : UpdateUiState
    object Checking : UpdateUiState
    /** API said an update is available; awaiting user confirmation. */
    data class Available(val versionName: String, val apkUrl: String) : UpdateUiState
    /** Downloading the APK. [progress] is 0f..1f or `null` when the server didn't send Content-Length. */
    data class Downloading(val versionName: String, val progress: Float?) : UpdateUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val storageTracker: StorageTracker,
    private val assetEvictionPolicy: AssetEvictionPolicy,
    private val settingsDataStore: SettingsDataStore,
    private val localMusicRepository: LocalMusicRepository,
    private val downloadRepository: DownloadRepository,
    private val recentSearchDao: RecentSearchDao,
    private val appUpdateService: AppUpdateService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /**
     * All currently downloaded tracks, surfaced to the Export Tracks bottom sheet
     * with their format / quality metadata.
     */
    val exportableTracks: StateFlow<List<ExportableTrack>> = downloadRepository
        .getExportableTracks()
        .catch { /* ignore collection errors */ }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private var scanJob: Job? = null

    init {
        collectAccountState()
        collectYtmAccountState()
        collectSpotifyState()
        collectCacheInfo()
        collectThemeMode()
        collectDynamicColor()
        collectStorageLimit()
        collectAutoDownloadCollection()
        collectAutoDownloadFutureContent()
        collectDownloadFormat()
        collectSaveDataOnMetered()
        collectProgressiveDownload()
        collectSeamlessQualityUpgrade()
        collectOledBlack()
        collectAlbumArtTheme()
        collectProgressBar()
        collectLocalMusicEnabled()
        collectLocalMusicFolderUris()
        collectLocalMusicUseMediaStore()
        collectBandcampEnabled()
        collectYoutubeEnabled()
        collectSpotifyEnabled()
        collectShowInlineVolumeSlider()
        collectShowVolumeButton()
        collectSearchHistoryEnabled()
        collectYoutubeDefaultSource()
        collectAlbumCoverLongPressCarousel()
        collectKeepScreenOnInApp()
        collectKeepScreenOnWhilePlaying()
        collectKeepLocalSort()
        collectKeepLocalFilters()
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
            try { settingsDataStore.setProgressBarStyle(style) }
            catch (e: Exception) { if (e is CancellationException) throw e }
        }
    }

    fun setProgressBarSizeDp(sizeDp: Int) {
        viewModelScope.launch {
            try { settingsDataStore.setProgressBarSizeDp(sizeDp) }
            catch (e: Exception) { if (e is CancellationException) throw e }
        }
    }

    fun setAutoDownloadFavorites(enabled: Boolean) {
        viewModelScope.launch {
            try { settingsDataStore.setAutoDownloadFavorites(enabled) }
            catch (e: Exception) { if (e is CancellationException) throw e }
        }
    }

    fun setStorageLimit(gb: Float) {
        viewModelScope.launch {
            try {
                val bytes = when {
                    gb < 0f -> Long.MAX_VALUE // unlimited
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
                                    "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=.bandcamp.com"
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

    fun disconnectSpotify() {
        viewModelScope.launch {
            try {
                accountRepository.clearSpotifyAccount()
            } catch (_: Exception) {}
        }
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

    private fun collectAccountState() {
        viewModelScope.launch {
            accountRepository.getAccountState()
                .catch { /* ignore collection errors */ }
                .collect { state ->
                    _uiState.update { it.copy(accountState = state) }
                }
        }
    }

    private fun collectSpotifyState() {
        viewModelScope.launch {
            accountRepository.getSpotifyAccountState()
                .catch { /* ignore */ }
                .collect { state ->
                    _uiState.update { it.copy(spotifyConnected = state.isConnected) }
                }
        }
    }

    private fun collectYtmAccountState() {
        viewModelScope.launch {
            accountRepository.getYouTubeMusicAccountState()
                .catch { /* ignore collection errors */ }
                .collect { state ->
                    _uiState.update { it.copy(ytmAccountState = state) }
                }
        }
    }

    private fun collectCacheInfo() {
        viewModelScope.launch {
            storageTracker.getCacheInfo()
                .catch { /* ignore collection errors */ }
                .collect { info ->
                    _uiState.update { it.copy(cacheInfo = info) }
                }
        }
    }

    private fun collectThemeMode() {
        viewModelScope.launch {
            settingsDataStore.themeMode
                .catch { /* ignore collection errors */ }
                .collect { mode ->
                    _uiState.update { it.copy(themeMode = mode) }
                }
        }
    }

    private fun collectDynamicColor() {
        viewModelScope.launch {
            settingsDataStore.dynamicColor
                .catch { /* ignore collection errors */ }
                .collect { enabled ->
                    _uiState.update { it.copy(dynamicColor = enabled) }
                }
        }
    }

    private fun collectAutoDownloadCollection() {
        viewModelScope.launch {
            settingsDataStore.autoDownloadCollection
                .catch { /* ignore collection errors */ }
                .collect { enabled ->
                    _uiState.update { it.copy(autoDownloadCollection = enabled) }
                }
        }
    }

    private fun collectAutoDownloadFutureContent() {
        viewModelScope.launch {
            settingsDataStore.autoDownloadFutureContent
                .catch { /* ignore collection errors */ }
                .collect { enabled ->
                    _uiState.update { it.copy(autoDownloadFutureContent = enabled) }
                }
        }
    }

    private fun collectDownloadFormat() {
        viewModelScope.launch {
            settingsDataStore.downloadFormat
                .catch { /* ignore */ }
                .collect { formatKey ->
                    _uiState.update { it.copy(downloadFormat = formatKey) }
                }
        }
    }

    private fun collectSaveDataOnMetered() {
        viewModelScope.launch {
            settingsDataStore.saveDataOnMetered
                .catch { /* ignore */ }
                .collect { enabled ->
                    _uiState.update { it.copy(saveDataOnMetered = enabled) }
                }
        }
    }

    private fun collectProgressiveDownload() {
        viewModelScope.launch {
            settingsDataStore.progressiveDownload
                .catch { /* ignore */ }
                .collect { enabled ->
                    _uiState.update { it.copy(progressiveDownload = enabled) }
                }
        }
    }

    private fun collectSeamlessQualityUpgrade() {
        viewModelScope.launch {
            settingsDataStore.seamlessQualityUpgrade
                .catch { /* ignore */ }
                .collect { enabled ->
                    _uiState.update { it.copy(seamlessQualityUpgrade = enabled) }
                }
        }
    }

    private fun collectOledBlack() {
        viewModelScope.launch {
            settingsDataStore.oledBlack
                .catch { /* ignore collection errors */ }
                .collect { enabled ->
                    _uiState.update { it.copy(oledBlack = enabled) }
                }
        }
    }

    private fun collectAlbumArtTheme() {
        viewModelScope.launch {
            settingsDataStore.albumArtTheme
                .catch { /* ignore collection errors */ }
                .collect { enabled ->
                    _uiState.update { it.copy(albumArtTheme = enabled) }
                }
        }
    }

    private fun collectProgressBar() {
        viewModelScope.launch {
            settingsDataStore.progressBarStyle
                .catch { /* ignore */ }
                .collect { v -> _uiState.update { it.copy(progressBarStyle = v) } }
        }
        viewModelScope.launch {
            settingsDataStore.progressBarSizeDp
                .catch { /* ignore */ }
                .collect { v -> _uiState.update { it.copy(progressBarSizeDp = v) } }
        }
        viewModelScope.launch {
            settingsDataStore.autoDownloadFavorites
                .catch { /* ignore */ }
                .collect { v -> _uiState.update { it.copy(autoDownloadFavorites = v) } }
        }
    }

    fun setLocalMusicEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setLocalMusicEnabled(enabled)
                if (!enabled) {
                    localMusicRepository.cancelSyncWork()
                    localMusicRepository.clearAll()
                    settingsDataStore.setLocalMusicUseMediaStore(true)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setLocalMusicUseMediaStore(enabled: Boolean) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            try {
                // Clear all existing local tracks before switching modes
                localMusicRepository.clearAll()
                settingsDataStore.setLocalMusicUseMediaStore(enabled)
                if (enabled) {
                    _uiState.update { it.copy(isScanning = true) }
                    val result = localMusicRepository.scan()
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            scanMessage = UiText.PluralsResource(R.plurals.scan_found, result.total),
                        )
                    }
                    localMusicRepository.scheduleSyncWork()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanMessage = e.message?.let { UiText.StringResource(R.string.snackbar_scan_failed, listOf(it)) } ?: UiText.StringResource(R.string.snackbar_scan_failed, listOf("Unknown error")),
                    )
                }
            }
        }
    }

    fun addLocalMusicFolder(uri: String) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            try {
                localMusicRepository.addFolder(uri)
                _uiState.update { it.copy(isScanning = true) }
                val result = localMusicRepository.scan()
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanMessage = UiText.PluralsResource(R.plurals.scan_found, result.total),
                    )
                }
                localMusicRepository.scheduleSyncWork()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanMessage = e.message?.let { UiText.StringResource(R.string.snackbar_scan_failed, listOf(it)) } ?: UiText.StringResource(R.string.snackbar_scan_failed, listOf("Unknown error")),
                    )
                }
            }
        }
    }

    fun removeLocalMusicFolder(uri: String) {
        viewModelScope.launch {
            try {
                localMusicRepository.removeFolder(uri)
                val uris = _uiState.value.localMusicFolderUris - uri
                if (uris.isEmpty()) {
                    localMusicRepository.cancelSyncWork()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun rescanLocalMusic() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isScanning = true) }
                val result = localMusicRepository.scan()
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanMessage = UiText.PluralsResource(R.plurals.scan_found_detailed, result.total, listOf(result.total, result.added, result.removed)),
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanMessage = e.message?.let { UiText.StringResource(R.string.snackbar_scan_failed, listOf(it)) } ?: UiText.StringResource(R.string.snackbar_scan_failed, listOf("Unknown error")),
                    )
                }
            }
        }
    }

    fun clearScanMessage() {
        _uiState.update { it.copy(scanMessage = null) }
    }

    private fun collectLocalMusicEnabled() {
        viewModelScope.launch {
            settingsDataStore.localMusicEnabled
                .catch { /* ignore */ }
                .collect { enabled ->
                    _uiState.update { it.copy(localMusicEnabled = enabled) }
                }
        }
    }

    private fun collectLocalMusicFolderUris() {
        viewModelScope.launch {
            settingsDataStore.localMusicFolderUris
                .catch { /* ignore */ }
                .collect { uris ->
                    _uiState.update { it.copy(localMusicFolderUris = uris) }
                }
        }
    }

    private fun collectLocalMusicUseMediaStore() {
        viewModelScope.launch {
            settingsDataStore.localMusicUseMediaStore
                .catch { /* ignore */ }
                .collect { enabled ->
                    _uiState.update { it.copy(localMusicUseMediaStore = enabled) }
                }
        }
    }

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

    fun setSpotifyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setSpotifyEnabled(enabled)
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
                    "spotify" -> settingsDataStore.setSearchHistorySpotify(enabled)
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
                for (source in listOf("bandcamp", "youtube", "spotify", "local")) {
                    recentSearchDao.clearAll(source)
                }
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

    // ─── App update ────────────────────────────────────────────────────────

    fun checkForAppUpdate() {
        if (_uiState.value.updateState is UpdateUiState.Downloading) return
        viewModelScope.launch {
            _uiState.update { it.copy(updateState = UpdateUiState.Checking) }
            try {
                val available = appUpdateService.checkForUpdate()
                if (available == null) {
                    _uiState.update {
                        it.copy(
                            updateState = UpdateUiState.Idle,
                            updateMessage = UiText.StringResource(R.string.settings_update_no_update),
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            updateState = UpdateUiState.Available(
                                versionName = available.versionName,
                                apkUrl = available.apkDownloadUrl,
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        updateState = UpdateUiState.Idle,
                        updateMessage = UiText.StringResource(R.string.settings_update_check_failed),
                    )
                }
            }
        }
    }

    fun confirmAppUpdate() {
        val current = _uiState.value.updateState
        if (current !is UpdateUiState.Available) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(updateState = UpdateUiState.Downloading(current.versionName, 0f))
            }
            try {
                appUpdateService.downloadApk(current.apkUrl).collect { p ->
                    val frac = if (p.totalBytes > 0L) p.fraction else null
                    _uiState.update {
                        it.copy(updateState = UpdateUiState.Downloading(current.versionName, frac))
                    }
                }
                // Download finished — hand off to the system installer.
                try {
                    appUpdateService.launchInstaller()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _uiState.update {
                        it.copy(updateMessage = UiText.StringResource(R.string.settings_update_install_failed))
                    }
                }
                _uiState.update { it.copy(updateState = UpdateUiState.Idle) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        updateState = UpdateUiState.Idle,
                        updateMessage = UiText.StringResource(R.string.settings_update_download_failed),
                    )
                }
            }
        }
    }

    fun dismissAppUpdate() {
        _uiState.update { it.copy(updateState = UpdateUiState.Idle) }
    }

    fun clearUpdateMessage() {
        _uiState.update { it.copy(updateMessage = null) }
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

    fun setAlbumCoverLongPressCarousel(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setAlbumCoverLongPressCarousel(enabled)
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

    private var exportJob: Job? = null

    fun exportDownloads(destinationUri: String) {
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportProgress = 0f) }
            try {
                val count = downloadRepository.exportDownloads(destinationUri) { exported, total ->
                    _uiState.update { it.copy(exportProgress = exported.toFloat() / total.toFloat()) }
                }
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportMessage = UiText.PluralsResource(R.plurals.export_complete, count),
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportMessage = e.message?.let { UiText.StringResource(R.string.snackbar_export_failed, listOf(it)) } ?: UiText.StringResource(R.string.snackbar_export_failed, listOf("Unknown error")),
                    )
                }
            }
        }
    }

    fun exportSelectedDownloads(destinationUri: String, trackIds: Set<String>) {
        if (trackIds.isEmpty()) return
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportProgress = 0f) }
            try {
                val count = downloadRepository.exportDownloads(destinationUri, trackIds) { exported, total ->
                    _uiState.update {
                        it.copy(exportProgress = if (total == 0) 0f else exported.toFloat() / total.toFloat())
                    }
                }
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportMessage = UiText.PluralsResource(R.plurals.export_complete, count),
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportMessage = e.message?.let { UiText.StringResource(R.string.snackbar_export_failed, listOf(it)) }
                            ?: UiText.StringResource(R.string.snackbar_export_failed, listOf("Unknown error")),
                    )
                }
            }
        }
    }

    fun clearExportMessage() {
        _uiState.update { it.copy(exportMessage = null) }
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

    private fun collectBandcampEnabled() {
        viewModelScope.launch {
            settingsDataStore.bandcampEnabled
                .catch { /* ignore */ }
                .collect { enabled ->
                    _uiState.update { it.copy(bandcampEnabled = enabled) }
                }
        }
    }

    private fun collectYoutubeEnabled() {
        viewModelScope.launch {
            settingsDataStore.youtubeEnabled
                .catch { /* ignore */ }
                .collect { enabled ->
                    _uiState.update { it.copy(youtubeEnabled = enabled) }
                }
        }
    }

    private fun collectSpotifyEnabled() {
        viewModelScope.launch {
            settingsDataStore.spotifyEnabled
                .catch { /* ignore */ }
                .collect { enabled ->
                    _uiState.update { it.copy(spotifyEnabled = enabled) }
                }
        }
    }

    private fun collectShowInlineVolumeSlider() {
        viewModelScope.launch {
            settingsDataStore.showInlineVolumeSlider
                .catch { /* ignore */ }
                .collect { enabled ->
                    _uiState.update { it.copy(showInlineVolumeSlider = enabled) }
                }
        }
    }

    private fun collectShowVolumeButton() {
        viewModelScope.launch {
            settingsDataStore.showVolumeButton
                .catch { /* ignore */ }
                .collect { enabled ->
                    _uiState.update { it.copy(showVolumeButton = enabled) }
                }
        }
    }

    private fun collectSearchHistoryEnabled() {
        viewModelScope.launch {
            settingsDataStore.searchHistoryEnabled
                .catch { /* ignore */ }
                .collect { enabled ->
                    _uiState.update { it.copy(searchHistoryEnabled = enabled) }
                }
        }
        viewModelScope.launch {
            settingsDataStore.searchHistoryBandcamp
                .catch { /* ignore */ }
                .collect { v -> _uiState.update { it.copy(searchHistoryBandcamp = v) } }
        }
        viewModelScope.launch {
            settingsDataStore.searchHistoryYoutube
                .catch { /* ignore */ }
                .collect { v -> _uiState.update { it.copy(searchHistoryYoutube = v) } }
        }
        viewModelScope.launch {
            settingsDataStore.searchHistorySpotify
                .catch { /* ignore */ }
                .collect { v -> _uiState.update { it.copy(searchHistorySpotify = v) } }
        }
        viewModelScope.launch {
            settingsDataStore.searchHistoryLocal
                .catch { /* ignore */ }
                .collect { v -> _uiState.update { it.copy(searchHistoryLocal = v) } }
        }
    }

    private fun collectYoutubeDefaultSource() {
        viewModelScope.launch {
            settingsDataStore.youtubeDefaultSource
                .catch { /* ignore */ }
                .collect { source ->
                    _uiState.update { it.copy(youtubeDefaultSource = source) }
                }
        }
    }

    private fun collectAlbumCoverLongPressCarousel() {
        viewModelScope.launch {
            settingsDataStore.albumCoverLongPressCarousel
                .catch { /* ignore */ }
                .collect { enabled ->
                    _uiState.update { it.copy(albumCoverLongPressCarousel = enabled) }
                }
        }
    }

    private fun collectKeepScreenOnInApp() {
        viewModelScope.launch {
            settingsDataStore.keepScreenOnInApp
                .catch { /* ignore */ }
                .collect { enabled ->
                    _uiState.update { it.copy(keepScreenOnInApp = enabled) }
                }
        }
    }

    private fun collectKeepScreenOnWhilePlaying() {
        viewModelScope.launch {
            settingsDataStore.keepScreenOnWhilePlaying
                .catch { /* ignore */ }
                .collect { enabled ->
                    _uiState.update { it.copy(keepScreenOnWhilePlaying = enabled) }
                }
        }
    }

    private fun collectKeepLocalSort() {
        viewModelScope.launch {
            settingsDataStore.keepLocalSort
                .catch { /* ignore */ }
                .collect { enabled ->
                    _uiState.update { it.copy(keepLocalSort = enabled) }
                }
        }
    }

    private fun collectKeepLocalFilters() {
        viewModelScope.launch {
            settingsDataStore.keepLocalFilters
                .catch { /* ignore */ }
                .collect { enabled ->
                    _uiState.update { it.copy(keepLocalFilters = enabled) }
                }
        }
    }

    fun setKeepLocalSort(enabled: Boolean) {
        viewModelScope.launch {
            try { settingsDataStore.setKeepLocalSort(enabled) }
            catch (e: Exception) { if (e is CancellationException) throw e }
        }
    }

    fun setKeepLocalFilters(enabled: Boolean) {
        viewModelScope.launch {
            try { settingsDataStore.setKeepLocalFilters(enabled) }
            catch (e: Exception) { if (e is CancellationException) throw e }
        }
    }

    private fun collectStorageLimit() {
        viewModelScope.launch {
            settingsDataStore.storageLimit
                .catch { /* ignore collection errors */ }
                .collect { bytes ->
                    val index = bytesToSliderIndex(bytes)
                    _uiState.update { it.copy(storageLimitIndex = index) }
                }
        }
    }

    companion object {
        private val STORAGE_STEPS_BYTES = listOf(
            100L * 1024 * 1024,               // 100 MB
            (0.5 * 1024 * 1024 * 1024).toLong(), // 500 MB
            1L * 1024 * 1024 * 1024,           // 1 GB
            2L * 1024 * 1024 * 1024,           // 2 GB
            5L * 1024 * 1024 * 1024,           // 5 GB
            10L * 1024 * 1024 * 1024,          // 10 GB
            Long.MAX_VALUE,                     // Unlimited
        )

        private fun bytesToSliderIndex(bytes: Long): Int {
            if (bytes == Long.MAX_VALUE) return STORAGE_STEPS_BYTES.lastIndex
            // Find closest step
            var closestIndex = 0
            var closestDiff = Long.MAX_VALUE
            for (i in 0 until STORAGE_STEPS_BYTES.size - 1) {
                val diff = kotlin.math.abs(bytes - STORAGE_STEPS_BYTES[i])
                if (diff < closestDiff) {
                    closestDiff = diff
                    closestIndex = i
                }
            }
            return closestIndex
        }
    }
}
