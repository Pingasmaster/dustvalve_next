package com.dustvalve.next.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dustvalve.next.android.data.local.db.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

private const val SQLITE_MAX_BIND_PARAMS = 900

@Dao
interface AlbumDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: AlbumEntity)

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getById(id: String): AlbumEntity?

    @Query("SELECT * FROM albums")
    suspend fun getAll(): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE id IN (:ids)")
    suspend fun getByIdsChunk(ids: List<String>): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE url = :url")
    suspend fun getByUrl(url: String): AlbumEntity?

    @Query(
        """
        SELECT albums.* FROM albums
        INNER JOIN favorites ON albums.id = favorites.id
        WHERE favorites.type = 'album'
        ORDER BY favorites.addedAt DESC
        """
    )
    fun getFavorites(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE artistUrl = :artistUrl")
    suspend fun getByArtistUrl(artistUrl: String): List<AlbumEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(album: AlbumEntity)

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE albums SET autoDownload = :autoDownload WHERE id = :albumId")
    suspend fun setAutoDownload(albumId: String, autoDownload: Boolean)

    @Query("UPDATE albums SET saleItemId = :saleItemId, saleItemType = :saleItemType WHERE id = :albumId")
    suspend fun updatePurchaseInfo(albumId: String, saleItemId: Long, saleItemType: String)

    @Query("UPDATE albums SET cachedAt = :cachedAt WHERE id = :albumId")
    suspend fun updateCachedAt(albumId: String, cachedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM albums")
    fun getAllFlow(): Flow<List<AlbumEntity>>

    @Query("DELETE FROM albums")
    suspend fun deleteAll()
}

suspend fun AlbumDao.getByIds(ids: List<String>): List<AlbumEntity> {
    if (ids.isEmpty()) return emptyList()
    return ids.chunked(SQLITE_MAX_BIND_PARAMS).flatMap { chunk -> getByIdsChunk(chunk) }
}
