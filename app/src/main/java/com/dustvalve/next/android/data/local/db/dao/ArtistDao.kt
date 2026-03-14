package com.dustvalve.next.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dustvalve.next.android.data.local.db.entity.ArtistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(artist: ArtistEntity)

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getById(id: String): ArtistEntity?

    @Query("SELECT * FROM artists WHERE url = :url")
    suspend fun getByUrl(url: String): ArtistEntity?

    @Query("""
        SELECT a.* FROM artists a
        INNER JOIN favorites f ON a.id = f.id
        WHERE f.type = 'artist'
        ORDER BY f.addedAt DESC
    """)
    fun getFavoriteArtists(): Flow<List<ArtistEntity>>

    @Query("UPDATE artists SET autoDownload = :autoDownload WHERE id = :artistId")
    suspend fun setAutoDownload(artistId: String, autoDownload: Boolean)

    @Query("UPDATE artists SET cachedAt = :cachedAt WHERE id = :artistId")
    suspend fun updateCachedAt(artistId: String, cachedAt: Long = System.currentTimeMillis())
}
