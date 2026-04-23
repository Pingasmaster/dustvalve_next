package com.dustvalve.next.android.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.dustvalve.next.android.util.CookieEncryption
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val STORAGE_LIMIT = longPreferencesKey("storage_limit")
        val ACCOUNT_USERNAME = stringPreferencesKey("account_username")
        val ACCOUNT_AVATAR = stringPreferencesKey("account_avatar")
        val AUTH_COOKIES = stringPreferencesKey("auth_cookies")
        val ACCOUNT_FAN_ID = longPreferencesKey("account_fan_id")
        val AUTO_DOWNLOAD_COLLECTION = booleanPreferencesKey("auto_download_collection")
        val AUTO_DOWNLOAD_FUTURE_CONTENT = booleanPreferencesKey("auto_download_future_content")
        val DOWNLOAD_FORMAT = stringPreferencesKey("download_format")
        val SAVE_DATA_ON_METERED = booleanPreferencesKey("save_data_on_metered")
        val PROGRESSIVE_DOWNLOAD = booleanPreferencesKey("progressive_download")
        val SEAMLESS_QUALITY_UPGRADE = booleanPreferencesKey("seamless_quality_upgrade")
        val OLED_BLACK = booleanPreferencesKey("oled_black")
        val ALBUM_ART_THEME = booleanPreferencesKey("album_art_theme")
        val WAVY_PROGRESS_BAR = booleanPreferencesKey("wavy_progress_bar")
        val LOCAL_MUSIC_ENABLED = booleanPreferencesKey("local_music_enabled")
        val LOCAL_MUSIC_FOLDER_URIS = stringPreferencesKey("local_music_folder_uris")
        val LOCAL_MUSIC_USE_MEDIASTORE = booleanPreferencesKey("local_music_use_mediastore")
        val BANDCAMP_ENABLED = booleanPreferencesKey("bandcamp_enabled")
        val YOUTUBE_ENABLED = booleanPreferencesKey("youtube_enabled")
        val SHOW_INLINE_VOLUME_SLIDER = booleanPreferencesKey("show_inline_volume_slider")
        val SHOW_VOLUME_BUTTON = booleanPreferencesKey("show_volume_button")
        val LAST_YOUTUBE_VIDEO_ID = stringPreferencesKey("last_youtube_video_id")
        val SEARCH_HISTORY_ENABLED = booleanPreferencesKey("search_history_enabled")
        val SEARCH_HISTORY_BANDCAMP = booleanPreferencesKey("search_history_bandcamp")
        val SEARCH_HISTORY_YOUTUBE = booleanPreferencesKey("search_history_youtube")
        val SEARCH_HISTORY_SPOTIFY = booleanPreferencesKey("search_history_spotify")
        val SEARCH_HISTORY_LOCAL = booleanPreferencesKey("search_history_local")
        val ALBUM_COVER_LONG_PRESS_CAROUSEL = booleanPreferencesKey("album_cover_long_press_carousel")
        val YTM_CONNECTED = booleanPreferencesKey("ytm_connected")
        val YOUTUBE_DEFAULT_SOURCE = stringPreferencesKey("youtube_default_source")
        val SPOTIFY_ENABLED = booleanPreferencesKey("spotify_enabled")
        val SPOTIFY_CONNECTED = booleanPreferencesKey("spotify_connected")
        val KEEP_SCREEN_ON_IN_APP = booleanPreferencesKey("keep_screen_on_in_app")
        val KEEP_SCREEN_ON_WHILE_PLAYING = booleanPreferencesKey("keep_screen_on_while_playing")
    }

    companion object {
        const val DEFAULT_STORAGE_LIMIT = 2L * 1024 * 1024 * 1024 // 2 GB
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE] ?: "system"
    }

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.DYNAMIC_COLOR] ?: true
    }

    val storageLimit: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.STORAGE_LIMIT] ?: DEFAULT_STORAGE_LIMIT
    }

    val accountUsername: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACCOUNT_USERNAME]
    }

    val accountAvatar: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACCOUNT_AVATAR]
    }

    val authCookies: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTH_COOKIES]?.let { encrypted ->
            try {
                CookieEncryption.decrypt(encrypted)
            } catch (_: Exception) {
                // Stored value may be unencrypted (pre-migration) — return as-is and
                // it will be re-encrypted on the next write
                encrypted
            }
        }
    }

    val accountFanId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACCOUNT_FAN_ID]
    }

    val autoDownloadCollection: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_DOWNLOAD_COLLECTION] ?: true
    }

    val autoDownloadFutureContent: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_DOWNLOAD_FUTURE_CONTENT] ?: false
    }

    val downloadFormat: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.DOWNLOAD_FORMAT] ?: "flac"
    }

    val saveDataOnMetered: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SAVE_DATA_ON_METERED] ?: true
    }

    val progressiveDownload: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.PROGRESSIVE_DOWNLOAD] ?: true
    }

    val seamlessQualityUpgrade: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SEAMLESS_QUALITY_UPGRADE] ?: true
    }

    val oledBlack: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.OLED_BLACK] ?: false
    }

    val albumArtTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ALBUM_ART_THEME] ?: false
    }

    val wavyProgressBar: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.WAVY_PROGRESS_BAR] ?: true
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setStorageLimit(bytes: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STORAGE_LIMIT] = bytes.coerceAtLeast(0L)
        }
    }

    suspend fun setAccountUsername(username: String?) {
        context.dataStore.edit { prefs ->
            if (username != null) {
                prefs[Keys.ACCOUNT_USERNAME] = username
            } else {
                prefs.remove(Keys.ACCOUNT_USERNAME)
            }
        }
    }

    suspend fun setAccountAvatar(avatarUrl: String?) {
        context.dataStore.edit { prefs ->
            if (avatarUrl != null) {
                prefs[Keys.ACCOUNT_AVATAR] = avatarUrl
            } else {
                prefs.remove(Keys.ACCOUNT_AVATAR)
            }
        }
    }

    suspend fun setAccountFanId(fanId: Long?) {
        context.dataStore.edit { prefs ->
            if (fanId != null) {
                prefs[Keys.ACCOUNT_FAN_ID] = fanId
            } else {
                prefs.remove(Keys.ACCOUNT_FAN_ID)
            }
        }
    }

    /**
     * Atomically sets all account info fields in a single DataStore edit.
     * Passing null for a field clears it, preventing stale data from a previous login.
     */
    suspend fun setAccountInfo(username: String?, avatarUrl: String?, fanId: Long?) {
        context.dataStore.edit { prefs ->
            if (username != null) prefs[Keys.ACCOUNT_USERNAME] = username
            else prefs.remove(Keys.ACCOUNT_USERNAME)
            if (avatarUrl != null) prefs[Keys.ACCOUNT_AVATAR] = avatarUrl
            else prefs.remove(Keys.ACCOUNT_AVATAR)
            if (fanId != null) prefs[Keys.ACCOUNT_FAN_ID] = fanId
            else prefs.remove(Keys.ACCOUNT_FAN_ID)
        }
    }

    suspend fun setAuthCookies(cookiesJson: String?) {
        context.dataStore.edit { prefs ->
            if (cookiesJson != null) {
                prefs[Keys.AUTH_COOKIES] = CookieEncryption.encrypt(cookiesJson)
            } else {
                prefs.remove(Keys.AUTH_COOKIES)
            }
        }
    }

    suspend fun getStorageLimitSync(): Long {
        return context.dataStore.data.firstOrNull()?.get(Keys.STORAGE_LIMIT)
            ?: DEFAULT_STORAGE_LIMIT
    }

    suspend fun setAutoDownloadCollection(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_DOWNLOAD_COLLECTION] = enabled
        }
    }

    suspend fun setAutoDownloadFutureContent(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_DOWNLOAD_FUTURE_CONTENT] = enabled
        }
    }

    suspend fun getAutoDownloadFutureContentSync(): Boolean {
        return context.dataStore.data.firstOrNull()?.get(Keys.AUTO_DOWNLOAD_FUTURE_CONTENT) ?: false
    }

    suspend fun setDownloadFormat(formatKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DOWNLOAD_FORMAT] = formatKey
        }
    }

    suspend fun setSaveDataOnMetered(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SAVE_DATA_ON_METERED] = enabled
        }
    }

    suspend fun setProgressiveDownload(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROGRESSIVE_DOWNLOAD] = enabled
        }
    }

    suspend fun setSeamlessQualityUpgrade(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SEAMLESS_QUALITY_UPGRADE] = enabled
        }
    }

    suspend fun setOledBlack(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.OLED_BLACK] = enabled
        }
    }

    suspend fun setAlbumArtTheme(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ALBUM_ART_THEME] = enabled
        }
    }

    suspend fun setWavyProgressBar(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WAVY_PROGRESS_BAR] = enabled
        }
    }

    suspend fun getDownloadFormatSync(): String {
        return context.dataStore.data.firstOrNull()?.get(Keys.DOWNLOAD_FORMAT) ?: "flac"
    }

    suspend fun getProgressiveDownloadSync(): Boolean {
        return context.dataStore.data.firstOrNull()?.get(Keys.PROGRESSIVE_DOWNLOAD) ?: true
    }

    suspend fun getSeamlessQualityUpgradeSync(): Boolean {
        return context.dataStore.data.firstOrNull()?.get(Keys.SEAMLESS_QUALITY_UPGRADE) ?: true
    }

    suspend fun getSaveDataOnMeteredSync(): Boolean {
        return context.dataStore.data.firstOrNull()?.get(Keys.SAVE_DATA_ON_METERED) ?: true
    }

    suspend fun clearAccount() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.ACCOUNT_USERNAME)
            prefs.remove(Keys.ACCOUNT_AVATAR)
            prefs.remove(Keys.AUTH_COOKIES)
            prefs.remove(Keys.ACCOUNT_FAN_ID)
        }
    }

    val localMusicEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOCAL_MUSIC_ENABLED] ?: false
    }

    val localMusicFolderUris: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val json = prefs[Keys.LOCAL_MUSIC_FOLDER_URIS]
        if (json != null) Json.decodeFromString<List<String>>(json) else emptyList()
    }

    val localMusicUseMediaStore: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOCAL_MUSIC_USE_MEDIASTORE] ?: true
    }

    suspend fun setLocalMusicEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LOCAL_MUSIC_ENABLED] = enabled
        }
    }

    suspend fun setLocalMusicFolderUris(uris: List<String>) {
        context.dataStore.edit { prefs ->
            if (uris.isNotEmpty()) {
                prefs[Keys.LOCAL_MUSIC_FOLDER_URIS] = Json.encodeToString(uris)
            } else {
                prefs.remove(Keys.LOCAL_MUSIC_FOLDER_URIS)
            }
        }
    }

    suspend fun addLocalMusicFolderUri(uri: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.LOCAL_MUSIC_FOLDER_URIS]
                ?.let { Json.decodeFromString<List<String>>(it) }
                ?: emptyList()
            if (uri !in current) {
                prefs[Keys.LOCAL_MUSIC_FOLDER_URIS] = Json.encodeToString(current + uri)
            }
        }
    }

    suspend fun removeLocalMusicFolderUri(uri: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.LOCAL_MUSIC_FOLDER_URIS]
                ?.let { Json.decodeFromString<List<String>>(it) }
                ?: emptyList()
            val updated = current - uri
            if (updated.isNotEmpty()) {
                prefs[Keys.LOCAL_MUSIC_FOLDER_URIS] = Json.encodeToString(updated)
            } else {
                prefs.remove(Keys.LOCAL_MUSIC_FOLDER_URIS)
            }
        }
    }

    suspend fun setLocalMusicUseMediaStore(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LOCAL_MUSIC_USE_MEDIASTORE] = enabled
        }
    }

    suspend fun getLocalMusicEnabledSync(): Boolean {
        return context.dataStore.data.firstOrNull()?.get(Keys.LOCAL_MUSIC_ENABLED) ?: false
    }

    suspend fun getLocalMusicFolderUrisSync(): List<String> {
        val json = context.dataStore.data.firstOrNull()?.get(Keys.LOCAL_MUSIC_FOLDER_URIS)
        return if (json != null) Json.decodeFromString<List<String>>(json) else emptyList()
    }

    suspend fun getLocalMusicUseMediaStoreSync(): Boolean {
        return context.dataStore.data.firstOrNull()?.get(Keys.LOCAL_MUSIC_USE_MEDIASTORE) ?: true
    }

    val bandcampEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.BANDCAMP_ENABLED] ?: false
    }

    val youtubeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.YOUTUBE_ENABLED] ?: false
    }

    val spotifyEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SPOTIFY_ENABLED] ?: false
    }

    val showInlineVolumeSlider: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHOW_INLINE_VOLUME_SLIDER] ?: false
    }

    val showVolumeButton: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHOW_VOLUME_BUTTON] ?: false
    }

    val lastYoutubeVideoId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_YOUTUBE_VIDEO_ID]
    }

    suspend fun setBandcampEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BANDCAMP_ENABLED] = enabled
        }
    }

    suspend fun setYoutubeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.YOUTUBE_ENABLED] = enabled
        }
    }

    suspend fun setShowInlineVolumeSlider(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_INLINE_VOLUME_SLIDER] = enabled
        }
    }

    suspend fun setShowVolumeButton(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_VOLUME_BUTTON] = enabled
        }
    }

    val keepScreenOnInApp: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.KEEP_SCREEN_ON_IN_APP] ?: false
    }

    val keepScreenOnWhilePlaying: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.KEEP_SCREEN_ON_WHILE_PLAYING] ?: false
    }

    val searchHistoryEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SEARCH_HISTORY_ENABLED] ?: true
    }

    val searchHistoryBandcamp: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SEARCH_HISTORY_BANDCAMP] ?: true
    }

    val searchHistoryYoutube: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SEARCH_HISTORY_YOUTUBE] ?: true
    }

    val searchHistorySpotify: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SEARCH_HISTORY_SPOTIFY] ?: true
    }

    val searchHistoryLocal: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SEARCH_HISTORY_LOCAL] ?: true
    }

    suspend fun setKeepScreenOnInApp(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.KEEP_SCREEN_ON_IN_APP] = enabled
        }
    }

    suspend fun setKeepScreenOnWhilePlaying(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.KEEP_SCREEN_ON_WHILE_PLAYING] = enabled
        }
    }

    suspend fun setSearchHistoryEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SEARCH_HISTORY_ENABLED] = enabled
        }
    }

    suspend fun setSearchHistoryBandcamp(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SEARCH_HISTORY_BANDCAMP] = enabled }
    }

    suspend fun setSearchHistoryYoutube(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SEARCH_HISTORY_YOUTUBE] = enabled }
    }

    suspend fun setSearchHistorySpotify(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SEARCH_HISTORY_SPOTIFY] = enabled }
    }

    suspend fun setSearchHistoryLocal(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SEARCH_HISTORY_LOCAL] = enabled }
    }

    val albumCoverLongPressCarousel: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ALBUM_COVER_LONG_PRESS_CAROUSEL] ?: true
    }

    suspend fun setAlbumCoverLongPressCarousel(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ALBUM_COVER_LONG_PRESS_CAROUSEL] = enabled
        }
    }

    val ytmConnected: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.YTM_CONNECTED] ?: false
    }

    suspend fun setYtmConnected(connected: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.YTM_CONNECTED] = connected
        }
    }

    val youtubeDefaultSource: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.YOUTUBE_DEFAULT_SOURCE] ?: "youtube"
    }

    suspend fun setYoutubeDefaultSource(source: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.YOUTUBE_DEFAULT_SOURCE] = source
        }
    }

    suspend fun clearYtmAccount() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.YTM_CONNECTED)
        }
    }

    val spotifyConnected: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SPOTIFY_CONNECTED] ?: false
    }

    suspend fun setSpotifyEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SPOTIFY_ENABLED] = enabled
        }
    }

    suspend fun setSpotifyConnected(connected: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SPOTIFY_CONNECTED] = connected
        }
    }

    suspend fun clearSpotifyAccount() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.SPOTIFY_CONNECTED)
        }
    }

    suspend fun setLastYoutubeVideoId(videoId: String?) {
        context.dataStore.edit { prefs ->
            if (videoId != null) {
                prefs[Keys.LAST_YOUTUBE_VIDEO_ID] = videoId
            } else {
                prefs.remove(Keys.LAST_YOUTUBE_VIDEO_ID)
            }
        }
    }
}
