package com.dustvalve.next.android.data.repository

import androidx.room.withTransaction
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.RecentTrackDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.local.db.entity.RecentTrackEntity
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.LibraryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val database: DustvalveNextDatabase,
    private val trackDao: TrackDao,
    private val favoriteDao: FavoriteDao,
    private val recentTrackDao: RecentTrackDao,
) : LibraryRepository {

    companion object {
        private const val MAX_RECENT_TRACKS = 100
    }

    override suspend fun toggleTrackFavorite(trackId: String): Boolean = database.withTransaction {
        val isFavorite = favoriteDao.isFavorite(trackId)
        if (isFavorite) {
            favoriteDao.delete(trackId)
        } else {
            favoriteDao.insert(FavoriteEntity(id = trackId, type = "track"))
        }
        !isFavorite
    }

    override suspend fun addToRecent(track: Track) {
        database.withTransaction {
            // Ensure the track exists in the database
            if (trackDao.getById(track.id) == null) {
                trackDao.insertAll(listOf(track.toEntity()))
            }

            // Insert or update the recent track entry
            recentTrackDao.insert(
                RecentTrackEntity(
                    trackId = track.id,
                    playedAt = System.currentTimeMillis(),
                ),
            )

            // Clean up old entries (keepCount must be >= 1 to avoid deleting all)
            recentTrackDao.deleteOld(MAX_RECENT_TRACKS.coerceAtLeast(1))
        }
    }
}
