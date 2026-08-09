package com.dustvalve.next.android.data.local.datastore

import com.dustvalve.next.android.util.CookieEncryption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class SourcesSearchPlayerPrefsStoreImpl(
    private val prefs: SettingsPreferences,
) : SourcesSearchPlayerPrefsStore {
    private val keys = SettingsPreferences.Keys

    override val authCookies: Flow<String?> = prefs.guardedPreferences.map { p ->
        p[keys.AUTH_COOKIES]?.let { encrypted ->
            try {
                CookieEncryption.decrypt(encrypted)
            } catch (_: Exception) {
                // Undecryptable (corrupt) value: treat as absent
                null
            }
        }
    }
    override val bandcampEnabled: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.BANDCAMP_ENABLED] ?: false
    }
    override val youtubeEnabled: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.YOUTUBE_ENABLED] ?: false
    }
    override val soundcloudEnabled: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.SOUNDCLOUD_ENABLED] ?: false
    }
    override val soundcloudClientId: Flow<String?> = prefs.guardedPreferences.map {
        it[keys.SOUNDCLOUD_CLIENT_ID]
    }
    override val showInlineVolumeSlider: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.SHOW_INLINE_VOLUME_SLIDER] ?: false
    }
    override val showVolumeButton: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.SHOW_VOLUME_BUTTON] ?: false
    }
    override val lastYoutubeVideoId: Flow<String?> = prefs.guardedPreferences.map {
        it[keys.LAST_YOUTUBE_VIDEO_ID]
    }
    override val keepScreenOnInApp: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.KEEP_SCREEN_ON_IN_APP] ?: false
    }
    override val keepScreenOnWhilePlaying: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.KEEP_SCREEN_ON_WHILE_PLAYING] ?: true
    }
    override val searchHistoryEnabled: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.SEARCH_HISTORY_ENABLED] ?: true
    }
    override val searchHistoryBandcamp: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.SEARCH_HISTORY_BANDCAMP] ?: true
    }
    override val searchHistoryYoutube: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.SEARCH_HISTORY_YOUTUBE] ?: true
    }
    override val searchHistorySoundcloud: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.SEARCH_HISTORY_SOUNDCLOUD] ?: true
    }
    override val searchHistoryLocal: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.SEARCH_HISTORY_LOCAL] ?: true
    }
    override val playerDebugOverlay: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.ALBUM_COVER_LONG_PRESS_CAROUSEL] ?: false
    }
    override val youtubeDefaultSource: Flow<String> = prefs.guardedPreferences.map {
        it[keys.YOUTUBE_DEFAULT_SOURCE] ?: "youtube"
    }
    override val autoUpdateCheckEnabled: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.AUTO_UPDATE_CHECK_ENABLED] ?: true
    }
    override val bandcampCustomGenres: Flow<List<String>> = prefs.guardedPreferences.map {
        StringListPreference.decode(it[keys.BANDCAMP_CUSTOM_GENRES])
    }

    override suspend fun setAuthCookies(cookiesJson: String?) {
        prefs.edit { p ->
            if (cookiesJson != null) {
                p[keys.AUTH_COOKIES] = CookieEncryption.encrypt(cookiesJson)
            } else {
                p.remove(keys.AUTH_COOKIES)
            }
        }
    }

    override suspend fun setBandcampEnabled(enabled: Boolean) {
        prefs.edit { it[keys.BANDCAMP_ENABLED] = enabled }
    }

    override suspend fun setYoutubeEnabled(enabled: Boolean) {
        prefs.edit { it[keys.YOUTUBE_ENABLED] = enabled }
    }

    override suspend fun setSoundcloudEnabled(enabled: Boolean) {
        prefs.edit { it[keys.SOUNDCLOUD_ENABLED] = enabled }
    }

    override suspend fun setSoundcloudClientId(clientId: String) {
        prefs.edit { it[keys.SOUNDCLOUD_CLIENT_ID] = clientId }
    }

    override suspend fun clearSoundcloudClientId() {
        prefs.edit { it.remove(keys.SOUNDCLOUD_CLIENT_ID) }
    }

    override suspend fun setShowInlineVolumeSlider(enabled: Boolean) {
        prefs.edit { it[keys.SHOW_INLINE_VOLUME_SLIDER] = enabled }
    }

    override suspend fun setShowVolumeButton(enabled: Boolean) {
        prefs.edit { it[keys.SHOW_VOLUME_BUTTON] = enabled }
    }

    override suspend fun setKeepScreenOnInApp(enabled: Boolean) {
        prefs.edit { it[keys.KEEP_SCREEN_ON_IN_APP] = enabled }
    }

    override suspend fun setKeepScreenOnWhilePlaying(enabled: Boolean) {
        prefs.edit { it[keys.KEEP_SCREEN_ON_WHILE_PLAYING] = enabled }
    }

    override suspend fun setSearchHistoryEnabled(enabled: Boolean) {
        prefs.edit { it[keys.SEARCH_HISTORY_ENABLED] = enabled }
    }

    override suspend fun setSearchHistoryBandcamp(enabled: Boolean) {
        prefs.edit { it[keys.SEARCH_HISTORY_BANDCAMP] = enabled }
    }

    override suspend fun setSearchHistoryYoutube(enabled: Boolean) {
        prefs.edit { it[keys.SEARCH_HISTORY_YOUTUBE] = enabled }
    }

    override suspend fun setSearchHistorySoundcloud(enabled: Boolean) {
        prefs.edit { it[keys.SEARCH_HISTORY_SOUNDCLOUD] = enabled }
    }

    override suspend fun setSearchHistoryLocal(enabled: Boolean) {
        prefs.edit { it[keys.SEARCH_HISTORY_LOCAL] = enabled }
    }

    override suspend fun setPlayerDebugOverlay(enabled: Boolean) {
        prefs.edit { it[keys.ALBUM_COVER_LONG_PRESS_CAROUSEL] = enabled }
    }

    override suspend fun setYoutubeDefaultSource(source: String) {
        prefs.edit { it[keys.YOUTUBE_DEFAULT_SOURCE] = source }
    }

    override suspend fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        prefs.edit { it[keys.AUTO_UPDATE_CHECK_ENABLED] = enabled }
    }

    override suspend fun setLastYoutubeVideoId(videoId: String?) {
        prefs.edit { p ->
            if (videoId != null) {
                p[keys.LAST_YOUTUBE_VIDEO_ID] = videoId
            } else {
                p.remove(keys.LAST_YOUTUBE_VIDEO_ID)
            }
        }
    }

    override suspend fun setBandcampCustomGenres(genres: List<String>) {
        prefs.edit { p ->
            if (genres.isNotEmpty()) {
                p[keys.BANDCAMP_CUSTOM_GENRES] = Json.encodeToString(genres)
            } else {
                p.remove(keys.BANDCAMP_CUSTOM_GENRES)
            }
        }
    }
}
