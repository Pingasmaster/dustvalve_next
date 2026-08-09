package com.dustvalve.next.android.data.local.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

internal class DownloadStorageSettingsStoreImpl(
    private val prefs: SettingsPreferences,
) : DownloadStorageSettingsStore {
    private val keys = SettingsPreferences.Keys

    override val storageLimit: Flow<Long> = prefs.guardedPreferences.map {
        it[keys.STORAGE_LIMIT] ?: SettingsDataStore.DEFAULT_STORAGE_LIMIT
    }
    override val autoDownloadFutureContent: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.AUTO_DOWNLOAD_FUTURE_CONTENT] ?: false
    }
    override val downloadFormat: Flow<String> = prefs.guardedPreferences.map {
        it[keys.DOWNLOAD_FORMAT] ?: "flac"
    }
    override val saveDataOnMetered: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.SAVE_DATA_ON_METERED] ?: true
    }
    override val progressiveDownload: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.PROGRESSIVE_DOWNLOAD] ?: true
    }
    override val seamlessQualityUpgrade: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.SEAMLESS_QUALITY_UPGRADE] ?: false
    }
    override val downloadNotificationsEnabled: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.DOWNLOAD_NOTIFICATIONS_ENABLED] ?: true
    }
    override val autoDownloadFavorites: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.AUTO_DOWNLOAD_FAVORITES] ?: false
    }

    override suspend fun setStorageLimit(bytes: Long) {
        prefs.edit { it[keys.STORAGE_LIMIT] = bytes.coerceAtLeast(0L) }
    }

    override suspend fun getStorageLimitSync(): Long =
        prefs.guardedPreferences.firstOrNull()?.get(keys.STORAGE_LIMIT)
            ?: SettingsDataStore.DEFAULT_STORAGE_LIMIT

    override suspend fun setAutoDownloadFutureContent(enabled: Boolean) {
        prefs.edit { it[keys.AUTO_DOWNLOAD_FUTURE_CONTENT] = enabled }
    }

    override suspend fun getAutoDownloadFutureContentSync(): Boolean =
        prefs.guardedPreferences.firstOrNull()?.get(keys.AUTO_DOWNLOAD_FUTURE_CONTENT) ?: false

    override suspend fun setDownloadFormat(formatKey: String) {
        prefs.edit { it[keys.DOWNLOAD_FORMAT] = formatKey }
    }

    override suspend fun setSaveDataOnMetered(enabled: Boolean) {
        prefs.edit { it[keys.SAVE_DATA_ON_METERED] = enabled }
    }

    override suspend fun setProgressiveDownload(enabled: Boolean) {
        prefs.edit { it[keys.PROGRESSIVE_DOWNLOAD] = enabled }
    }

    override suspend fun setSeamlessQualityUpgrade(enabled: Boolean) {
        prefs.edit { it[keys.SEAMLESS_QUALITY_UPGRADE] = enabled }
    }

    override suspend fun setDownloadNotificationsEnabled(enabled: Boolean) {
        prefs.edit { it[keys.DOWNLOAD_NOTIFICATIONS_ENABLED] = enabled }
    }

    override suspend fun setAutoDownloadFavorites(enabled: Boolean) {
        prefs.edit { it[keys.AUTO_DOWNLOAD_FAVORITES] = enabled }
    }

    override suspend fun getDownloadFormatSync(): String =
        prefs.guardedPreferences.firstOrNull()?.get(keys.DOWNLOAD_FORMAT) ?: "flac"

    override suspend fun getProgressiveDownloadSync(): Boolean =
        prefs.guardedPreferences.firstOrNull()?.get(keys.PROGRESSIVE_DOWNLOAD) ?: true

    override suspend fun getSeamlessQualityUpgradeSync(): Boolean =
        prefs.guardedPreferences.firstOrNull()?.get(keys.SEAMLESS_QUALITY_UPGRADE) ?: false

    override suspend fun getSaveDataOnMeteredSync(): Boolean =
        prefs.guardedPreferences.firstOrNull()?.get(keys.SAVE_DATA_ON_METERED) ?: true
}
