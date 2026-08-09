package com.dustvalve.next.android.data.local.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class LocalMusicPrefsStoreImpl(
    private val prefs: SettingsPreferences,
) : LocalMusicPrefsStore {
    private val keys = SettingsPreferences.Keys

    override val localMusicEnabled: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.LOCAL_MUSIC_ENABLED] ?: false
    }
    override val localMusicFolderUris: Flow<List<String>> = prefs.guardedPreferences.map {
        StringListPreference.decode(it[keys.LOCAL_MUSIC_FOLDER_URIS])
    }
    override val localMusicUseMediaStore: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.LOCAL_MUSIC_USE_MEDIASTORE] ?: true
    }
    override val keepLocalSort: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.KEEP_LOCAL_SORT] ?: false
    }
    override val keepLocalFilters: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.KEEP_LOCAL_FILTERS] ?: false
    }
    override val localSortOption: Flow<String?> = prefs.guardedPreferences.map { it[keys.LOCAL_SORT_OPTION] }
    override val localReverseOrder: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.LOCAL_REVERSE_ORDER] ?: false
    }
    override val localSelectedArtists: Flow<Set<String>> = prefs.guardedPreferences.map {
        it[keys.LOCAL_SELECTED_ARTISTS] ?: emptySet()
    }
    override val localSelectedAlbums: Flow<Set<String>> = prefs.guardedPreferences.map {
        it[keys.LOCAL_SELECTED_ALBUMS] ?: emptySet()
    }
    override val localSelectedDurations: Flow<Set<String>> = prefs.guardedPreferences.map {
        it[keys.LOCAL_SELECTED_DURATIONS] ?: emptySet()
    }
    override val localFavoritesOnly: Flow<Boolean> = prefs.guardedPreferences.map {
        it[keys.LOCAL_FAVORITES_ONLY] ?: false
    }
    override val localSelectedFolders: Flow<Set<String>> = prefs.guardedPreferences.map {
        it[keys.LOCAL_SELECTED_FOLDERS] ?: emptySet()
    }

    override suspend fun setLocalMusicEnabled(enabled: Boolean) {
        prefs.edit { it[keys.LOCAL_MUSIC_ENABLED] = enabled }
    }

    override suspend fun setLocalMusicFolderUris(uris: List<String>) {
        prefs.edit { p ->
            if (uris.isNotEmpty()) {
                p[keys.LOCAL_MUSIC_FOLDER_URIS] = Json.encodeToString(uris)
            } else {
                p.remove(keys.LOCAL_MUSIC_FOLDER_URIS)
            }
        }
    }

    override suspend fun addLocalMusicFolderUri(uri: String) {
        prefs.edit { p ->
            // decodeStringList treats a malformed stored value as empty, so
            // this write also repairs the key.
            val current = StringListPreference.decode(p[keys.LOCAL_MUSIC_FOLDER_URIS])
            if (uri !in current) {
                p[keys.LOCAL_MUSIC_FOLDER_URIS] = Json.encodeToString(current + uri)
            }
        }
    }

    override suspend fun removeLocalMusicFolderUri(uri: String) {
        prefs.edit { p ->
            val current = StringListPreference.decode(p[keys.LOCAL_MUSIC_FOLDER_URIS])
            val updated = current - uri
            if (updated.isNotEmpty()) {
                p[keys.LOCAL_MUSIC_FOLDER_URIS] = Json.encodeToString(updated)
            } else {
                p.remove(keys.LOCAL_MUSIC_FOLDER_URIS)
            }
        }
    }

    override suspend fun setLocalMusicUseMediaStore(enabled: Boolean) {
        prefs.edit { it[keys.LOCAL_MUSIC_USE_MEDIASTORE] = enabled }
    }

    override suspend fun getLocalMusicEnabledSync(): Boolean =
        prefs.guardedPreferences.firstOrNull()?.get(keys.LOCAL_MUSIC_ENABLED) ?: false

    override suspend fun getLocalMusicFolderUrisSync(): List<String> =
        StringListPreference.decode(
            prefs.guardedPreferences.firstOrNull()?.get(keys.LOCAL_MUSIC_FOLDER_URIS),
        )

    override suspend fun getLocalMusicUseMediaStoreSync(): Boolean =
        prefs.guardedPreferences.firstOrNull()?.get(keys.LOCAL_MUSIC_USE_MEDIASTORE) ?: true

    override suspend fun setKeepLocalSort(enabled: Boolean) {
        prefs.edit { it[keys.KEEP_LOCAL_SORT] = enabled }
    }

    override suspend fun setKeepLocalFilters(enabled: Boolean) {
        prefs.edit { it[keys.KEEP_LOCAL_FILTERS] = enabled }
    }

    override suspend fun setLocalSort(sortOptionName: String, reverseOrder: Boolean) {
        prefs.edit {
            it[keys.LOCAL_SORT_OPTION] = sortOptionName
            it[keys.LOCAL_REVERSE_ORDER] = reverseOrder
        }
    }

    override suspend fun setLocalFilters(
        artists: Set<String>,
        albums: Set<String>,
        durations: Set<String>,
        favoritesOnly: Boolean,
        folders: Set<String>,
    ) {
        prefs.edit {
            it[keys.LOCAL_SELECTED_ARTISTS] = artists
            it[keys.LOCAL_SELECTED_ALBUMS] = albums
            it[keys.LOCAL_SELECTED_DURATIONS] = durations
            it[keys.LOCAL_FAVORITES_ONLY] = favoritesOnly
            it[keys.LOCAL_SELECTED_FOLDERS] = folders
        }
    }
}
