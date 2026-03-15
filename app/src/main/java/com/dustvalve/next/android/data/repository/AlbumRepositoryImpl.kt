package com.dustvalve.next.android.data.repository

import androidx.room.withTransaction
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.AlbumDao
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.dao.getByAlbumIds
import com.dustvalve.next.android.data.local.db.dao.getFavoriteIds
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.mapper.toDomain
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.data.remote.DustvalveAlbumScraper
import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.PurchaseInfo
import com.dustvalve.next.android.domain.repository.AlbumRepository
import com.dustvalve.next.android.domain.repository.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumRepositoryImpl @Inject constructor(
    private val database: DustvalveNextDatabase,
    private val albumDao: AlbumDao,
    private val trackDao: TrackDao,
    private val favoriteDao: FavoriteDao,
    private val albumScraper: DustvalveAlbumScraper,
    private val downloadRepository: DownloadRepository,
) : AlbumRepository {

    companion object {
        private const val REVALIDATE_THRESHOLD_MS = 30 * 60 * 1000L // 30 minutes
    }

    override suspend fun getAlbumDetail(url: String): Album {
        val cleanUrl = url.substringBefore('?').substringBefore('#').trimEnd('/')

        val cachedAlbum = findCachedAlbum(cleanUrl, url)
        if (cachedAlbum != null) {
            val trackEntities = trackDao.getByAlbumId(cachedAlbum.id)
            val age = System.currentTimeMillis() - cachedAlbum.cachedAt
            // Return cache if fresh and not a stub (has tracks)
            if (age < REVALIDATE_THRESHOLD_MS && trackEntities.isNotEmpty()) {
                return buildCachedAlbum(cachedAlbum, trackEntities)
            }
        }

        // Cache miss, stub, or stale: scrape and persist
        return scrapeAndPersistAlbum(cleanUrl, cachedAlbum)
    }

    override fun getAlbumDetailFlow(url: String): Flow<Album> = flow {
        val cleanUrl = url.substringBefore('?').substringBefore('#').trimEnd('/')

        val cachedAlbum = findCachedAlbum(cleanUrl, url)
        if (cachedAlbum != null) {
            val trackEntities = trackDao.getByAlbumId(cachedAlbum.id)
            // Emit cached data immediately if it's not a stub
            if (trackEntities.isNotEmpty()) {
                val cached = buildCachedAlbum(cachedAlbum, trackEntities)
                emit(cached)

                val age = System.currentTimeMillis() - cachedAlbum.cachedAt
                if (age < REVALIDATE_THRESHOLD_MS) return@flow // Fresh enough
            }
        }

        // No cache, stub, or stale: scrape and emit
        val fresh = scrapeAndPersistAlbum(cleanUrl, cachedAlbum)
        // Always emit fresh data after a scrape — metadata (title, art, tags)
        // may have changed even if track IDs are identical
        emit(fresh)
    }.flowOn(Dispatchers.IO)

    private suspend fun findCachedAlbum(
        cleanUrl: String,
        originalUrl: String,
    ): com.dustvalve.next.android.data.local.db.entity.AlbumEntity? {
        return albumDao.getByUrl(cleanUrl)
            ?: albumDao.getByUrl(cleanUrl.lowercase())
            ?: albumDao.getByUrl(originalUrl)
            ?: albumDao.getByUrl(originalUrl.trimEnd('/'))
    }

    private suspend fun buildCachedAlbum(
        cachedAlbum: com.dustvalve.next.android.data.local.db.entity.AlbumEntity,
        trackEntities: List<com.dustvalve.next.android.data.local.db.entity.TrackEntity>,
    ): Album {
        val allIds = listOf(cachedAlbum.id) + trackEntities.map { it.id }
        val favoriteIds = favoriteDao.getFavoriteIds(allIds).toSet()
        val tracks = trackEntities.map { it.toDomain(it.id in favoriteIds) }
        return cachedAlbum.toDomain(tracks, cachedAlbum.id in favoriteIds)
    }

    private suspend fun scrapeAndPersistAlbum(
        cleanUrl: String,
        cachedAlbum: com.dustvalve.next.android.data.local.db.entity.AlbumEntity?,
    ): Album {
        val album = albumScraper.scrapeAlbum(cleanUrl)

        val previousAutoDownload = cachedAlbum?.autoDownload ?: false
        val previousSaleItemId = cachedAlbum?.saleItemId
        val previousSaleItemType = cachedAlbum?.saleItemType
        val cachedTrackIds = if (cachedAlbum != null) {
            trackDao.getByAlbumId(cachedAlbum.id).map { it.id }.toSet()
        } else {
            emptySet()
        }
        val scrapedTrackIds = album.tracks.map { it.id }.toSet()
        val contentChanged = cachedTrackIds.isEmpty() || cachedTrackIds != scrapedTrackIds

        val result = if (contentChanged) {
            database.withTransaction {
                albumDao.insert(album.toEntity())
                trackDao.deleteByAlbumId(album.id)
                trackDao.insertAll(album.tracks.map { it.toEntity() })

                if (previousAutoDownload) {
                    albumDao.setAutoDownload(album.id, true)
                }
                if (previousSaleItemId != null && previousSaleItemType != null) {
                    albumDao.updatePurchaseInfo(album.id, previousSaleItemId, previousSaleItemType)
                }

                val allIds = listOf(album.id) + album.tracks.map { it.id }
                val favoriteIds = favoriteDao.getFavoriteIds(allIds).toSet()
                val tracksWithFavorites = album.tracks.map { track ->
                    track.copy(isFavorite = track.id in favoriteIds)
                }
                album.copy(
                    isFavorite = album.id in favoriteIds,
                    tracks = tracksWithFavorites,
                    autoDownload = previousAutoDownload,
                )
            }
        } else {
            // Content unchanged — just touch the timestamp
            albumDao.updateCachedAt(cachedAlbum?.id ?: album.id)
            val allIds = listOf(album.id) + album.tracks.map { it.id }
            val favoriteIds = favoriteDao.getFavoriteIds(allIds).toSet()
            val tracksWithFavorites = album.tracks.map { track ->
                track.copy(isFavorite = track.id in favoriteIds)
            }
            album.copy(
                isFavorite = album.id in favoriteIds,
                tracks = tracksWithFavorites,
                autoDownload = previousAutoDownload,
            )
        }

        // Auto-download new tracks if auto-download is enabled
        if (previousAutoDownload && cachedTrackIds.isNotEmpty()) {
            val newTracks = result.tracks.filter { it.id !in cachedTrackIds }
            for (track in newTracks) {
                try {
                    downloadRepository.downloadTrack(track)
                } catch (_: Exception) {
                    // Best-effort auto-download
                }
            }
        }

        return result
    }

    override suspend fun getAlbumById(id: String): Album? {
        val albumEntity = albumDao.getById(id) ?: return null
        val trackEntities = trackDao.getByAlbumId(id)
        val allIds = listOf(id) + trackEntities.map { it.id }
        val favoriteIds = favoriteDao.getFavoriteIds(allIds).toSet()
        val tracks = trackEntities.map { it.toDomain(it.id in favoriteIds) }
        return albumEntity.toDomain(tracks, id in favoriteIds)
    }

    override fun getFavoriteAlbums(): Flow<List<Album>> {
        return albumDao.getFavorites().map { albumEntities ->
            if (albumEntities.isEmpty()) return@map emptyList()

            // Batch-load all tracks for all favorite albums at once
            val albumIds = albumEntities.map { it.id }
            val allTracks = trackDao.getByAlbumIds(albumIds)
            val tracksByAlbum = allTracks.groupBy { it.albumId }

            // Batch-check favorite status for all track IDs
            val allTrackIds = allTracks.map { it.id }
            val favoriteTrackIds = favoriteDao.getFavoriteIds(allTrackIds).toSet()

            albumEntities.map { albumEntity ->
                val trackEntities = tracksByAlbum[albumEntity.id].orEmpty()
                val tracks = trackEntities.map { it.toDomain(it.id in favoriteTrackIds) }
                albumEntity.toDomain(tracks, isFavorite = true)
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun setAutoDownload(albumId: String, autoDownload: Boolean) {
        albumDao.setAutoDownload(albumId, autoDownload)
    }

    override suspend fun updatePurchaseInfo(albumId: String, purchaseInfo: PurchaseInfo) {
        albumDao.updatePurchaseInfo(albumId, purchaseInfo.saleItemId, purchaseInfo.saleItemType)
    }

    override suspend fun toggleFavorite(albumId: String) {
        database.withTransaction {
            val isFavorite = favoriteDao.isFavorite(albumId)
            if (isFavorite) {
                favoriteDao.delete(albumId)
            } else {
                favoriteDao.insert(FavoriteEntity(id = albumId, type = "album"))
            }
        }
    }
}
