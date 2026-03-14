package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    // Playlist CRUD
    fun getAllPlaylists(): Flow<List<Playlist>>
    fun getPlaylistById(playlistId: String): Flow<Playlist?>
    suspend fun getPlaylistByIdSync(playlistId: String): Playlist?
    suspend fun createPlaylist(name: String, shapeKey: String? = null, iconUrl: String? = null): Playlist
    suspend fun renamePlaylist(playlistId: String, newName: String): Boolean
    suspend fun updatePlaylistAppearance(playlistId: String, name: String, shapeKey: String?, iconUrl: String?): Boolean
    suspend fun deletePlaylist(playlistId: String): Boolean
    suspend fun pinPlaylist(playlistId: String, isPinned: Boolean): Boolean
    suspend fun setAutoDownload(playlistId: String, autoDownload: Boolean)

    // System playlists (Downloads, Recent, Collection, Favorites)
    suspend fun ensureSystemPlaylistsExist()
    fun getSystemPlaylist(type: Playlist.SystemPlaylistType): Flow<Playlist?>
    suspend fun getSystemPlaylistSync(type: Playlist.SystemPlaylistType): Playlist?

    // Track management
    fun getTracksInPlaylist(playlistId: String): Flow<List<Track>>
    suspend fun getTracksInPlaylistSync(playlistId: String): List<Track>
    suspend fun addTrackToPlaylist(playlistId: String, trackId: String)
    suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<String>)
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)
    suspend fun moveTrackInPlaylist(playlistId: String, fromPosition: Int, toPosition: Int)
    suspend fun isTrackInPlaylist(playlistId: String, trackId: String): Boolean
    fun getTrackIdsInUserPlaylists(): Flow<Set<String>>

    // Auto-sync for system playlists
    suspend fun syncDownloadsPlaylist()
    suspend fun syncRecentPlaylist()
    suspend fun syncCollectionPlaylist()
    suspend fun syncFavoritesPlaylist()
}
