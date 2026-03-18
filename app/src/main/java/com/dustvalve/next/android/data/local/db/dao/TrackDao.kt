package com.dustvalve.next.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

private const val SQLITE_MAX_BIND_PARAMS = 900

@Dao
interface TrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE albumId = :albumId")
    suspend fun deleteByAlbumId(albumId: String)

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY trackNumber ASC")
    suspend fun getByAlbumId(albumId: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getByIdsChunk(ids: List<String>): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE albumId IN (:albumIds) ORDER BY trackNumber ASC")
    suspend fun getByAlbumIdsChunk(albumIds: List<String>): List<TrackEntity>

    @Query(
        """
        SELECT tracks.* FROM tracks
        INNER JOIN favorites ON tracks.id = favorites.id
        WHERE favorites.type = 'track'
        ORDER BY favorites.addedAt DESC
        """
    )
    fun getFavorites(): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT tracks.* FROM tracks
        INNER JOIN recent_tracks ON tracks.id = recent_tracks.trackId
        ORDER BY recent_tracks.playedAt DESC
        """
    )
    fun getRecent(): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT tracks.* FROM tracks
        INNER JOIN downloads ON tracks.id = downloads.trackId
        ORDER BY downloads.downloadedAt DESC
        """
    )
    fun getDownloaded(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE source = 'local' ORDER BY title ASC")
    fun getLocalTracks(): Flow<List<TrackEntity>>

    @Query("SELECT id FROM tracks WHERE source = 'local'")
    suspend fun getLocalTrackIdsSync(): List<String>

    @Query("DELETE FROM tracks WHERE source = 'local'")
    suspend fun deleteAllLocalTracks()

    @Query("SELECT id FROM tracks WHERE source = 'local' AND folderUri = :folderUri")
    suspend fun getLocalTrackIdsByFolderSync(folderUri: String): List<String>

    @Query("DELETE FROM tracks WHERE source = 'local' AND folderUri = :folderUri")
    suspend fun deleteLocalTracksByFolder(folderUri: String)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteByIdsChunk(ids: List<String>)

    @Query(
        """
        SELECT * FROM tracks WHERE source = 'local'
        AND (title LIKE '%' || :query || '%'
          OR artist LIKE '%' || :query || '%'
          OR albumTitle LIKE '%' || :query || '%')
        ORDER BY title ASC LIMIT 50
        """
    )
    suspend fun searchLocalTracks(query: String): List<TrackEntity>
}

suspend fun TrackDao.deleteByIds(ids: Collection<String>) {
    if (ids.isEmpty()) return
    ids.chunked(SQLITE_MAX_BIND_PARAMS).forEach { chunk -> deleteByIdsChunk(chunk) }
}

suspend fun TrackDao.getByIds(ids: List<String>): List<TrackEntity> {
    if (ids.isEmpty()) return emptyList()
    return ids.chunked(SQLITE_MAX_BIND_PARAMS).flatMap { chunk -> getByIdsChunk(chunk) }
}

suspend fun TrackDao.getByAlbumIds(albumIds: List<String>): List<TrackEntity> {
    if (albumIds.isEmpty()) return emptyList()
    return albumIds.chunked(SQLITE_MAX_BIND_PARAMS).flatMap { chunk -> getByAlbumIdsChunk(chunk) }
}
