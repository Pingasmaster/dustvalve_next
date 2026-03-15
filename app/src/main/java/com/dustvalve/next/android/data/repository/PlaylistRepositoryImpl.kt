package com.dustvalve.next.android.data.repository

import androidx.room.withTransaction
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.PlaylistDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.dao.getByAlbumIds
import com.dustvalve.next.android.data.local.db.dao.getFavoriteIds
import com.dustvalve.next.android.data.local.db.entity.PlaylistEntity
import com.dustvalve.next.android.data.local.db.entity.PlaylistTrackEntity
import com.dustvalve.next.android.data.mapper.toDomain
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val database: DustvalveNextDatabase,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val favoriteDao: FavoriteDao,
    private val downloadRepository: DownloadRepository,
) : PlaylistRepository {

    override fun getAllPlaylists(): Flow<List<Playlist>> {
        // Combine playlist entities with live track counts from source tables
        // so system playlist counts stay up-to-date without manual sync.
        return combine(
            playlistDao.getAllPlaylists(),
            trackDao.getFavorites().map { it.size },
            trackDao.getDownloaded().map { it.size },
            trackDao.getRecent().map { it.size },
        ) { entities, favCount, dlCount, recentCount ->
            entities.map { entity ->
                val liveCount = when (entity.id) {
                    Playlist.ID_FAVORITES -> favCount
                    Playlist.ID_DOWNLOADS -> dlCount
                    Playlist.ID_RECENT -> recentCount
                    else -> entity.trackCount
                }
                entity.toDomain().copy(trackCount = liveCount)
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getPlaylistById(playlistId: String): Flow<Playlist?> {
        return playlistDao.getPlaylistByIdFlow(playlistId).map { it?.toDomain() }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getPlaylistByIdSync(playlistId: String): Playlist? {
        return playlistDao.getPlaylistById(playlistId)?.toDomain()
    }

    override suspend fun createPlaylist(name: String, shapeKey: String?, iconUrl: String?): Playlist {
        val playlist = PlaylistEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            shapeKey = shapeKey,
            iconUrl = iconUrl,
            isSystem = false,
            isPinned = false,
            sortOrder = 0,
            trackCount = 0,
        )
        playlistDao.insertPlaylist(playlist)
        return playlist.toDomain()
    }

    override suspend fun renamePlaylist(playlistId: String, newName: String): Boolean {
        val updated = playlistDao.renamePlaylist(playlistId, newName)
        return updated > 0
    }

    override suspend fun updatePlaylistAppearance(playlistId: String, name: String, shapeKey: String?, iconUrl: String?): Boolean {
        val updated = playlistDao.updatePlaylistAppearance(playlistId, name, shapeKey, iconUrl)
        return updated > 0
    }

    override suspend fun deletePlaylist(playlistId: String): Boolean {
        val deleted = playlistDao.deletePlaylist(playlistId)
        return deleted > 0
    }

    override suspend fun pinPlaylist(playlistId: String, isPinned: Boolean): Boolean {
        val updated = playlistDao.setPlaylistPinned(playlistId, isPinned)
        return updated > 0
    }

    override suspend fun setAutoDownload(playlistId: String, autoDownload: Boolean) {
        playlistDao.setAutoDownload(playlistId, autoDownload)
    }

    override suspend fun ensureSystemPlaylistsExist() {
        val existingPlaylists = playlistDao.getAllPlaylists().first()
        val existingTypes = existingPlaylists.filter { it.isSystem }.mapNotNull { it.systemType }.toSet()

        val playlistsToCreate = Playlist.SystemPlaylistType.entries.filter { it.name !in existingTypes }

        playlistsToCreate.forEach { type ->
            val entity = PlaylistEntity(
                id = when (type) {
                    Playlist.SystemPlaylistType.DOWNLOADS -> Playlist.ID_DOWNLOADS
                    Playlist.SystemPlaylistType.RECENT -> Playlist.ID_RECENT
                    Playlist.SystemPlaylistType.COLLECTION -> Playlist.ID_COLLECTION
                    Playlist.SystemPlaylistType.FAVORITES -> Playlist.ID_FAVORITES
                },
                name = type.defaultName,
                isSystem = true,
                systemType = type.name,
                isPinned = false,
                sortOrder = when (type) {
                    Playlist.SystemPlaylistType.FAVORITES -> 0
                    Playlist.SystemPlaylistType.COLLECTION -> 1
                    Playlist.SystemPlaylistType.DOWNLOADS -> 2
                    Playlist.SystemPlaylistType.RECENT -> 3
                },
            )
            playlistDao.insertPlaylist(entity)
        }
    }

    override fun getSystemPlaylist(type: Playlist.SystemPlaylistType): Flow<Playlist?> {
        return playlistDao.getSystemPlaylistByTypeFlow(type.name).map { it?.toDomain() }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getSystemPlaylistSync(type: Playlist.SystemPlaylistType): Playlist? {
        return playlistDao.getSystemPlaylistByType(type.name)?.toDomain()
    }

    override fun getTracksInPlaylist(playlistId: String): Flow<List<Track>> {
        // For system playlists, read live from source tables so changes
        // (new favorite, new download, new play) appear instantly.
        return when (playlistId) {
            Playlist.ID_FAVORITES -> trackDao.getFavorites().map { tracks ->
                tracks.map { it.toDomain(isFavorite = true) }
            }
            Playlist.ID_DOWNLOADS -> trackDao.getDownloaded().map { tracks ->
                val trackIds = tracks.map { it.id }
                val favoriteIds = if (trackIds.isNotEmpty()) {
                    favoriteDao.getFavoriteIds(trackIds).toSet()
                } else {
                    emptySet()
                }
                tracks.map { it.toDomain(it.id in favoriteIds) }
            }
            Playlist.ID_RECENT -> trackDao.getRecent().map { tracks ->
                val trackIds = tracks.map { it.id }
                val favoriteIds = if (trackIds.isNotEmpty()) {
                    favoriteDao.getFavoriteIds(trackIds).toSet()
                } else {
                    emptySet()
                }
                tracks.map { it.toDomain(it.id in favoriteIds) }
            }
            else -> playlistDao.getTracksInPlaylist(playlistId).map { trackEntities ->
                val trackIds = trackEntities.map { it.id }
                val favoriteIds = if (trackIds.isNotEmpty()) {
                    favoriteDao.getFavoriteIds(trackIds).toSet()
                } else {
                    emptySet()
                }
                trackEntities.map { it.toDomain(it.id in favoriteIds) }
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getTracksInPlaylistSync(playlistId: String): List<Track> {
        val tracks = when (playlistId) {
            Playlist.ID_FAVORITES -> trackDao.getFavorites().first()
            Playlist.ID_DOWNLOADS -> trackDao.getDownloaded().first()
            Playlist.ID_RECENT -> trackDao.getRecent().first()
            else -> playlistDao.getTracksInPlaylistSync(playlistId)
        }
        if (playlistId == Playlist.ID_FAVORITES) {
            return tracks.map { it.toDomain(isFavorite = true) }
        }
        val trackIds = tracks.map { it.id }
        val favoriteIds = if (trackIds.isNotEmpty()) {
            favoriteDao.getFavoriteIds(trackIds).toSet()
        } else {
            emptySet()
        }
        return tracks.map { it.toDomain(it.id in favoriteIds) }
    }

    override suspend fun addTrackToPlaylist(playlistId: String, trackId: String) {
        val trackEntity = trackDao.getById(trackId) ?: return
        playlistDao.addTrackToPlaylist(playlistId, trackId)

        // Auto-download if playlist has autoDownload enabled
        val playlist = playlistDao.getPlaylistById(playlistId)
        if (playlist?.autoDownload == true && !downloadRepository.isTrackDownloaded(trackId)) {
            try {
                downloadRepository.downloadTrack(trackEntity.toDomain(false))
            } catch (_: Exception) {
                // Best-effort auto-download, ignore failures
            }
        }
    }

    override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<String>) {
        // Add all tracks to the playlist in a single transaction (DB only)
        val tracksToDownload = mutableListOf<Track>()
        database.withTransaction {
            val playlist = playlistDao.getPlaylistById(playlistId)
            trackIds.forEach { trackId ->
                val trackEntity = trackDao.getById(trackId) ?: return@forEach
                playlistDao.addTrackToPlaylist(playlistId, trackId)
                if (playlist?.autoDownload == true && !downloadRepository.isTrackDownloaded(trackId)) {
                    tracksToDownload.add(trackEntity.toDomain(false))
                }
            }
        }
        // Auto-download outside the transaction to avoid holding the DB lock
        tracksToDownload.forEach { track ->
            try {
                downloadRepository.downloadTrack(track)
            } catch (_: Exception) {
                // Best-effort auto-download, ignore failures
            }
        }
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        playlistDao.removeTrackFromPlaylistAndUpdateCount(playlistId, trackId)
    }

    override suspend fun moveTrackInPlaylist(playlistId: String, fromPosition: Int, toPosition: Int) {
        val tracks = playlistDao.getTracksInPlaylistSync(playlistId)
        if (fromPosition < 0 || fromPosition >= tracks.size) return
        if (toPosition < 0 || toPosition >= tracks.size) return

        val trackId = tracks[fromPosition].id
        playlistDao.reorderTrack(playlistId, trackId, fromPosition, toPosition)
    }

    override suspend fun isTrackInPlaylist(playlistId: String, trackId: String): Boolean {
        return playlistDao.isTrackInPlaylist(playlistId, trackId)
    }

    override fun getTrackIdsInUserPlaylists(): Flow<Set<String>> {
        return playlistDao.getTrackIdsInUserPlaylists().map { it.toSet() }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun syncDownloadsPlaylist() {
        val playlist = getSystemPlaylistSync(Playlist.SystemPlaylistType.DOWNLOADS) ?: return
        val downloadedTracks = trackDao.getDownloaded().first()

        database.withTransaction {
            playlistDao.clearPlaylistTracks(playlist.id)
            val playlistTracks = downloadedTracks.mapIndexed { index, trackEntity ->
                PlaylistTrackEntity(
                    playlistId = playlist.id,
                    trackId = trackEntity.id,
                    position = index,
                )
            }
            if (playlistTracks.isNotEmpty()) {
                playlistDao.insertPlaylistTracks(playlistTracks)
            }
            playlistDao.updateTrackCount(playlist.id, downloadedTracks.size)
        }
    }

    override suspend fun syncRecentPlaylist() {
        val playlist = getSystemPlaylistSync(Playlist.SystemPlaylistType.RECENT) ?: return
        val recentTracks = trackDao.getRecent().first()

        database.withTransaction {
            playlistDao.clearPlaylistTracks(playlist.id)
            val playlistTracks = recentTracks.mapIndexed { index, trackEntity ->
                PlaylistTrackEntity(
                    playlistId = playlist.id,
                    trackId = trackEntity.id,
                    position = index,
                )
            }
            if (playlistTracks.isNotEmpty()) {
                playlistDao.insertPlaylistTracks(playlistTracks)
            }
            playlistDao.updateTrackCount(playlist.id, recentTracks.size)
        }
    }

    override suspend fun syncCollectionPlaylist(collectionAlbumIds: List<String>) {
        val playlist = getSystemPlaylistSync(Playlist.SystemPlaylistType.COLLECTION) ?: return
        val collectionTracks = trackDao.getByAlbumIds(collectionAlbumIds)

        database.withTransaction {
            playlistDao.clearPlaylistTracks(playlist.id)
            val playlistTracks = collectionTracks.mapIndexed { index, trackEntity ->
                PlaylistTrackEntity(
                    playlistId = playlist.id,
                    trackId = trackEntity.id,
                    position = index,
                )
            }
            if (playlistTracks.isNotEmpty()) {
                playlistDao.insertPlaylistTracks(playlistTracks)
            }
            playlistDao.updateTrackCount(playlist.id, collectionTracks.size)
        }
    }

    override suspend fun syncFavoritesPlaylist() {
        val playlist = getSystemPlaylistSync(Playlist.SystemPlaylistType.FAVORITES) ?: return
        val favoriteTracks = trackDao.getFavorites().first()

        database.withTransaction {
            playlistDao.clearPlaylistTracks(playlist.id)
            val playlistTracks = favoriteTracks.mapIndexed { index, trackEntity ->
                PlaylistTrackEntity(
                    playlistId = playlist.id,
                    trackId = trackEntity.id,
                    position = index,
                )
            }
            if (playlistTracks.isNotEmpty()) {
                playlistDao.insertPlaylistTracks(playlistTracks)
            }
            playlistDao.updateTrackCount(playlist.id, favoriteTracks.size)
        }
    }
}
