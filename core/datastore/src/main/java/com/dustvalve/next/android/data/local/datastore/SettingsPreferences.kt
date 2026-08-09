package com.dustvalve.next.android.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

// A corrupt preferences file (torn write, bit rot) without a corruption
// handler is fatal-forever: every READ throws CorruptionException into the
// flow and every WRITE rethrows it, so no edit can ever repair the file.
// Replacing with emptyPreferences() self-heals to defaults instead.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Shared DataStore handle + preference keys for the settings domain slices.
 * Every settings READ goes through [guardedPreferences]: DataStore reports
 * read failures by throwing INTO the flow, so a bare `dataStore.data`
 * collector dies on the first IOException and never resumes. Recover to
 * defaults instead.
 *
 * CorruptionException never reaches the catch: it EXTENDS IOException, and
 * the ReplaceFileCorruptionHandler installed on the DataStore consumes
 * corruption before it surfaces.
 */
internal class SettingsPreferences(context: Context) {
    private val dataStore = context.settingsDataStore

    val guardedPreferences: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    suspend fun edit(transform: suspend (MutablePreferences) -> Unit) {
        dataStore.edit(transform)
    }

    object Keys {
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
        val DOWNLOAD_NOTIFICATIONS_ENABLED = booleanPreferencesKey("download_notifications_enabled")
        val OLED_BLACK = booleanPreferencesKey("oled_black")
        val ALBUM_ART_THEME = booleanPreferencesKey("album_art_theme")
        val PROGRESS_BAR_STYLE = stringPreferencesKey("progress_bar_style")
        val PROGRESS_BAR_SIZE_DP = androidx.datastore.preferences.core.intPreferencesKey("progress_bar_size_dp")
        val AUTO_DOWNLOAD_FAVORITES = booleanPreferencesKey("auto_download_favorites")
        val LOCAL_MUSIC_ENABLED = booleanPreferencesKey("local_music_enabled")
        val LOCAL_MUSIC_FOLDER_URIS = stringPreferencesKey("local_music_folder_uris")
        val LOCAL_MUSIC_USE_MEDIASTORE = booleanPreferencesKey("local_music_use_mediastore")
        val BANDCAMP_ENABLED = booleanPreferencesKey("bandcamp_enabled")
        val YOUTUBE_ENABLED = booleanPreferencesKey("youtube_enabled")
        val SOUNDCLOUD_ENABLED = booleanPreferencesKey("soundcloud_enabled")
        val SOUNDCLOUD_CLIENT_ID = stringPreferencesKey("soundcloud_client_id")
        val SHOW_INLINE_VOLUME_SLIDER = booleanPreferencesKey("show_inline_volume_slider")
        val SHOW_VOLUME_BUTTON = booleanPreferencesKey("show_volume_button")
        val LAST_YOUTUBE_VIDEO_ID = stringPreferencesKey("last_youtube_video_id")
        val SEARCH_HISTORY_ENABLED = booleanPreferencesKey("search_history_enabled")
        val SEARCH_HISTORY_BANDCAMP = booleanPreferencesKey("search_history_bandcamp")
        val SEARCH_HISTORY_YOUTUBE = booleanPreferencesKey("search_history_youtube")
        val SEARCH_HISTORY_SOUNDCLOUD = booleanPreferencesKey("search_history_soundcloud")
        val SEARCH_HISTORY_LOCAL = booleanPreferencesKey("search_history_local")
        val ALBUM_COVER_LONG_PRESS_CAROUSEL = booleanPreferencesKey("album_cover_long_press_carousel")
        val YTM_CONNECTED = booleanPreferencesKey("ytm_connected")
        val YOUTUBE_DEFAULT_SOURCE = stringPreferencesKey("youtube_default_source")
        val KEEP_SCREEN_ON_IN_APP = booleanPreferencesKey("keep_screen_on_in_app")
        val KEEP_SCREEN_ON_WHILE_PLAYING = booleanPreferencesKey("keep_screen_on_while_playing")
        val KEEP_LOCAL_SORT = booleanPreferencesKey("keep_local_sort")
        val KEEP_LOCAL_FILTERS = booleanPreferencesKey("keep_local_filters")
        val LOCAL_SORT_OPTION = stringPreferencesKey("local_sort_option")
        val LOCAL_REVERSE_ORDER = booleanPreferencesKey("local_reverse_order")
        val LOCAL_SELECTED_ARTISTS = stringSetPreferencesKey("local_selected_artists")
        val LOCAL_SELECTED_ALBUMS = stringSetPreferencesKey("local_selected_albums")
        val LOCAL_SELECTED_DURATIONS = stringSetPreferencesKey("local_selected_durations")
        val LOCAL_FAVORITES_ONLY = booleanPreferencesKey("local_favorites_only")
        val LOCAL_SELECTED_FOLDERS = stringSetPreferencesKey("local_selected_folders")
        val BANDCAMP_CUSTOM_GENRES = stringPreferencesKey("bandcamp_custom_genres")
        val AUTO_UPDATE_CHECK_ENABLED = booleanPreferencesKey("auto_update_check_enabled")
    }
}
