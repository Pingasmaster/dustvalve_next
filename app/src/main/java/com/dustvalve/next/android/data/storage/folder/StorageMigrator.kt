package com.dustvalve.next.android.data.storage.folder

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.dustvalve.next.android.data.asset.StoragePaths
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * One-shot transitions between app-internal storage and the dedicated
 * folder. Both directions are reported to the caller as
 * (progressFraction, statusMessage) pairs so the UI can render a
 * blocking loading overlay.
 *
 * `migrateToFolder`: serialize Room tables → JSON in the tree, copy every
 * audio file from `filesDir/downloads/` to `<tree>/dustvalve/downloads/`
 * via ContentResolver streams, repoint `DownloadEntity.filePath` to the new
 * `content://` URI, and delete the app-internal originals. Also writes
 * settings.json and the optional image/metadata caches.
 *
 * `migrateFromFolder`: reverse direction; after success the `dustvalve/`
 * subdir is deleted from the tree and the persistable permission is released.
 */
@Singleton
class StorageMigrator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val database: DustvalveNextDatabase,
    private val downloadDao: DownloadDao,
    private val mirror: FolderMirror,
) {
    data class Progress(val fraction: Float, val label: String)

    suspend fun migrateToFolder(
        treeUriStr: String,
        includeImages: Boolean,
        includeMetadata: Boolean,
        onProgress: (Progress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val treeUri = treeUriStr.toUri()
        onProgress(Progress(0f, "Preparing folder"))
        DedicatedFolderPaths.dustvalveRoot(context, treeUri)
            ?: throw IOException("Dedicated folder not accessible")

        // 1) Flush DB tables to JSON snapshots.
        onProgress(Progress(0.05f, "Saving playlists"))
        writeSnapshotsToFolder(treeUri, includeMetadata)

        // 2) Copy audio files app-internal → tree and repoint DownloadEntity rows.
        val downloads = downloadDao.getAllSync()
        val total = downloads.size.coerceAtLeast(1)
        for ((idx, d) in downloads.withIndex()) {
            coroutineContext.ensureActive()
            onProgress(
                Progress(
                    0.2f + 0.7f * (idx.toFloat() / total.toFloat()),
                    "Copying ${idx + 1} / $total",
                )
            )
            val src = File(d.filePath)
            if (!src.exists()) continue
            val ext = src.extension.ifBlank { "bin" }
            val albumDir = DedicatedFolderPaths.downloadsDir(context, treeUri)
                ?.let { root -> root.findFile(d.albumId) ?: root.createDirectory(d.albumId) }
                ?: continue
            val fileName = "${d.trackId}.$ext"
            albumDir.findFile(fileName)?.delete()
            val newFile = albumDir.createFile(mimeFor(ext), fileName) ?: continue
            context.contentResolver.openOutputStream(newFile.uri, "wt")?.use { out ->
                src.inputStream().use { it.copyTo(out, 8192) }
            } ?: continue
            downloadDao.insert(d.copy(filePath = newFile.uri.toString()))
            try { src.delete() } catch (_: Exception) {}
        }

        // 3) Optional extras.
        if (includeImages) {
            onProgress(Progress(0.92f, "Copying image cache"))
            copyDirectoryToFolder(StoragePaths.imagesDir(context), imageCacheRoot(treeUri))
        }

        // 4) Mark toggle on. Mirror will become active for future edits.
        onProgress(Progress(0.97f, "Finalizing"))
        settingsDataStore.setDedicatedFolder(enabled = true, treeUri = treeUriStr)
        if (includeImages) settingsDataStore.setDedicatedFolderIncludeImageCache(true)
        if (includeMetadata) settingsDataStore.setDedicatedFolderIncludeMetadataCache(true)

        // Re-emit the snapshots AFTER flipping the flag so the mirror has
        // the canonical files pointing at the new folder.
        writeSnapshotsToFolder(treeUri, includeMetadata)
        onProgress(Progress(1f, "Done"))
    }

    suspend fun migrateFromFolder(
        onProgress: (Progress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        onProgress(Progress(0f, "Preparing"))
        val treeUriStr = settingsDataStore.getDedicatedFolderTreeUriSync()
        val treeUri = treeUriStr?.toUri()

        // 1) Copy audio files tree → app-internal and repoint DownloadEntity rows.
        val downloads = downloadDao.getAllSync()
        val total = downloads.size.coerceAtLeast(1)
        for ((idx, d) in downloads.withIndex()) {
            coroutineContext.ensureActive()
            onProgress(
                Progress(
                    0.1f + 0.7f * (idx.toFloat() / total.toFloat()),
                    "Copying ${idx + 1} / $total",
                )
            )
            val srcPath = d.filePath
            if (!srcPath.startsWith("content://")) continue // already local
            val srcUri = try { srcPath.toUri() } catch (_: Exception) { continue }
            val targetDir = File(StoragePaths.downloadsDir(context), d.albumId).also { it.mkdirs() }
            val fileName = srcUri.lastPathSegment?.substringAfterLast('/') ?: "${d.trackId}.bin"
            val targetFile = File(targetDir, fileName)
            context.contentResolver.openInputStream(srcUri)?.use { input ->
                targetFile.outputStream().use { input.copyTo(it, 8192) }
            } ?: continue
            downloadDao.insert(d.copy(filePath = targetFile.absolutePath))
        }

        // 2) Turn off the toggle BEFORE we nuke the folder, so any
        //    in-flight mirror calls become no-ops.
        onProgress(Progress(0.85f, "Finalizing"))
        settingsDataStore.setDedicatedFolder(enabled = false, treeUri = null)
        settingsDataStore.setDedicatedFolderIncludeImageCache(false)
        settingsDataStore.setDedicatedFolderIncludeMetadataCache(false)

        // 3) Delete the `dustvalve/` subdir in the tree and release the grant.
        if (treeUri != null) {
            try {
                DedicatedFolderPaths.treeRoot(context, treeUri)
                    ?.findFile(DedicatedFolderPaths.ROOT_DIR)
                    ?.delete()
            } catch (_: Exception) {}
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.releasePersistableUriPermission(treeUri, flags)
            } catch (_: Exception) {}
        }
        onProgress(Progress(1f, "Done"))
    }

    /** Sub-toggle: toggle the image cache mirror on/off without touching the main toggle. */
    suspend fun setIncludeImageCache(include: Boolean) = withContext(Dispatchers.IO) {
        settingsDataStore.setDedicatedFolderIncludeImageCache(include)
        val treeUri = settingsDataStore.getDedicatedFolderTreeUriSync()?.toUri() ?: return@withContext
        if (include) {
            copyDirectoryToFolder(StoragePaths.imagesDir(context), imageCacheRoot(treeUri))
        } else {
            DedicatedFolderPaths.cacheDir(context, treeUri)
                ?.findFile(DedicatedFolderPaths.IMAGES_DIR)?.delete()
        }
    }

    /** Sub-toggle: toggle metadata cache mirror on/off. */
    suspend fun setIncludeMetadataCache(include: Boolean) = withContext(Dispatchers.IO) {
        settingsDataStore.setDedicatedFolderIncludeMetadataCache(include)
        // Enabling causes FolderMirror to start its metadata-cache observer,
        // which writes cache/metadata.json on the next DB change. Disabling
        // cancels the observer and we clean up the stale JSON here.
        val treeUri = settingsDataStore.getDedicatedFolderTreeUriSync()?.toUri() ?: return@withContext
        if (!include) {
            DedicatedFolderPaths.findInCache(context, treeUri, DedicatedFolderPaths.FILE_METADATA_CACHE)?.delete()
        }
    }

    private fun imageCacheRoot(treeUri: Uri): DocumentFile? =
        DedicatedFolderPaths.imageCacheDir(context, treeUri)

    private fun copyDirectoryToFolder(src: File, dst: DocumentFile?) {
        if (dst == null || !src.exists() || !src.isDirectory) return
        for (child in src.listFiles() ?: return) {
            if (child.isDirectory) continue
            val name = child.name
            dst.findFile(name)?.delete()
            val file = dst.createFile("application/octet-stream", name) ?: continue
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
                child.inputStream().use { it.copyTo(out, 8192) }
            }
        }
    }

    private suspend fun writeSnapshotsToFolder(treeUri: Uri, includeMetadata: Boolean) {
        // Directly compute + write snapshots bypassing the debounced mirror.
        val settingsFile = mirror.captureSettingsFile()
        FolderIo.writeJson(
            context, treeUri, DedicatedFolderPaths.FILE_SETTINGS,
            SettingsFile.serializer(), settingsFile,
        )
        val snap = folderSnapshotFromDb()
        FolderIo.writeJson(context, treeUri, DedicatedFolderPaths.FILE_PLAYLISTS, PlaylistsFile.serializer(), snap.playlists)
        FolderIo.writeJson(context, treeUri, DedicatedFolderPaths.FILE_FAVORITES, FavoritesFile.serializer(), snap.favorites)
        FolderIo.writeJson(context, treeUri, DedicatedFolderPaths.FILE_TRACKS, TracksFile.serializer(), snap.tracks)
        FolderIo.writeJson(context, treeUri, DedicatedFolderPaths.FILE_ALBUMS, AlbumsFile.serializer(), snap.albums)
        FolderIo.writeJson(context, treeUri, DedicatedFolderPaths.FILE_ARTISTS, ArtistsFile.serializer(), snap.artists)
        FolderIo.writeJson(context, treeUri, DedicatedFolderPaths.FILE_DOWNLOADS, DownloadsFile.serializer(), snap.downloads)
        FolderIo.writeJson(context, treeUri, DedicatedFolderPaths.FILE_HISTORY, HistoryFile.serializer(), snap.history)
        if (includeMetadata && snap.metadata != null) {
            val cache = DedicatedFolderPaths.cacheDir(context, treeUri) ?: return
            val tmpName = "${DedicatedFolderPaths.FILE_METADATA_CACHE}.tmp"
            cache.findFile(tmpName)?.delete()
            val tmp = cache.createFile(DedicatedFolderPaths.JSON_MIME, tmpName) ?: return
            val text = FolderSnapshotSerializer.json.encodeToString(
                MetadataCacheFile.serializer(), snap.metadata,
            )
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
    }

    private data class DbSnapshot(
        val playlists: PlaylistsFile,
        val favorites: FavoritesFile,
        val tracks: TracksFile,
        val albums: AlbumsFile,
        val artists: ArtistsFile,
        val downloads: DownloadsFile,
        val history: HistoryFile,
        val metadata: MetadataCacheFile?,
    )

    private suspend fun folderSnapshotFromDb(): DbSnapshot {
        val playlistDao = database.playlistDao()
        val favoriteDao = database.favoriteDao()
        val trackDao = database.trackDao()
        val albumDao = database.albumDao()
        val artistDao = database.artistDao()
        val recentTrackDao = database.recentTrackDao()
        val recentSearchDao = database.recentSearchDao()
        val ytVideoDao = database.youtubeVideoCacheDao()
        val ytPlaylistDao = database.youtubePlaylistCacheDao()
        val ytmHomeDao = database.youtubeMusicHomeCacheDao()

        return DbSnapshot(
            playlists = PlaylistsFile(
                playlists = playlistDao.getAllPlaylistsSync().map { it.toSnapshot() },
                mappings = playlistDao.getAllPlaylistTrackMappingsSync().map { it.toSnapshot() },
            ),
            favorites = FavoritesFile(favoriteDao.getAllSync().map { it.toSnapshot() }),
            tracks = TracksFile(trackDao.getAllSync().map { it.toSnapshot() }),
            albums = AlbumsFile(albumDao.getAll().map { it.toSnapshot() }),
            artists = ArtistsFile(artistDao.getAllSync().map { it.toSnapshot() }),
            downloads = DownloadsFile(downloadDao.getAllSync().map { it.toSnapshot() }),
            history = HistoryFile(
                tracks = recentTrackDao.getAllSync().map { it.toSnapshot() },
                searches = recentSearchDao.getAllSync().map { it.toSnapshot() },
            ),
            metadata = MetadataCacheFile(
                videos = ytVideoDao.getAllSync().map { it.toSnapshot() },
                playlists = ytPlaylistDao.getAllSync().map { it.toSnapshot() },
                home = ytmHomeDao.getAllSync().map { it.toSnapshot() },
            ),
        )
    }

    private fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "flac" -> "audio/flac"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "opus" -> "audio/opus"
        "webm" -> "audio/webm"
        "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }
}
