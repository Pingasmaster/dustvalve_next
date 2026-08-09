package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.local.DatabaseGateway
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.dao.getByIds
import com.dustvalve.next.android.data.local.db.dao.getFavoriteIds
import com.dustvalve.next.android.data.mapper.toDomain
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.TrackCacheRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackCacheRepositoryImpl(private val trackDao: TrackDao, private val favoriteDao: FavoriteDao) : TrackCacheRepository {

    @Inject constructor(gateway: DatabaseGateway) : this(gateway.trackDao, gateway.favoriteDao)

    // TrackDao.insertAll is a true @Upsert (NOT REPLACE - REPLACE would
    // cascade-delete playlist_tracks memberships). No transaction: matches
    // today's page-caching call sites.
    override suspend fun cacheTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        trackDao.insertAll(tracks.map { it.toEntity() })
    }

    override suspend fun getTrack(trackId: String): Track? {
        val entity = trackDao.getById(trackId) ?: return null
        val isFavorite = favoriteDao.isFavorite(trackId, "track")
        return entity.toDomain(isFavorite)
    }

    override suspend fun getTracks(trackIds: List<String>): List<Track> {
        val entities = trackDao.getByIds(trackIds)
        if (entities.isEmpty()) return emptyList()
        val favoriteIds = favoriteDao.getFavoriteIds("track", entities.map { it.id }).toSet()
        return entities.map { it.toDomain(it.id in favoriteIds) }
    }
}
