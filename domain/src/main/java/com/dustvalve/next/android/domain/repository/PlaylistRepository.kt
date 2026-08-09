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

    /**
     * Caches [track] into Room (upsert), then adds membership. Returns true when
     * the membership write ran; false when the track still cannot be added
     * (callers must not toast success on false).
     */
    suspend fun addTrackToPlaylist(playlistId: String, track: Track): Boolean

    /**
     * Adds an already-cached track by id. Returns false when the tracks row is
     * missing - a silent historical no-op that used to still snackbar success.
     */
    suspend fun addTrackToPlaylist(playlistId: String, trackId: String): Boolean
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
     * Collection detail favoriting uses this with a real source URL as
     * [favoriteId]; the standalone import button omits the favorite parameters.
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
