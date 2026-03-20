package com.dustvalve.next.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.dustvalve.next.android.data.local.db.entity.PlaylistEntity
import com.dustvalve.next.android.data.local.db.entity.PlaylistTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY isPinned DESC, sortOrder ASC, createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylistByIdFlow(playlistId: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE isSystem = 1 AND systemType = :systemType")
    suspend fun getSystemPlaylistByType(systemType: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE isSystem = 1 AND systemType = :systemType")
    fun getSystemPlaylistByTypeFlow(systemType: String): Flow<PlaylistEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistIfAbsent(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE id = :playlistId AND isSystem = 0")
    suspend fun renamePlaylist(playlistId: String, name: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE playlists SET name = :name, shapeKey = :shapeKey, iconUrl = :iconUrl, updatedAt = :updatedAt WHERE id = :playlistId AND isSystem = 0")
    suspend fun updatePlaylistAppearance(playlistId: String, name: String, shapeKey: String?, iconUrl: String?, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE playlists SET isPinned = :isPinned WHERE id = :playlistId")
    suspend fun setPlaylistPinned(playlistId: String, isPinned: Boolean): Int

    @Query("UPDATE playlists SET autoDownload = :autoDownload WHERE id = :playlistId")
    suspend fun setAutoDownload(playlistId: String, autoDownload: Boolean)

    @Query("UPDATE playlists SET sortOrder = :sortOrder WHERE id = :playlistId")
    suspend fun setPlaylistSortOrder(playlistId: String, sortOrder: Int)

    @Query("DELETE FROM playlists WHERE id = :playlistId AND isSystem = 0")
    suspend fun deletePlaylist(playlistId: String): Int

    @Query("SELECT * FROM playlists WHERE name = :name AND isSystem = 0 ORDER BY createdAt DESC LIMIT 1")
    suspend fun getPlaylistByName(name: String): PlaylistEntity?

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun getPlaylistCount(): Int

    // Playlist tracks operations

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
    """)
    fun getTracksInPlaylist(playlistId: String): Flow<List<com.dustvalve.next.android.data.local.db.entity.TrackEntity>>

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
    """)
    suspend fun getTracksInPlaylistSync(playlistId: String): List<com.dustvalve.next.android.data.local.db.entity.TrackEntity>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getPlaylistTrackCount(playlistId: String): Int

    @Query("UPDATE playlists SET trackCount = :count WHERE id = :playlistId")
    suspend fun updateTrackCount(playlistId: String, count: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(playlistTrack: PlaylistTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(playlistTracks: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: String)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun updateTrackPosition(playlistId: String, trackId: String, position: Int)

    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: String): Int?

    @Query("SELECT artUrl FROM tracks WHERE id = :trackId")
    suspend fun getTrackArtUrl(trackId: String): String?

    @Query("UPDATE playlists SET iconUrl = :iconUrl WHERE id = :playlistId")
    suspend fun updatePlaylistIconUrl(playlistId: String, iconUrl: String?)

    @Transaction
    suspend fun addTrackToPlaylist(playlistId: String, trackId: String) {
        // Skip if track is already in the playlist to avoid silently repositioning it
        if (isTrackInPlaylist(playlistId, trackId)) return
        val maxPosition = getMaxPosition(playlistId) ?: -1
        insertPlaylistTrack(PlaylistTrackEntity(playlistId, trackId, maxPosition + 1))
        updateTrackCount(playlistId, getPlaylistTrackCount(playlistId))
        // Auto-set cover art from first track if playlist has no cover
        val playlist = getPlaylistById(playlistId)
        if (playlist != null && playlist.iconUrl == null) {
            val artUrl = getTrackArtUrl(trackId)
            if (artUrl != null) {
                updatePlaylistIconUrl(playlistId, artUrl)
            }
        }
    }

    @Transaction
    suspend fun removeTrackFromPlaylistAndUpdateCount(playlistId: String, trackId: String) {
        // Get current position of the track being removed
        val position = getTrackPosition(playlistId, trackId) ?: return

        // Remove the track
        removeTrackFromPlaylist(playlistId, trackId)

        // Update positions of remaining tracks
        shiftTrackPositions(playlistId, position)

        // Update count
        updateTrackCount(playlistId, getPlaylistTrackCount(playlistId))
    }

    @Query("SELECT position FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun getTrackPosition(playlistId: String, trackId: String): Int?

    @Query("UPDATE playlist_tracks SET position = position - 1 WHERE playlistId = :playlistId AND position > :position")
    suspend fun shiftTrackPositions(playlistId: String, position: Int)

    @Transaction
    suspend fun reorderTrack(playlistId: String, trackId: String, fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return

        if (fromPosition < toPosition) {
            // Moving down: decrement positions of tracks between from and to
            @Suppress("SqlSourceToSinkFlow")
            shiftTrackPositionsRange(playlistId, fromPosition + 1, toPosition, -1)
        } else {
            // Moving up: increment positions of tracks between to and from
            @Suppress("SqlSourceToSinkFlow")
            shiftTrackPositionsRange(playlistId, toPosition, fromPosition - 1, 1)
        }

        // Set the moved track's position
        updateTrackPosition(playlistId, trackId, toPosition)
    }

    @Query("UPDATE playlist_tracks SET position = position + :delta WHERE playlistId = :playlistId AND position BETWEEN :start AND :end")
    suspend fun shiftTrackPositionsRange(playlistId: String, start: Int, end: Int, delta: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId)")
    suspend fun isTrackInPlaylist(playlistId: String, trackId: String): Boolean

    @Query("SELECT DISTINCT pt.trackId FROM playlist_tracks pt INNER JOIN playlists p ON pt.playlistId = p.id WHERE p.isSystem = 0")
    fun getTrackIdsInUserPlaylists(): Flow<List<String>>

    @Query("SELECT * FROM playlist_tracks")
    fun getAllPlaylistTrackMappings(): Flow<List<PlaylistTrackEntity>>
}
