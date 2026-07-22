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
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FolderRehydrator"

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
    @param:Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun rehydrateAll() = withContext(ioDispatcher) {
        val uriStr = settingsDataStore.getDedicatedFolderTreeUriSync() ?: return@withContext
        val uri = try {
            uriStr.toUri()
        } catch (_: Exception) {
            return@withContext
        }

        // Settings first so downstream reads pick up the user's prefs.
        readJsonOrQuarantine(uri, DedicatedFolderPaths.FILE_SETTINGS, SettingsFile.serializer())?.let { file ->
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

        val playlists = readJsonOrQuarantine(uri, DedicatedFolderPaths.FILE_PLAYLISTS, PlaylistsFile.serializer())
        val favorites = readJsonOrQuarantine(uri, DedicatedFolderPaths.FILE_FAVORITES, FavoritesFile.serializer())
        val tracks = readJsonOrQuarantine(uri, DedicatedFolderPaths.FILE_TRACKS, TracksFile.serializer())
        val albums = readJsonOrQuarantine(uri, DedicatedFolderPaths.FILE_ALBUMS, AlbumsFile.serializer())
        val artists = readJsonOrQuarantine(uri, DedicatedFolderPaths.FILE_ARTISTS, ArtistsFile.serializer())
        val downloads = readJsonOrQuarantine(uri, DedicatedFolderPaths.FILE_DOWNLOADS, DownloadsFile.serializer())
        val history = readJsonOrQuarantine(uri, DedicatedFolderPaths.FILE_HISTORY, HistoryFile.serializer())

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
                    // Validate against the track set that will actually be in
                    // the DB after this transaction: the snapshot's tracks if
                    // tracks.json was rehydrated, otherwise the UNTOUCHED
                    // current DB rows. The old code used emptySet() when
                    // tracks.json was missing/unreadable, which dropped every
                    // playlist-track mapping even though the referenced tracks
                    // still existed - emptying all user playlists.
                    val knownTrackIds = tracks?.tracks?.map { it.id }?.toSet()
                        ?: trackDao.getAllIdsSync().toSet()
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

        // Metadata cache - only if the user opted in to keep it in the folder.
        if (settingsDataStore.getDedicatedFolderIncludeMetadataCacheSync()) {
            val metadata = try {
                FolderIo.readJson(
                    context,
                    uri,
                    "${DedicatedFolderPaths.CACHE_DIR}/${DedicatedFolderPaths.FILE_METADATA_CACHE}",
                    MetadataCacheFile.serializer(),
                    ioDispatcher,
                ) ?: readMetadataCacheFromCacheDir(uri)
            } catch (e: SerializationException) {
                // Corrupt cache snapshot: quarantine + skip, keep the local
                // cache. Never fail the whole boot over a cache file.
                android.util.Log.w(TAG, "Corrupt ${DedicatedFolderPaths.FILE_METADATA_CACHE}, skipping: ${e.message}")
                try {
                    DedicatedFolderPaths.findInCache(context, uri, DedicatedFolderPaths.FILE_METADATA_CACHE)
                        ?.renameTo("${DedicatedFolderPaths.FILE_METADATA_CACHE}.corrupt")
                } catch (qe: Exception) {
                    if (qe is kotlin.coroutines.cancellation.CancellationException) throw qe
                }
                null
            }
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

    /**
     * Reads `dustvalve/[name]`, treating a corrupt (undecodable) file exactly
     * like an absent one: the matching table keeps its current app-internal
     * rows and boot continues instead of surfacing the blocking folder-error
     * screen. The corrupt file is quarantined to "[name].corrupt" so the
     * mirror's next flush writes a fresh snapshot.
     */
    private suspend fun <T> readJsonOrQuarantine(uri: android.net.Uri, name: String, serializer: KSerializer<T>): T? = try {
        FolderIo.readJson(context, uri, name, serializer, ioDispatcher)
    } catch (e: SerializationException) {
        android.util.Log.w(TAG, "Corrupt $name, quarantining: ${e.message}")
        FolderIo.quarantine(context, uri, name, ioDispatcher)
        null
    }

    private suspend fun readMetadataCacheFromCacheDir(uri: android.net.Uri): MetadataCacheFile? = withContext(ioDispatcher) {
        val file = DedicatedFolderPaths.findInCache(
            context,
            uri,
            DedicatedFolderPaths.FILE_METADATA_CACHE,
        ) ?: return@withContext null
        val text = context.contentResolver.openInputStream(file.uri)?.use {
            it.bufferedReader().readText()
        } ?: return@withContext null
        if (text.isBlank()) return@withContext null
        FolderSnapshotSerializer.json.decodeFromString(
            MetadataCacheFile.serializer(),
            text,
        )
    }
}
