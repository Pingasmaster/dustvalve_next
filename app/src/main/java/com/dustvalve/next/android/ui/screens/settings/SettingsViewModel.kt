package com.dustvalve.next.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.model.AccountState
import com.dustvalve.next.android.domain.model.CacheInfo
import com.dustvalve.next.android.domain.model.YouTubeMusicAccountState
import com.dustvalve.next.android.domain.repository.AccountRepository
import com.dustvalve.next.android.domain.repository.CacheRepository
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.LocalMusicRepository
import com.dustvalve.next.android.domain.usecase.ManageCacheUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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
    val downloadFormat: String = "flac",
    val saveDataOnMetered: Boolean = true,
    val progressiveDownload: Boolean = true,
    val oledBlack: Boolean = false,
    val albumArtTheme: Boolean = false,
    val wavyProgressBar: Boolean = true,
    val localMusicEnabled: Boolean = false,
    val localMusicFolderUris: List<String> = emptyList(),
    val localMusicUseMediaStore: Boolean = true,
    val isScanning: Boolean = false,
    val scanMessage: String? = null,
    val bandcampEnabled: Boolean = false,
    val youtubeEnabled: Boolean = false,
    val showInlineVolumeSlider: Boolean = false,
    val showVolumeButton: Boolean = false,
    val searchHistoryEnabled: Boolean = true,
    val albumCoverLongPressCarousel: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val cacheRepository: CacheRepository,
    private val settingsDataStore: SettingsDataStore,
    private val manageCacheUseCase: ManageCacheUseCase,
    private val localMusicRepository: LocalMusicRepository,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        collectAccountState()
        collectYtmAccountState()
        collectCacheInfo()
        collectThemeMode()
        collectDynamicColor()
        collectStorageLimit()
        collectAutoDownloadCollection()
        collectAutoDownloadFutureContent()
        collectDownloadFormat()
        collectSaveDataOnMetered()
        collectProgressiveDownload()
        collectOledBlack()
        collectAlbumArtTheme()
        collectWavyProgressBar()
        collectLocalMusicEnabled()
        collectLocalMusicFolderUris()
        collectLocalMusicUseMediaStore()
        collectBandcampEnabled()
        collectYoutubeEnabled()
        collectShowInlineVolumeSlider()
        collectShowVolumeButton()
        collectSearchHistoryEnabled()
        collectAlbumCoverLongPressCarousel()
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

    fun setWavyProgressBar(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setWavyProgressBar(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setStorageLimit(gb: Float) {
        viewModelScope.launch {
            try {
                val bytes = when {
                    gb < 0f -> Long.MAX_VALUE // unlimited
                    else -> (gb * 1024 * 1024 * 1024).toLong()
                }
                manageCacheUseCase.setStorageLimit(bytes)
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

    fun clearCache() {
        viewModelScope.launch {
            try {
                cacheRepository.clearCache()
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
                                val domain = android.net.Uri.parse(url).host?.let { ".$it" } ?: return@forEach
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
            cacheRepository.getCacheInfo()
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

    private fun collectWavyProgressBar() {
        viewModelScope.launch {
            settingsDataStore.wavyProgressBar
                .catch { /* ignore collection errors */ }
                .collect { enabled ->
                    _uiState.update { it.copy(wavyProgressBar = enabled) }
                }
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
                            scanMessage = "Found ${result.total} songs",
                        )
                    }
                    localMusicRepository.scheduleSyncWork()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanMessage = "Scan failed: ${e.message}",
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
                        scanMessage = "Found ${result.total} songs",
                    )
                }
                localMusicRepository.scheduleSyncWork()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanMessage = "Scan failed: ${e.message}",
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
                        scanMessage = "Found ${result.total} songs (${result.added} new, ${result.removed} removed)",
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanMessage = "Scan failed: ${e.message}",
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

    fun setAlbumCoverLongPressCarousel(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setAlbumCoverLongPressCarousel(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun removeAllDownloads() {
        viewModelScope.launch {
            try {
                val tracks = downloadRepository.getDownloadedTracks().first()
                for (track in tracks) {
                    downloadRepository.deleteDownload(track.id)
                }
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
