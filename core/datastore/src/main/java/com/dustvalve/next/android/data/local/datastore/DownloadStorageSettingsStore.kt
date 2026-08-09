package com.dustvalve.next.android.data.local.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

/** Storage limit + download-format / progressive / auto-download prefs. */
interface DownloadStorageSettingsStore {
    val storageLimit: Flow<Long>
    val autoDownloadFutureContent: Flow<Boolean>
    val downloadFormat: Flow<String>
    val saveDataOnMetered: Flow<Boolean>
    val progressiveDownload: Flow<Boolean>
    val seamlessQualityUpgrade: Flow<Boolean>
    val downloadNotificationsEnabled: Flow<Boolean>
    /**
     * Sub-toggle of [autoDownloadFutureContent]: when on, every favorited
     * track is downloaded in the background and the Favorites playlist
     * hides its manual Download button (no point - it'd be redundant).
     */
    val autoDownloadFavorites: Flow<Boolean>

    suspend fun setStorageLimit(bytes: Long)
    suspend fun getStorageLimitSync(): Long
    suspend fun setAutoDownloadFutureContent(enabled: Boolean)
    suspend fun getAutoDownloadFutureContentSync(): Boolean
    suspend fun setDownloadFormat(formatKey: String)
    suspend fun setSaveDataOnMetered(enabled: Boolean)
    suspend fun setProgressiveDownload(enabled: Boolean)
    suspend fun setSeamlessQualityUpgrade(enabled: Boolean)
    suspend fun setDownloadNotificationsEnabled(enabled: Boolean)
    suspend fun setAutoDownloadFavorites(enabled: Boolean)
    suspend fun getDownloadFormatSync(): String
    suspend fun getProgressiveDownloadSync(): Boolean
    suspend fun getSeamlessQualityUpgradeSync(): Boolean
    suspend fun getSaveDataOnMeteredSync(): Boolean
}
