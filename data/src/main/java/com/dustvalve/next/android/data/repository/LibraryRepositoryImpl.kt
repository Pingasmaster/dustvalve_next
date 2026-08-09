package com.dustvalve.next.android.data.repository

import androidx.room.withTransaction
import com.dustvalve.next.android.data.local.DatabaseGateway
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.RecentTrackDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.local.db.entity.RecentTrackEntity
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.domain.model.FavoriteAlbumItem
import com.dustvalve.next.android.domain.model.FavoriteArtistItem
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl(
    private val database: DustvalveNextDatabase,
    private val trackDao: TrackDao,
    private val favoriteDao: FavoriteDao,
    private val recentTrackDao: RecentTrackDao,
) : LibraryRepository {

    @Inject constructor(gateway: DatabaseGateway) :
        this(gateway.database, gateway.trackDao, gateway.favoriteDao, gateway.recentTrackDao)

    companion object {
        private const val MAX_RECENT_TRACKS = 100
    }

    override suspend fun toggleTrackFavorite(track: Track): Boolean = database.withTransaction {
        // Favorites playlist is an INNER JOIN on tracks - a favorite row
        // without a tracks row is invisible membership. Cache first.
        if (trackDao.getById(track.id) == null) {
            trackDao.insertAll(listOf(track.toEntity()))
        }
        toggleTrackFavoriteUnlocked(track.id)
    }

    override suspend fun toggleTrackFavorite(trackId: String): Boolean = database.withTransaction {
        toggleTrackFavoriteUnlocked(trackId)
    }

    private suspend fun toggleTrackFavoriteUnlocked(trackId: String): Boolean {
        val isFavorite = favoriteDao.isFavorite(trackId)
        if (isFavorite) {
            favoriteDao.delete(trackId)
            return false
        }
        // Refuse to write an orphan favorite that Favorites would hide.
        if (trackDao.getById(trackId) == null) return false
        favoriteDao.insert(FavoriteEntity(id = trackId, type = "track"))
        return true
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

    override fun getFavoriteAlbums(): Flow<List<FavoriteAlbumItem>> = favoriteDao.getFavoritedAlbumsWithInfo().map { infos ->
        infos.map { info ->
            FavoriteAlbumItem(
                id = info.id,
                addedAt = info.addedAt,
                isPinned = info.isPinned,
                shapeKey = info.shapeKey,
                albumTitle = info.albumTitle,
                albumArtist = info.albumArtist,
                albumArtUrl = info.albumArtUrl,
                albumUrl = info.albumUrl,
            )
        }
    }

    override fun getFavoriteArtists(): Flow<List<FavoriteArtistItem>> = favoriteDao.getFavoritedArtistsWithInfo().map { infos ->
        infos.map { info ->
            FavoriteArtistItem(
                id = info.id,
                addedAt = info.addedAt,
                isPinned = info.isPinned,
                shapeKey = info.shapeKey,
                artistName = info.artistName,
                artistImageUrl = info.artistImageUrl,
                artistUrl = info.artistUrl,
            )
        }
    }
}
