package com.dustvalve.next.android.data.local.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Local-music enable/folders + Local-tab sort/filter persistence. */
interface LocalMusicPrefsStore {
    val localMusicEnabled: Flow<Boolean>
    val localMusicFolderUris: Flow<List<String>>
    val localMusicUseMediaStore: Flow<Boolean>
    val keepLocalSort: Flow<Boolean>
    val keepLocalFilters: Flow<Boolean>
    val localSortOption: Flow<String?>
    val localReverseOrder: Flow<Boolean>
    val localSelectedArtists: Flow<Set<String>>
    val localSelectedAlbums: Flow<Set<String>>
    val localSelectedDurations: Flow<Set<String>>
    val localFavoritesOnly: Flow<Boolean>
    val localSelectedFolders: Flow<Set<String>>

    suspend fun setLocalMusicEnabled(enabled: Boolean)
    suspend fun setLocalMusicFolderUris(uris: List<String>)
    suspend fun addLocalMusicFolderUri(uri: String)
    suspend fun removeLocalMusicFolderUri(uri: String)
    suspend fun setLocalMusicUseMediaStore(enabled: Boolean)
    suspend fun getLocalMusicEnabledSync(): Boolean
    suspend fun getLocalMusicFolderUrisSync(): List<String>
    suspend fun getLocalMusicUseMediaStoreSync(): Boolean
    suspend fun setKeepLocalSort(enabled: Boolean)
    suspend fun setKeepLocalFilters(enabled: Boolean)
    suspend fun setLocalSort(sortOptionName: String, reverseOrder: Boolean)
    suspend fun setLocalFilters(
        artists: Set<String>,
        albums: Set<String>,
        durations: Set<String>,
        favoritesOnly: Boolean,
        folders: Set<String>,
    )
}
