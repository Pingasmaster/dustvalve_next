package com.dustvalve.next.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

private const val SQLITE_MAX_BIND_PARAMS = 900

@Dao
interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id)")
    suspend fun isFavorite(id: String): Boolean

    @Query("SELECT id FROM favorites WHERE id IN (:ids)")
    suspend fun getFavoriteIdsChunk(ids: List<String>): List<String>

    @Query("SELECT * FROM favorites WHERE type = :type ORDER BY addedAt DESC")
    fun getAllByType(type: String): Flow<List<FavoriteEntity>>

    @Query("UPDATE favorites SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: String, isPinned: Boolean)

    @Query("UPDATE favorites SET shapeKey = :shapeKey WHERE id = :id")
    suspend fun setShapeKey(id: String, shapeKey: String?)

    @Query("""
        SELECT f.id, f.addedAt, f.isPinned, f.shapeKey,
               a.title AS albumTitle, a.artist AS albumArtist, a.artUrl AS albumArtUrl, a.url AS albumUrl
        FROM favorites f INNER JOIN albums a ON f.id = a.id
        WHERE f.type = 'album'
        ORDER BY f.isPinned DESC, f.addedAt DESC
    """)
    fun getFavoritedAlbumsWithInfo(): Flow<List<FavoriteAlbumInfo>>

    @Query("""
        SELECT f.id, f.addedAt, f.isPinned, f.shapeKey,
               ar.name AS artistName, ar.imageUrl AS artistImageUrl, ar.url AS artistUrl
        FROM favorites f INNER JOIN artists ar ON f.id = ar.id
        WHERE f.type = 'artist'
        ORDER BY f.isPinned DESC, f.addedAt DESC
    """)
    fun getFavoritedArtistsWithInfo(): Flow<List<FavoriteArtistInfo>>
}

suspend fun FavoriteDao.getFavoriteIds(ids: List<String>): List<String> {
    if (ids.isEmpty()) return emptyList()
    return ids.chunked(SQLITE_MAX_BIND_PARAMS).flatMap { chunk -> getFavoriteIdsChunk(chunk) }
}
