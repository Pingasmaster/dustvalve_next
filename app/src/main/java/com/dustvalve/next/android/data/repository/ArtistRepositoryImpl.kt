package com.dustvalve.next.android.data.repository

import androidx.room.withTransaction
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.AlbumDao
import com.dustvalve.next.android.data.local.db.dao.ArtistDao
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.dao.getByAlbumIds
import com.dustvalve.next.android.data.local.db.dao.getFavoriteIds
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.mapper.toDomain
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.data.remote.DustvalveArtistScraper
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.AlbumRepository
import com.dustvalve.next.android.domain.repository.ArtistRepository
import com.dustvalve.next.android.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val orderJson = Json { ignoreUnknownKeys = true }

@Singleton
class ArtistRepositoryImpl @Inject constructor(
    private val database: DustvalveNextDatabase,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val favoriteDao: FavoriteDao,
    private val trackDao: TrackDao,
    private val artistScraper: DustvalveArtistScraper,
    private val downloadRepository: DownloadRepository,
    private val albumRepository: AlbumRepository,
) : ArtistRepository {

    companion object {
        private const val REVALIDATE_THRESHOLD_MS = 30 * 60 * 1000L // 30 minutes
    }

    override suspend fun getArtistDetail(url: String): Artist {
        val cleanUrl = url.substringBefore('?').substringBefore('#').trimEnd('/')

        // Try cache first
        val cachedArtist = artistDao.getByUrl(cleanUrl) ?: artistDao.getByUrl(url)
        if (cachedArtist != null) {
            val age = System.currentTimeMillis() - cachedArtist.cachedAt
            if (age < REVALIDATE_THRESHOLD_MS) {
                return buildCachedArtist(cachedArtist, cleanUrl, url)
            }
            // Stale: try revalidate, fall back to stale cache offline
            return try {
                scrapeAndPersistArtist(cleanUrl, url, cachedArtist)
            } catch (e: Exception) {
                if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                buildCachedArtist(cachedArtist, cleanUrl, url)
            }
        }

        // Cache miss: scrape and persist
        return scrapeAndPersistArtist(cleanUrl, url, cachedArtist)
    }

    override fun getArtistDetailFlow(url: String): Flow<Artist> = flow {
        val cleanUrl = url.substringBefore('?').substringBefore('#').trimEnd('/')

        val cachedArtist = artistDao.getByUrl(cleanUrl) ?: artistDao.getByUrl(url)
        if (cachedArtist != null) {
            // Always emit cached data immediately
            emit(buildCachedArtist(cachedArtist, cleanUrl, url))

            val age = System.currentTimeMillis() - cachedArtist.cachedAt
            if (age < REVALIDATE_THRESHOLD_MS) return@flow // Fresh enough, no revalidation
        }

        // No cache or stale: scrape in background and emit updated result
        try {
            val fresh = scrapeAndPersistArtist(cleanUrl, url, cachedArtist)
            // Only re-emit if we didn't have cache, or if content actually changed
            if (cachedArtist == null || didArtistChange(cachedArtist, fresh)) {
                emit(fresh)
            }
        } catch (e: Exception) {
            if (e is kotlin.coroutines.cancellation.CancellationException) throw e
            if (cachedArtist == null) throw e
            // Stale cache already emitted — swallow network error for offline use
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun buildCachedArtist(
        cachedArtist: com.dustvalve.next.android.data.local.db.entity.ArtistEntity,
        cleanUrl: String,
        originalUrl: String,
    ): Artist {
        val isFavorite = favoriteDao.isFavorite(cachedArtist.id)
        val albumEntities = albumDao.getByArtistUrl(cleanUrl) +
            (if (cleanUrl != originalUrl) albumDao.getByArtistUrl(originalUrl) else emptyList())
        val albumMap = albumEntities.distinctBy { it.id }.associateBy { it.id }
        val orderedIds = cachedArtist.albumIdOrder?.let {
            try { orderJson.decodeFromString<List<String>>(it) } catch (_: Exception) { null }
        }
        val albums = if (orderedIds != null) {
            val ordered = orderedIds.mapNotNull { albumMap[it] }
            val remaining = albumMap.values.filter { it.id !in orderedIds.toSet() }
            (ordered + remaining).map { it.toDomain(emptyList(), false) }
        } else {
            albumMap.values.map { it.toDomain(emptyList(), false) }
        }
        return cachedArtist.toDomain(albums, isFavorite)
    }

    private suspend fun scrapeAndPersistArtist(
        cleanUrl: String,
        originalUrl: String,
        cachedArtist: com.dustvalve.next.android.data.local.db.entity.ArtistEntity?,
    ): Artist {
        val artist = artistScraper.scrapeArtist(cleanUrl)

        val previousAutoDownload = cachedArtist?.autoDownload ?: false
        val previousAlbumUrls = if (previousAutoDownload) {
            val existingAlbums = albumDao.getByArtistUrl(cleanUrl) +
                (if (cleanUrl != originalUrl) albumDao.getByArtistUrl(originalUrl) else emptyList())
            existingAlbums.distinctBy { it.id }.map { it.url }.toSet()
        } else {
            emptySet()
        }

        // Check if content actually changed
        val storedAlbumIds = cachedArtist?.albumIdOrder?.let {
            try { orderJson.decodeFromString<List<String>>(it) } catch (_: Exception) { null }
        }
        val scrapedAlbumIds = artist.albums.map { it.id }
        val contentChanged = storedAlbumIds == null || storedAlbumIds != scrapedAlbumIds

        val isFavorite = if (contentChanged) {
            database.withTransaction {
                artistDao.insert(artist.toEntity())
                for (album in artist.albums) {
                    albumDao.insertIfAbsent(album.toEntity())
                }
                if (previousAutoDownload) {
                    artistDao.setAutoDownload(artist.id, true)
                }
                favoriteDao.isFavorite(artist.id)
            }
        } else {
            // Content unchanged — just touch the timestamp
            val cachedId = cachedArtist.id
            artistDao.updateCachedAt(cachedId)
            favoriteDao.isFavorite(cachedId)
        }

        // Auto-download new albums if auto-download is enabled
        if (previousAutoDownload) {
            val newAlbums = artist.albums.filter { it.url !in previousAlbumUrls }
            for (albumStub in newAlbums) {
                try {
                    val fullAlbum = albumRepository.getAlbumDetail(albumStub.url)
                    downloadRepository.downloadAlbum(fullAlbum)
                } catch (e: Exception) {
                    if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                    // Best-effort auto-download
                }
            }
        }

        return artist.copy(isFavorite = isFavorite, autoDownload = previousAutoDownload)
    }

    private fun didArtistChange(
        cachedEntity: com.dustvalve.next.android.data.local.db.entity.ArtistEntity,
        freshArtist: Artist,
    ): Boolean {
        val storedAlbumIds = cachedEntity.albumIdOrder?.let {
            try { orderJson.decodeFromString<List<String>>(it) } catch (_: Exception) { null }
        }
        val freshAlbumIds = freshArtist.albums.map { it.id }
        return storedAlbumIds != freshAlbumIds
    }

    override suspend fun setAutoDownload(artistId: String, autoDownload: Boolean) {
        artistDao.setAutoDownload(artistId, autoDownload)
    }

    override suspend fun toggleFavorite(artistId: String) {
        database.withTransaction {
            val isFavorite = favoriteDao.isFavorite(artistId)
            if (isFavorite) {
                favoriteDao.delete(artistId)
            } else {
                favoriteDao.insert(FavoriteEntity(id = artistId, type = "artist"))
            }
        }
    }

    override suspend fun isFavorite(artistId: String): Boolean {
        return favoriteDao.isFavorite(artistId)
    }

    override fun getFavoriteArtists(): Flow<List<Artist>> {
        return artistDao.getFavoriteArtists().map { entities ->
            entities.map { it.toDomain(emptyList(), isFavorite = true) }
        }
    }

    override suspend fun getArtistMixTracks(albumIds: List<String>): List<Track> {
        if (albumIds.isEmpty()) return emptyList()
        val trackEntities = trackDao.getByAlbumIds(albumIds)
        if (trackEntities.isEmpty()) return emptyList()
        val allTrackIds = trackEntities.map { it.id }
        val favoriteIds = favoriteDao.getFavoriteIds(allTrackIds).toSet()
        return trackEntities.map { it.toDomain(it.id in favoriteIds) }
    }
}
