package com.dustvalve.next.android.ui.screens.settings

import com.dustvalve.next.android.R
import com.dustvalve.next.android.cache.StorageTracker
import com.dustvalve.next.android.data.asset.AssetEvictionPolicy
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.RecentSearchRepository
import com.dustvalve.next.android.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Storage / download / sources / search-history preference writes extracted
 * from SettingsViewModel.
 */
internal class SettingsStorageSourcesPrefsCoordinator(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<SettingsUiState>,
    private val settingsDataStore: SettingsDataStore,
    private val storageTracker: StorageTracker,
    private val assetEvictionPolicy: AssetEvictionPolicy,
    private val downloadRepository: DownloadRepository,
    private val recentSearchRepository: RecentSearchRepository,
) {
    fun setAutoDownloadFavorites(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setAutoDownloadFavorites(enabled)
    }

    fun setDownloadNotificationsEnabled(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setDownloadNotificationsEnabled(enabled)
    }

    fun setStorageLimit(gb: Float) {
        scope.launch {
            runSettingsPrefWrite {
                val bytes = when {
                    gb < 0f -> Long.MAX_VALUE
                    else -> (gb * 1024 * 1024 * 1024).toLong()
                }
                settingsDataStore.setStorageLimit(bytes)
                val overage = storageTracker.getOverageBytes()
                if (overage > 0L) {
                    assetEvictionPolicy.evict(overage)
                    storageTracker.notifyChanged()
                }
            }
        }
    }

    fun setAutoDownloadFutureContent(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setAutoDownloadFutureContent(enabled)
    }

    fun setDownloadFormat(formatKey: String) = scope.launchSettingsPref {
        settingsDataStore.setDownloadFormat(formatKey)
    }

    fun setSaveDataOnMetered(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setSaveDataOnMetered(enabled)
    }

    fun setProgressiveDownload(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setProgressiveDownload(enabled)
    }

    fun setSeamlessQualityUpgrade(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setSeamlessQualityUpgrade(enabled)
    }

    fun setBandcampEnabled(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setBandcampEnabled(enabled)
    }

    fun setYoutubeEnabled(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setYoutubeEnabled(enabled)
    }

    fun setSoundcloudEnabled(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setSoundcloudEnabled(enabled)
    }

    fun setSearchHistoryEnabled(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setSearchHistoryEnabled(enabled)
    }

    fun setSearchHistorySource(source: String, enabled: Boolean) {
        scope.launch {
            runSettingsPrefWrite {
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
        scope.launch {
            runSettingsPrefWrite {
                recentSearchRepository.clearAllSources()
                uiState.update {
                    it.copy(searchHistoryClearedMessage = UiText.StringResource(R.string.settings_search_history_cleared))
                }
            }
        }
    }

    fun clearSearchHistoryClearedMessage() {
        uiState.update { it.copy(searchHistoryClearedMessage = null) }
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setAutoUpdateCheckEnabled(enabled)
    }

    fun setYoutubeDefaultSource(source: String) = scope.launchSettingsPref {
        settingsDataStore.setYoutubeDefaultSource(source)
    }

    fun removeAllDownloads() {
        scope.launch {
            runSettingsPrefWrite {
                downloadRepository.clearAll()
            }
        }
    }

    fun setKeepLocalSort(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setKeepLocalSort(enabled)
    }

    fun setKeepLocalFilters(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setKeepLocalFilters(enabled)
    }
}
