package com.dustvalve.next.android.data.storage.folder

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Observes every canonical table plus DataStore and writes a matching JSON
 * snapshot into the dedicated folder whenever data changes. Each observer
 * has an independent 500 ms debounce so bursty writes flush once.
 *
 * The mirror is started lazily from `DustvalveNextApplication.onCreate` once,
 * and internally watches the main enabled flag — when off, every per-table
 * collector is cancelled; when on, they start fresh. The first emission
 * after a (re)start is dropped so we don't immediately re-flush data that
 * was just rehydrated from the folder.
 */
@OptIn(FlowPreview::class)
@Singleton
class FolderMirror @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJobs: MutableList<Job> = mutableListOf()
    private val metadataJobs: MutableList<Job> = mutableListOf()
    @Volatile private var suspendUntil: Long = 0L

    /**
     * Called from Application.onCreate. Watches the enabled flag; when true,
     * starts collectors for every table. When false, cancels them.
     */
    fun start() {
        scope.launch {
            settingsDataStore.dedicatedFolderEnabled
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) startMirrorJobs() else stopMirrorJobs()
                }
        }
    }

    /**
     * Call immediately before a rehydrate or migration so the observers
     * don't fight the copy in progress. Pauses all mirror writes for
     * [millis] ms regardless of the toggle state.
     */
    fun suspendFor(millis: Long) {
        suspendUntil = System.currentTimeMillis() + millis
    }

    private fun isSuspended() = System.currentTimeMillis() < suspendUntil

    private suspend fun treeUri(): Uri? {
        val uriStr = settingsDataStore.getDedicatedFolderTreeUriSync() ?: return null
        return try { uriStr.toUri() } catch (_: Exception) { null }
    }

    private fun <T> mirrorFlow(flow: Flow<T>, write: suspend (T, Uri) -> Unit): Job =
        flow
            .drop(1)
            .debounce(500)
            .onEach { value ->
                if (isSuspended()) return@onEach
                val uri = treeUri() ?: return@onEach
                try {
                    write(value, uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    android.util.Log.w("FolderMirror", "write failed: ${t.message}")
                }
            }
            .launchIn(scope)

    private fun startMirrorJobs() {
        stopMirrorJobs()
        val jobs = activeJobs

        // Playlists + mappings: both contribute to the single playlists.json,
        // so collapse them into one stream then write the combined snapshot.
        jobs += mirrorFlow(
            combine(
                playlistDao.getAllPlaylists(),
                playlistDao.getAllPlaylistTrackMappings(),
            ) { _, _ -> Unit }
        ) { _, _ -> writePlaylists() }

        jobs += mirrorFlow(favoriteDao.getAllFlow()) { list, uri ->
            FolderIo.writeJson(
                context, uri, DedicatedFolderPaths.FILE_FAVORITES,
                FavoritesFile.serializer(), FavoritesFile(list.map { it.toSnapshot() }),
            )
        }
        jobs += mirrorFlow(trackDao.getAllFlow()) { list, uri ->
            FolderIo.writeJson(
                context, uri, DedicatedFolderPaths.FILE_TRACKS,
                TracksFile.serializer(), TracksFile(list.map { it.toSnapshot() }),
            )
        }
        jobs += mirrorFlow(albumDao.getAllFlow()) { list, uri ->
            FolderIo.writeJson(
                context, uri, DedicatedFolderPaths.FILE_ALBUMS,
                AlbumsFile.serializer(), AlbumsFile(list.map { it.toSnapshot() }),
            )
        }
        jobs += mirrorFlow(artistDao.getAllFlow()) { list, uri ->
            FolderIo.writeJson(
                context, uri, DedicatedFolderPaths.FILE_ARTISTS,
                ArtistsFile.serializer(), ArtistsFile(list.map { it.toSnapshot() }),
            )
        }
        jobs += mirrorFlow(downloadDao.getAll()) { list, uri ->
            FolderIo.writeJson(
                context, uri, DedicatedFolderPaths.FILE_DOWNLOADS,
                DownloadsFile.serializer(), DownloadsFile(list.map { it.toSnapshot() }),
            )
        }
        jobs += mirrorFlow(
            combine(recentTrackDao.getAllFlow(), recentSearchDao.getAllFlow()) { t, s -> t to s }
        ) { pair, uri ->
            val (tracks, searches) = pair
            FolderIo.writeJson(
                context, uri, DedicatedFolderPaths.FILE_HISTORY,
                HistoryFile.serializer(),
                HistoryFile(tracks.map { it.toSnapshot() }, searches.map { it.toSnapshot() }),
            )
        }

        // Settings mirror — the raw preferences Flow.
        jobs += mirrorFlow(settingsDataStore.rawPreferencesFlow) { _, uri ->
            FolderIo.writeJson(
                context, uri, DedicatedFolderPaths.FILE_SETTINGS,
                SettingsFile.serializer(), captureSettingsFile(),
            )
        }

        // Metadata cache — observed only when the sub-toggle is on.
        jobs += scope.launch {
            settingsDataStore.dedicatedFolderIncludeMetadataCache
                .distinctUntilChanged()
                .collect { include ->
                    metadataJobs.forEach { it.cancel() }
                    metadataJobs.clear()
                    if (include) startMetadataJobs()
                }
        }
    }

    private fun startMetadataJobs() {
        metadataJobs += mirrorFlow(
            combine(
                ytVideoDao.getAllFlow(),
                ytPlaylistDao.getAllFlow(),
                ytmHomeDao.getAllFlow(),
            ) { _, _, _ -> Unit }
        ) { _, uri ->
            val file = MetadataCacheFile(
                videos = ytVideoDao.getAllSync().map { it.toSnapshot() },
                playlists = ytPlaylistDao.getAllSync().map { it.toSnapshot() },
                home = ytmHomeDao.getAllSync().map { it.toSnapshot() },
            )
            writeMetadataCache(uri, file)
        }
    }

    private fun stopMirrorJobs() {
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
        metadataJobs.forEach { it.cancel() }
        metadataJobs.clear()
    }

    private suspend fun writePlaylists() {
        val uri = treeUri() ?: return
        val playlists = playlistDao.getAllPlaylistsSync().map { it.toSnapshot() }
        val mappings = playlistDao.getAllPlaylistTrackMappingsSync().map { it.toSnapshot() }
        FolderIo.writeJson(
            context, uri, DedicatedFolderPaths.FILE_PLAYLISTS,
            PlaylistsFile.serializer(), PlaylistsFile(playlists, mappings),
        )
    }

    private suspend fun writeMetadataCache(treeUri: Uri, file: MetadataCacheFile) {
        val cache = DedicatedFolderPaths.cacheDir(context, treeUri) ?: return
        val text = FolderSnapshotSerializer.json.encodeToString(MetadataCacheFile.serializer(), file)
        val tmpName = "${DedicatedFolderPaths.FILE_METADATA_CACHE}.tmp"
        cache.findFile(tmpName)?.delete()
        val tmp = cache.createFile(DedicatedFolderPaths.JSON_MIME, tmpName) ?: return
        context.contentResolver.openOutputStream(tmp.uri, "wt")?.use {
            it.write(text.toByteArray(Charsets.UTF_8))
        } ?: return
        val renamed = try {
            tmp.renameTo(DedicatedFolderPaths.FILE_METADATA_CACHE)
        } catch (_: Exception) { false }
        if (!renamed) {
            val target = cache.findFile(DedicatedFolderPaths.FILE_METADATA_CACHE)
                ?: cache.createFile(DedicatedFolderPaths.JSON_MIME, DedicatedFolderPaths.FILE_METADATA_CACHE)
            if (target != null) {
                context.contentResolver.openOutputStream(target.uri, "wt")?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                }
            }
            tmp.delete()
        }
    }

    /** Produces a [SettingsFile] snapshot reflecting the current DataStore. */
    suspend fun captureSettingsFile(): SettingsFile {
        val raw = settingsDataStore.captureAllPreferences()
        val entries = raw.mapValues { (_, v) ->
            when (v) {
                is Boolean -> SettingValue.BoolV(v)
                is Int -> SettingValue.IntV(v)
                is Long -> SettingValue.LongV(v)
                is Float -> SettingValue.FloatV(v)
                is String -> SettingValue.StringV(v)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    SettingValue.StringSetV((v as Set<String>))
                }
                else -> SettingValue.StringV(v.toString())
            }
        }
        return SettingsFile(entries)
    }
}
