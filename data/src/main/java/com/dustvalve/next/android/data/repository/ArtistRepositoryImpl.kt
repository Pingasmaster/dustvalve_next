package com.dustvalve.next.android.data.repository

import androidx.room.withTransaction
import com.dustvalve.next.android.data.local.DatabaseGateway
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.AlbumDao
import com.dustvalve.next.android.data.local.db.dao.ArtistDao
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.dao.getByAlbumIds
import com.dustvalve.next.android.data.local.db.dao.getFavoriteIds
import com.dustvalve.next.android.data.local.db.entity.ArtistEntity
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.mapper.toDomain
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.data.network.OpportunisticRefreshGate
import com.dustvalve.next.android.data.network.UnmeteredRefreshGate
import com.dustvalve.next.android.data.remote.DustvalveArtistScraper
import com.dustvalve.next.android.data.util.orOnRemoteFailure
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.FavoriteType
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.AlbumRepository
import com.dustvalve.next.android.domain.repository.ArtistRepository
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.download.downloadEachDeferringFailures
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

private val orderJson = Json { ignoreUnknownKeys = true }

@Singleton
class ArtistRepositoryImpl(
    private val database: DustvalveNextDatabase,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val favoriteDao: FavoriteDao,
    private val trackDao: TrackDao,
    private val artistScraper: DustvalveArtistScraper,
    private val downloadRepository: DownloadRepository,
    private val albumRepository: AlbumRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val refreshGate: OpportunisticRefreshGate = OpportunisticRefreshGate.ALWAYS,
) : ArtistRepository {

    @Inject constructor(
        gateway: DatabaseGateway,
        artistScraper: DustvalveArtistScraper,
        downloadRepository: DownloadRepository,
        albumRepository: AlbumRepository,
        @Dispatcher(AppDispatchers.IO) ioDispatcher: CoroutineDispatcher,
        refreshGate: UnmeteredRefreshGate,
    ) : this(
        gateway.database,
        gateway.artistDao,
        gateway.albumDao,
        gateway.favoriteDao,
        gateway.trackDao,
        artistScraper,
        downloadRepository,
        albumRepository,
        ioDispatcher,
        refreshGate,
    )

    // Artists may grow their discography, so unlike fully-immutable album
    // metadata we emit cache first and revalidate in the background when
    // the network is unmetered. The cached copy is always shown immediately;
    // a fresh scrape that changes albumIdOrder or imageUrl rewrites the row
    // and re-emits. Empty scrapes (bot challenge / truncated HTML) never
    // wipe a populated cache. The artist-photo URL is content-addressed by
    // Bandcamp (the image id changes on re-upload), so a different imageUrl
    // is treated as a content change.

    override suspend fun getArtistDetail(url: String): Artist {
        val cleanUrl = url.substringBefore('?').substringBefore('#').trimEnd('/')

        val cachedArtist = artistDao.getByUrl(cleanUrl) ?: artistDao.getByUrl(url)
        if (cachedArtist != null) {
            if (!refreshGate.allowRefresh()) {
                return buildCachedArtist(cachedArtist, cleanUrl, url)
            }
            // Revalidate on unmetered; fall back to cache offline.
            return orOnRemoteFailure(buildCachedArtist(cachedArtist, cleanUrl, url)) {
                scrapeAndPersistArtist(cleanUrl, url, cachedArtist)
            }
        }

        return scrapeAndPersistArtist(cleanUrl, url, cachedArtist)
    }

    override fun getArtistDetailFlow(url: String): Flow<Artist> = flow {
        val cleanUrl = url.substringBefore('?').substringBefore('#').trimEnd('/')

        val cachedArtist = artistDao.getByUrl(cleanUrl) ?: artistDao.getByUrl(url)
        if (cachedArtist != null) {
            emit(buildCachedArtist(cachedArtist, cleanUrl, url))
            if (!refreshGate.allowRefresh()) return@flow
        }

        // Unmetered (or cache miss): scrape so newly published releases
        // rewrite the cache. Metered visits keep the snapshot they already
        // paid to download.
        try {
            val fresh = scrapeAndPersistArtist(cleanUrl, url, cachedArtist)
            if (cachedArtist == null || didArtistChange(cachedArtist, fresh)) {
                emit(fresh)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            if (cachedArtist == null) throw e
            // Cache already emitted - swallow network error for offline use
        } catch (e: SerializationException) {
            if (cachedArtist == null) throw e
        } catch (e: IllegalArgumentException) {
            if (cachedArtist == null) throw e
        }
    }.flowOn(ioDispatcher)

    private suspend fun buildCachedArtist(
        cachedArtist: com.dustvalve.next.android.data.local.db.entity.ArtistEntity,
        cleanUrl: String,
        originalUrl: String,
    ): Artist {
        val isFavorite = favoriteDao.isFavorite(cachedArtist.id, "artist")
        val albumEntities = albumDao.getByArtistUrl(cleanUrl) +
            (if (cleanUrl != originalUrl) albumDao.getByArtistUrl(originalUrl) else emptyList())
        val albumMap = albumEntities.distinctBy { it.id }.associateBy { it.id }
        val orderedIds = cachedArtist.albumIdOrder?.let {
            try {
                orderJson.decodeFromString<List<String>>(it)
            } catch (_: SerializationException) {
                null
            }
        }
        // albumIdOrder is the live discography snapshot. Do NOT append DB
        // rows missing from that list - insertIfAbsent leaves removed-release
        // stubs behind, and showing them made delisted albums stick forever.
        val albums = if (orderedIds != null) {
            orderedIds.mapNotNull { albumMap[it] }.map { it.toDomain(emptyList(), false) }
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

        // A zero-album scrape is almost always a bot wall or truncated HTML.
        // Never let that wipe a discography we already paid to download.
        if (artist.albums.isEmpty() && cachedArtist != null) {
            return buildCachedArtist(cachedArtist, cleanUrl, originalUrl)
        }

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
            try {
                orderJson.decodeFromString<List<String>>(it)
            } catch (_: Exception) {
                null
            }
        }
        val scrapedAlbumIds = artist.albums.map { it.id }
        val cachedImageUrl = cachedArtist?.imageUrl
        val contentChanged = storedAlbumIds == null ||
            storedAlbumIds != scrapedAlbumIds ||
            cachedImageUrl != artist.imageUrl

        // Always upsert the artist row so imageUrl heals even when the
        // discography ID list is unchanged; fill blank album art without
        // REPLACE-clobbering richer album rows.
        val isFavorite = database.withTransaction {
            artistDao.insert(artist.toEntity())
            for (album in artist.albums) {
                albumDao.insertIfAbsent(album.toEntity())
                if (album.artUrl.isNotBlank()) {
                    albumDao.fillBlankArtUrl(album.id, album.artUrl)
                }
            }
            if (previousAutoDownload) {
                artistDao.setAutoDownload(artist.id, true)
            }
            if (!contentChanged) {
                artistDao.updateCachedAt(artist.id)
            }
            favoriteDao.isFavorite(artist.id, "artist")
        }

        // Auto-download new albums if auto-download is enabled
        if (previousAutoDownload) {
            val newAlbums = artist.albums.filter { it.url !in previousAlbumUrls }
            // Best-effort auto-download: an album that fails is skipped at
            // once and retried after the others, and whatever still fails is
            // left for the next refresh to pick up.
            downloadEachDeferringFailures(newAlbums) { albumStub ->
                val fullAlbum = try {
                    albumRepository.getAlbumDetail(albumStub.url)
                } catch (e: SerializationException) {
                    // Not one of the types the runner absorbs; re-wrap so a
                    // scrape/parse failure is deferred-and-retried like any
                    // other rather than escaping into the caching path.
                    throw IOException("Album detail parse failed for ${albumStub.url}", e)
                }
                downloadRepository.downloadAlbum(fullAlbum)
            }
        }

        return artist.copy(isFavorite = isFavorite, autoDownload = previousAutoDownload)
    }

    private fun didArtistChange(cachedEntity: com.dustvalve.next.android.data.local.db.entity.ArtistEntity, freshArtist: Artist): Boolean {
        val storedAlbumIds = cachedEntity.albumIdOrder?.let {
            try {
                orderJson.decodeFromString<List<String>>(it)
            } catch (_: SerializationException) {
                null
            }
        }
        val freshAlbumIds = freshArtist.albums.map { it.id }
        return storedAlbumIds != freshAlbumIds || cachedEntity.imageUrl != freshArtist.imageUrl
    }

    override suspend fun setAutoDownload(artistId: String, autoDownload: Boolean) {
        artistDao.setAutoDownload(artistId, autoDownload)
    }

    override suspend fun toggleFavorite(artistId: String) {
        database.withTransaction {
            val isFavorite = favoriteDao.isFavorite(artistId, "artist")
            if (isFavorite) {
                favoriteDao.delete(artistId, "artist")
            } else {
                favoriteDao.insert(FavoriteEntity(id = artistId, type = "artist"))
            }
        }
    }

    override suspend fun isFavorite(artistId: String): Boolean = favoriteDao.isFavorite(artistId, "artist")

    override suspend fun cacheRemoteArtist(artist: Artist, source: String) {
        // Best-effort artist-row persist so library INNER JOINs on the artist
        // id resolve; failures are swallowed. Relocated verbatim from
        // ArtistDetailViewModel.persistYouTubeArtist (incl. the
        // catch(Throwable)). The insert is a REPLACE, so repeat calls refresh
        // the cached metadata.
        try {
            artistDao.insert(
                ArtistEntity(
                    id = artist.url,
                    name = artist.name,
                    url = artist.url,
                    imageUrl = artist.imageUrl,
                    bio = artist.bio,
                    location = artist.location,
                    source = source,
                ),
            )
        } catch (_: Throwable) { /* best-effort */ }
    }

    override suspend fun favoriteRemoteArtist(artist: Artist, source: String) {
        // Best-effort persist THEN the favorite - a failed artist-row insert
        // must not block the favorite itself. Deliberately NOT transactional,
        // matching the historical two-step sequence exactly.
        cacheRemoteArtist(artist, source)
        favoriteDao.insert(FavoriteEntity(id = artist.url, type = FavoriteType.ARTIST.key))
    }

    override suspend fun unfavoriteArtist(artistId: String) {
        favoriteDao.delete(artistId, "artist")
    }

    override suspend fun getArtistMixTracks(albumIds: List<String>): List<Track> {
        if (albumIds.isEmpty()) return emptyList()
        val trackEntities = trackDao.getByAlbumIds(albumIds)
        if (trackEntities.isEmpty()) return emptyList()
        val allTrackIds = trackEntities.map { it.id }
        val favoriteIds = favoriteDao.getFavoriteIds("track", allTrackIds).toSet()
        // Shuffled HERE rather than at the call site, because being a mix is
        // the entire point of this query. It used to return the DAO's
        // trackNumber order and the caller started at index 0, so every tap on
        // "Play mix" replayed the same first track of the same first album.
        return trackEntities.map { it.toDomain(it.id in favoriteIds) }.shuffled()
    }

    override suspend fun albumIdsMissingTracks(albumIds: List<String>): List<String> {
        if (albumIds.isEmpty()) return emptyList()
        val stocked = trackDao.getByAlbumIds(albumIds).mapTo(HashSet()) { it.albumId }
        return albumIds.filterNot { it in stocked }
    }
}
