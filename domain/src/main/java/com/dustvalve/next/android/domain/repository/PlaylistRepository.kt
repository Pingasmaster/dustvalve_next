package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.FavoriteType
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

    // System playlists (Downloads, Recent, Favorites)
    suspend fun ensureSystemPlaylistsExist()
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
    suspend fun syncRecentPlaylist()

    // Playlist import (YouTube playlist import / collection import)

    /**
     * Atomically (one database transaction): cache [tracks], create a playlist
     * named [name], add the tracks to it, and - when [favoriteId] is given -
     * insert a favorites row of [favoriteType] in the SAME transaction, so
     * cancellation cannot leave an imported-but-unfavorited playlist.
     * Callers that favorite OUTSIDE the import transaction (collection detail)
     * simply omit the favorite parameters.
     */
    suspend fun importTracksAsPlaylist(
        name: String,
        tracks: List<Track>,
        favoriteId: String? = null,
        favoriteType: FavoriteType? = null,
    ): Playlist

    /**
     * Display-only "already imported" probe. Deliberately a Boolean, never the
     * playlist or its id: callers must not learn a playlist id from a name
     * lookup (deletion-authorization safety - a name can collide with an
     * unrelated user playlist, and deleting that would destroy user data).
     */
    suspend fun playlistExistsByName(name: String): Boolean

    /** Reactive playlistId -> set of member trackIds for every playlist_tracks row. */
    fun getPlaylistTrackMappings(): Flow<Map<String, Set<String>>>
}
