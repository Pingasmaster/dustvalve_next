package com.dustvalve.next.android.data.storage.folder

import android.content.Context
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.AlbumDao
import com.dustvalve.next.android.data.local.db.dao.ArtistDao
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.PlaylistDao
import com.dustvalve.next.android.data.local.db.dao.RecentSearchDao
import com.dustvalve.next.android.data.local.db.dao.RecentTrackDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.dao.YouTubeMusicHomeCacheDao
import com.dustvalve.next.android.data.local.db.dao.YouTubePlaylistCacheDao
import com.dustvalve.next.android.data.local.db.dao.YouTubeVideoCacheDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rebuilds the app-internal Room + DataStore state from the JSON snapshots
 * inside the dedicated folder. Called once on cold start by MainActivity
 * before any UI is drawn.
 *
 * Overwrites user-data tables (playlists, favorites, tracks, albums, artists,
 * downloads, history) with the folder contents. The cache tables are only
 * rehydrated when the corresponding sub-toggle is on; otherwise we leave the
 * app-internal cache untouched so the user keeps whatever they warmed up
 * locally.
 */
@Singleton
class FolderRehydrator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val database: DustvalveNextDatabase,
    private val playlistDao: PlaylistDao,
    private val favoriteDao: FavoriteDao,
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val downloadDao: DownloadDao,
    private val recentTrackDao: RecentTrackDao,
    private val recentSearchDao: RecentSearchDao,
    private val ytVideoDao: YouTubeVideoCacheDao,
    private val ytPlaylistDao: YouTubePlaylistCacheDao,
    private val ytmHomeDao: YouTubeMusicHomeCacheDao,
) {
    suspend fun rehydrateAll() = withContext(Dispatchers.IO) {
        val uriStr = settingsDataStore.getDedicatedFolderTreeUriSync() ?: return@withContext
        val uri = try { uriStr.toUri() } catch (_: Exception) { return@withContext }

        // Settings first so downstream reads pick up the user's prefs.
        FolderIo.readJson(context, uri, DedicatedFolderPaths.FILE_SETTINGS, SettingsFile.serializer())?.let { file ->
            val map = file.entries.mapValues { (_, v) ->
                when (v) {
                    is SettingValue.BoolV -> v.value
                    is SettingValue.IntV -> v.value
                    is SettingValue.LongV -> v.value
                    is SettingValue.FloatV -> v.value
                    is SettingValue.StringV -> v.value
                    is SettingValue.StringSetV -> v.value
                }
            }
            settingsDataStore.restorePreferences(map)
        }

        val playlists = FolderIo.readJson(context, uri, DedicatedFolderPaths.FILE_PLAYLISTS, PlaylistsFile.serializer())
        val favorites = FolderIo.readJson(context, uri, DedicatedFolderPaths.FILE_FAVORITES, FavoritesFile.serializer())
        val tracks = FolderIo.readJson(context, uri, DedicatedFolderPaths.FILE_TRACKS, TracksFile.serializer())
        val albums = FolderIo.readJson(context, uri, DedicatedFolderPaths.FILE_ALBUMS, AlbumsFile.serializer())
        val artists = FolderIo.readJson(context, uri, DedicatedFolderPaths.FILE_ARTISTS, ArtistsFile.serializer())
        val downloads = FolderIo.readJson(context, uri, DedicatedFolderPaths.FILE_DOWNLOADS, DownloadsFile.serializer())
        val history = FolderIo.readJson(context, uri, DedicatedFolderPaths.FILE_HISTORY, HistoryFile.serializer())

        database.withTransaction {
            // Order of inserts matters due to FK constraints on
            // playlist_tracks(playlistId->playlists, trackId->tracks).
            if (tracks != null) {
                trackDao.deleteAll()
                if (tracks.tracks.isNotEmpty()) {
                    trackDao.insertAll(tracks.tracks.map { it.toEntity() })
                }
            }
            if (albums != null) {
                albumDao.deleteAll()
                for (a in albums.albums) albumDao.insert(a.toEntity())
            }
            if (artists != null) {
                artistDao.deleteAll()
                for (a in artists.artists) artistDao.insert(a.toEntity())
            }
            if (favorites != null) {
                favoriteDao.deleteAll()
                for (f in favorites.favorites) favoriteDao.insert(f.toEntity())
            }
            if (playlists != null) {
                playlistDao.deleteAllPlaylistTracks()
                playlistDao.deleteAllPlaylists()
                if (playlists.playlists.isNotEmpty()) {
                    playlistDao.insertPlaylists(playlists.playlists.map { it.toEntity() })
                }
                if (playlists.mappings.isNotEmpty()) {
                    // Drop mappings that reference unknown playlists or tracks
                    // so the FK on playlist_tracks doesn't abort the whole
                    // rehydrate transaction on a slightly-stale snapshot.
                    val knownPlaylistIds = playlists.playlists.map { it.id }.toSet()
                    val knownTrackIds = (tracks?.tracks?.map { it.id } ?: emptyList()).toSet()
                    val safe = playlists.mappings.filter {
                        it.playlistId in knownPlaylistIds && it.trackId in knownTrackIds
                    }
                    if (safe.isNotEmpty()) {
                        playlistDao.insertPlaylistTracks(safe.map { it.toEntity() })
                    }
                }
            }
            if (downloads != null) {
                downloadDao.deleteAll()
                for (d in downloads.downloads) downloadDao.insert(d.toEntity())
            }
            if (history != null) {
                recentTrackDao.deleteAll()
                recentSearchDao.deleteAll()
                for (t in history.tracks) recentTrackDao.insert(t.toEntity())
                for (s in history.searches) recentSearchDao.insert(s.toEntity())
            }
        }

        // Metadata cache — only if the user opted in to keep it in the folder.
        if (settingsDataStore.getDedicatedFolderIncludeMetadataCacheSync()) {
            val metadata = FolderIo.readJson(
                context,
                uri,
                "${DedicatedFolderPaths.CACHE_DIR}/${DedicatedFolderPaths.FILE_METADATA_CACHE}",
                MetadataCacheFile.serializer(),
            ) ?: readMetadataCacheFromCacheDir(uri)
            if (metadata != null) {
                database.withTransaction {
                    ytVideoDao.deleteAll()
                    ytPlaylistDao.deleteAll()
                    ytmHomeDao.deleteAll()
                    if (metadata.videos.isNotEmpty()) {
                        ytVideoDao.insertAll(metadata.videos.map { it.toEntity() })
                    }
                    for (p in metadata.playlists) ytPlaylistDao.insert(p.toEntity())
                    for (h in metadata.home) ytmHomeDao.insert(h.toEntity())
                }
            }
        }
    }

    private suspend fun readMetadataCacheFromCacheDir(
        uri: android.net.Uri,
    ): MetadataCacheFile? = withContext(Dispatchers.IO) {
        val file = DedicatedFolderPaths.findInCache(
            context, uri, DedicatedFolderPaths.FILE_METADATA_CACHE,
        ) ?: return@withContext null
        val text = context.contentResolver.openInputStream(file.uri)?.use {
            it.bufferedReader().readText()
        } ?: return@withContext null
        if (text.isBlank()) return@withContext null
        FolderSnapshotSerializer.json.decodeFromString(
            MetadataCacheFile.serializer(), text,
        )
    }
}

