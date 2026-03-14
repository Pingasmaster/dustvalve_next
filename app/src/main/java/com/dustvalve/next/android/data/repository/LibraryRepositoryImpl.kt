package com.dustvalve.next.android.data.repository

import androidx.room.withTransaction
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.RecentTrackDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.dao.getFavoriteIds
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.local.db.entity.RecentTrackEntity
import com.dustvalve.next.android.data.mapper.toDomain
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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

    override fun getFavoriteTracks(): Flow<List<Track>> {
        return trackDao.getFavorites().map { trackEntities ->
            trackEntities.map { it.toDomain(isFavorite = true) }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun toggleTrackFavorite(trackId: String): Boolean {
        return database.withTransaction {
            val isFavorite = favoriteDao.isFavorite(trackId)
            if (isFavorite) {
                favoriteDao.delete(trackId)
            } else {
                favoriteDao.insert(FavoriteEntity(id = trackId, type = "track"))
            }
            !isFavorite
        }
    }

    override fun getRecentTracks(): Flow<List<Track>> {
        return trackDao.getRecent().map { trackEntities ->
            if (trackEntities.isEmpty()) return@map emptyList()
            val allIds = trackEntities.map { it.id }
            val favoriteIds = favoriteDao.getFavoriteIds(allIds).toSet()
            trackEntities.map { trackEntity ->
                trackEntity.toDomain(trackEntity.id in favoriteIds)
            }
        }.flowOn(Dispatchers.IO)
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
                )
            )

            // Clean up old entries (keepCount must be >= 1 to avoid deleting all)
            recentTrackDao.deleteOld(MAX_RECENT_TRACKS.coerceAtLeast(1))
        }
    }
}
