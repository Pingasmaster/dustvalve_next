package com.dustvalve.next.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.model.AccountState
import com.dustvalve.next.android.domain.model.CacheInfo
import com.dustvalve.next.android.domain.repository.AccountRepository
import com.dustvalve.next.android.domain.repository.CacheRepository
import com.dustvalve.next.android.domain.usecase.ManageCacheUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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
    val signOutSuccess: Boolean = false,
    val downloadFormat: String = "flac",
    val saveDataOnMetered: Boolean = true,
    val progressiveDownload: Boolean = true,
    val oledBlack: Boolean = false,
    val wavyProgressBar: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val cacheRepository: CacheRepository,
    private val settingsDataStore: SettingsDataStore,
    private val manageCacheUseCase: ManageCacheUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        collectAccountState()
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
        collectWavyProgressBar()
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

    fun signOut() {
        viewModelScope.launch {
            try {
                accountRepository.clearAccount()
                // Clear WebView cookies so re-login starts fresh
                try {
                    android.webkit.CookieManager.getInstance().removeAllCookies { /* best-effort */ }
                } catch (_: Exception) {
                    // CookieManager may not be initialized if WebView was never used
                }
                _uiState.update { it.copy(signOutSuccess = true) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun clearSignOutSuccess() {
        _uiState.update { it.copy(signOutSuccess = false) }
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

    private fun collectWavyProgressBar() {
        viewModelScope.launch {
            settingsDataStore.wavyProgressBar
                .catch { /* ignore collection errors */ }
                .collect { enabled ->
                    _uiState.update { it.copy(wavyProgressBar = enabled) }
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
