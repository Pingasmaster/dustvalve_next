package com.dustvalve.next.android.ui.screens.settings

import com.dustvalve.next.android.cache.StorageTracker
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.repository.AccountRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Wires Settings DataStore / repository Flows into [SettingsUiState].
 * Extracted from [SettingsViewModel] so the ViewModel stays focused on
 * user actions rather than dozens of identical collect blocks.
 */
internal class SettingsUiCollectors(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<SettingsUiState>,
    private val accountRepository: AccountRepository,
    private val storageTracker: StorageTracker,
    private val settingsDataStore: SettingsDataStore,
) {
    fun start() {
        collect(accountRepository.getAccountState()) { copy(accountState = it) }
        collect(accountRepository.getYouTubeMusicAccountState()) { copy(ytmAccountState = it) }
        collect(storageTracker.getCacheInfo()) { copy(cacheInfo = it) }
        collect(settingsDataStore.themeMode) { copy(themeMode = it) }
        collect(settingsDataStore.dynamicColor) { copy(dynamicColor = it) }
        collect(settingsDataStore.autoDownloadCollection) { copy(autoDownloadCollection = it) }
        collect(settingsDataStore.autoDownloadFutureContent) { copy(autoDownloadFutureContent = it) }
        collect(settingsDataStore.downloadFormat) { copy(downloadFormat = it) }
        collect(settingsDataStore.saveDataOnMetered) { copy(saveDataOnMetered = it) }
        collect(settingsDataStore.progressiveDownload) { copy(progressiveDownload = it) }
        collect(settingsDataStore.seamlessQualityUpgrade) { copy(seamlessQualityUpgrade = it) }
        collect(settingsDataStore.downloadNotificationsEnabled) { copy(downloadNotificationsEnabled = it) }
        collect(settingsDataStore.oledBlack) { copy(oledBlack = it) }
        collect(settingsDataStore.albumArtTheme) { copy(albumArtTheme = it) }
        collect(settingsDataStore.progressBarStyle) { copy(progressBarStyle = it) }
        collect(settingsDataStore.progressBarSizeDp) { copy(progressBarSizeDp = it) }
        collect(settingsDataStore.autoDownloadFavorites) { copy(autoDownloadFavorites = it) }
        collect(settingsDataStore.localMusicEnabled) { copy(localMusicEnabled = it) }
        collect(settingsDataStore.localMusicFolderUris) { copy(localMusicFolderUris = it) }
        collect(settingsDataStore.localMusicUseMediaStore) { copy(localMusicUseMediaStore = it) }
        collect(settingsDataStore.bandcampEnabled) { copy(bandcampEnabled = it) }
        collect(settingsDataStore.youtubeEnabled) { copy(youtubeEnabled = it) }
        collect(settingsDataStore.showInlineVolumeSlider) { copy(showInlineVolumeSlider = it) }
        collect(settingsDataStore.showVolumeButton) { copy(showVolumeButton = it) }
        collect(settingsDataStore.searchHistoryEnabled) { copy(searchHistoryEnabled = it) }
        collect(settingsDataStore.searchHistoryBandcamp) { copy(searchHistoryBandcamp = it) }
        collect(settingsDataStore.searchHistoryYoutube) { copy(searchHistoryYoutube = it) }
        collect(settingsDataStore.searchHistoryLocal) { copy(searchHistoryLocal = it) }
        collect(settingsDataStore.youtubeDefaultSource) { copy(youtubeDefaultSource = it) }
        collect(settingsDataStore.autoUpdateCheckEnabled) { copy(autoUpdateCheckEnabled = it) }
        collect(settingsDataStore.albumCoverLongPressCarousel) { copy(albumCoverLongPressCarousel = it) }
        collect(settingsDataStore.keepScreenOnInApp) { copy(keepScreenOnInApp = it) }
        collect(settingsDataStore.keepScreenOnWhilePlaying) { copy(keepScreenOnWhilePlaying = it) }
        collect(settingsDataStore.keepLocalSort) { copy(keepLocalSort = it) }
        collect(settingsDataStore.keepLocalFilters) { copy(keepLocalFilters = it) }
        collect(settingsDataStore.dedicatedFolderEnabled) { copy(dedicatedFolderEnabled = it) }
        collect(settingsDataStore.dedicatedFolderTreeUri) { copy(dedicatedFolderTreeUri = it) }
        collect(settingsDataStore.dedicatedFolderIncludeImageCache) {
            copy(dedicatedFolderIncludeImageCache = it)
        }
        collect(settingsDataStore.dedicatedFolderIncludeMetadataCache) {
            copy(dedicatedFolderIncludeMetadataCache = it)
        }
        collect(settingsDataStore.storageLimit) { bytes ->
            copy(storageLimitIndex = bytesToSliderIndex(bytes))
        }
    }

    private fun <T> collect(flow: Flow<T>, map: SettingsUiState.(T) -> SettingsUiState) {
        scope.launch {
            flow
                .catch { /* ignore collection errors */ }
                .collect { value -> uiState.update { it.map(value) } }
        }
    }

    companion object {
        private val STORAGE_STEPS_BYTES = listOf(
            100L * 1024 * 1024, // 100 MB
            (0.5 * 1024 * 1024 * 1024).toLong(), // 500 MB
            1L * 1024 * 1024 * 1024, // 1 GB
            2L * 1024 * 1024 * 1024, // 2 GB
            5L * 1024 * 1024 * 1024, // 5 GB
            10L * 1024 * 1024 * 1024, // 10 GB
            Long.MAX_VALUE, // Unlimited
        )

        fun bytesToSliderIndex(bytes: Long): Int {
            if (bytes == Long.MAX_VALUE) return STORAGE_STEPS_BYTES.lastIndex
            var closestIndex = 0
            var closestDiff = Long.MAX_VALUE
            for (i in 0 until STORAGE_STEPS_BYTES.size - 1) {
                val diff = abs(bytes - STORAGE_STEPS_BYTES[i])
                if (diff < closestDiff) {
                    closestDiff = diff
                    closestIndex = i
                }
            }
            return closestIndex
        }
    }
}
