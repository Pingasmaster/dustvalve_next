package com.dustvalve.next.android.data.repository

import android.content.Context
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.dustvalve.next.android.cache.StorageTracker
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.AlbumDao
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.DownloadEntity
import com.dustvalve.next.android.data.mapper.toDomain
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.data.remote.DustvalveDownloadScraper
import com.dustvalve.next.android.data.remote.RangeResumeDownloader
import com.dustvalve.next.android.data.storage.folder.DedicatedFolderPaths
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import com.dustvalve.next.android.di.qualifiers.MediaHttp
import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.PurchaseInfo
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.DownloadInfo
import com.dustvalve.next.android.domain.repository.DownloadProgressReporter
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.MediaCacheClearer
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import com.dustvalve.next.android.download.isPauseCancellation
import com.dustvalve.next.android.util.NetworkUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val database: DustvalveNextDatabase,
    private val downloadDao: DownloadDao,
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    // MediaHttp: no callTimeout - a track download on a slow connection
    // legitimately outlives the base client's 30s whole-call cap.
    @param:MediaHttp private val client: OkHttpClient,
    private val storageTracker: StorageTracker,
    private val downloadScraper: DustvalveDownloadScraper,
    private val settingsDataStore: SettingsDataStore,
    private val youtubeRepository: YouTubeRepository,
    private val notificationCenter: DownloadProgressReporter,
    private val mediaCacheClearer: MediaCacheClearer,
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : DownloadRepository {

    /**
     * Sidecar persisted next to a partial `.tmp` file. On resume the recorded
     * total size and source identity must still match the freshly re-resolved
     * download URL - YouTube re-resolution can hand back a *different* stream
     * variant for the same track, and appending its bytes onto a partial from
     * another variant splices a corrupt file that still passes the size check.
     */
    @Serializable
    internal data class ResumeMeta(val expectedTotalBytes: Long, val sourceIdentity: String)

    private val metaJson = Json { ignoreUnknownKeys = true }

    /**
     * Per-track single-flight guard. Several callers reach [downloadTrack]
     * concurrently (playlist/album repositories, playlist transfer, the
     * auto-download coordinator's serial queue); two writers appending into
     * the same `.tmp` interleave into a corrupt file. Entries are
     * ref-counted so the map cannot leak a mutex another waiter still holds.
     */
    private class TrackLock {
        val mutex = Mutex()
        var refs = 0
    }

    private val trackLocks = HashMap<String, TrackLock>()

    private suspend fun <T> withTrackLock(trackId: String, block: suspend () -> T): T {
        val lock = synchronized(trackLocks) {
            trackLocks.getOrPut(trackId) { TrackLock() }.also { it.refs++ }
        }
        try {
            return lock.mutex.withLock { block() }
        } finally {
            synchronized(trackLocks) {
                lock.refs--
                if (lock.refs == 0) trackLocks.remove(trackId)
            }
        }
    }

    override suspend fun downloadAlbum(album: Album) {
        if (album.tracks.isEmpty()) {
            throw IOException("No tracks to download - album has an empty track list")
        }
        notificationCenter.withBatch(
            label = album.title,
            totalTracks = album.tracks.count { it.streamUrl != null },
            kind = DownloadProgressReporter.BatchKind.ALBUM,
        ) {
            downloadAlbumInner(album)
        }
    }

    @Suppress("ThrowsCount")
    private suspend fun downloadAlbumInner(album: Album) {
        val errors = mutableListOf<Exception>()
        var skipped = 0
        for (track in album.tracks) {
            try {
                if (track.streamUrl == null) {
                    skipped++
                    continue
                }
                downloadTrack(track)
            } catch (e: CancellationException) {
                if (errors.isNotEmpty()) {
                    android.util.Log.w("DownloadRepo", "${errors.size} track(s) failed before cancellation: ${errors.first().message}")
                }
                throw e // Respect structured concurrency
            } catch (e: Exception) {
                errors.add(e)
            }
        }
        if (skipped == album.tracks.size) {
            throw IOException("No tracks available for download - all ${album.tracks.size} tracks lack stream URLs")
        }
        if (errors.isNotEmpty()) {
            val skippedMsg = if (skipped > 0) " ($skipped tracks unavailable for streaming)" else ""
            throw IOException(
                "Failed to download ${errors.size} of ${album.tracks.size} tracks$skippedMsg: ${errors.first().message}",
            )
        }
        if (skipped > 0) {
            android.util.Log.w(
                "DownloadRepo",
                "Downloaded ${album.tracks.size - skipped} of ${album.tracks.size} tracks; $skipped tracks lacked stream URLs",
            )
        }
    }

    override suspend fun downloadTrack(track: Track, formatOverride: AudioFormat?) = withContext(ioDispatcher) {
        // Serialize concurrent calls for the same track. The loser of the race
        // waits, then short-circuits via the existing same-or-higher-quality
        // check in downloadTrackInner once the winner has committed its file.
        withTrackLock(track.id) {
            notificationCenter.trackStarted(track.id, track.title)
            var success = false
            try {
                downloadTrackInner(track, formatOverride)
                success = true
            } finally {
                notificationCenter.trackFinished(track.id, success)
            }
        }
    }

    @Suppress("ThrowsCount")
    private suspend fun downloadTrackInner(track: Track, formatOverride: AudioFormat?) {
        // Resolve purchase info for HQ download
        val purchaseInfo = resolvePurchaseInfo(track)

        val preferredFormatKey = settingsDataStore.getDownloadFormatSync()
        val preferredFormat = formatOverride ?: AudioFormat.fromKey(preferredFormatKey) ?: AudioFormat.FLAC

        // Three sources, in order: HQ for purchased content (with mp3-128
        // fallback), YouTube watch-page -> resolved audio stream, otherwise
        // the raw streamUrl as mp3-128.
        val (downloadUrl, format) = if (purchaseInfo != null) {
            resolveHqDownloadUrl(purchaseInfo, preferredFormat)
                ?: (track.streamUrl to AudioFormat.MP3_128)
        } else if (track.source == TrackSource.YOUTUBE) {
            // YouTube tracks store watch page URL in streamUrl; resolve actual audio stream.
            // Queue tracks may have resolved googlevideo.com URLs - reconstruct the watch URL.
            val streamUrl = track.streamUrl
                ?: throw IOException("Track '${track.title}' has no video URL")
            val videoUrl = if (streamUrl.contains("youtube.com") || streamUrl.contains("youtu.be")) {
                streamUrl
            } else {
                val videoId = track.id.removePrefix("yt_")
                "https://www.youtube.com/watch?v=$videoId"
            }
            youtubeRepository.getDownloadableStream(videoUrl)
        } else {
            (track.streamUrl to AudioFormat.MP3_128)
        }

        if (downloadUrl == null) {
            throw IOException("Track '${track.title}' has no download or stream URL available")
        }

        // Skip re-download if existing download is same or higher quality
        val existingDownload = downloadDao.getByTrackId(track.id)
        if (existingDownload != null) {
            val existingFormat = AudioFormat.fromKey(existingDownload.format)
            if (existingFormat != null &&
                existingFormat.qualityRank >= format.qualityRank &&
                downloadPathExists(existingDownload.filePath)
            ) {
                return
            }
            // Quality upgrade: the old file is deleted only AFTER the
            // replacement is fully committed (below). Deleting it up front
            // left a phantom "downloaded" row pointing at nothing whenever
            // the new download failed.
        }

        val safeAlbumId = NetworkUtils.sanitizeFileName(track.albumId)
        val safeTrackId = NetworkUtils.sanitizeFileName(track.id)
        val fileName = "$safeTrackId.${format.extension}"

        if (!downloadUrl.startsWith("https://")) {
            throw IOException("Download URL must use HTTPS: ${downloadUrl.take(50)}")
        }

        // Branch: SAF folder mode vs. app-internal mode. Internal writes go
        // via a temp sibling and atomic rename; SAF deletes any pre-existing
        // target and writes directly to the DocumentFile.
        val (finalPath, fileSize) = if (settingsDataStore.getDedicatedFolderEnabledSync()) {
            writeDownloadToFolder(safeAlbumId, fileName, format, downloadUrl, track.id)
        } else {
            writeDownloadToInternal(safeAlbumId, fileName, downloadUrl, track.id)
        }

        // Atomically insert the track row + the unified-pool download record.
        database.withTransaction {
            if (trackDao.getById(track.id) == null) {
                trackDao.insertAll(listOf(track.toEntity()))
            }

            downloadDao.insert(
                DownloadEntity(
                    trackId = track.id,
                    albumId = track.albumId,
                    filePath = finalPath,
                    sizeBytes = fileSize,
                    format = format.key,
                    pinned = true,
                ),
            )
        }

        // Now that the replacement is committed and the row updated, drop the
        // superseded lower-quality file. Same-path replacements (e.g. mp3-128
        // -> mp3-320 share the .mp3 name) were overwritten by the rename above.
        if (existingDownload != null && existingDownload.filePath != finalPath) {
            deleteByPath(existingDownload.filePath)
        }

        storageTracker.notifyChanged()
    }

    private suspend fun writeDownloadToInternal(
        safeAlbumId: String,
        fileName: String,
        downloadUrl: String,
        trackId: String,
    ): Pair<String, Long> {
        val downloadDir = File(context.filesDir, "downloads/$safeAlbumId")
        if (!downloadDir.mkdirs() && !downloadDir.exists()) {
            throw IOException("Failed to create download directory: ${downloadDir.absolutePath}")
        }
        val targetFile = File(downloadDir, fileName)
        val tempFile = File(downloadDir, "$fileName.tmp")
        val metaFile = File(downloadDir, "$fileName.tmp.meta")
        val identity = resumeSourceIdentity(downloadUrl)

        // A leftover .tmp means a prior transfer was paused - resume from its
        // current length via an HTTP Range request (append mode) instead of
        // restarting from 0. Only append when the sidecar proves the partial
        // came from the same source variant; a freshly re-resolved URL (e.g.
        // a different YouTube itag) must restart from zero.
        var resumeFrom = if (tempFile.exists()) tempFile.length() else 0L
        var knownTotal: Long? = null
        if (resumeFrom > 0L) {
            val meta = readResumeMeta(metaFile)
            if (meta == null || meta.sourceIdentity != identity) {
                tempFile.delete()
                metaFile.delete()
                resumeFrom = 0L
            } else {
                knownTotal = meta.expectedTotalBytes
            }
        }

        suspend fun transfer(offset: Long, expectedTotal: Long?) {
            var metaPersisted = false
            FileOutputStream(tempFile, offset > 0L).use { out ->
                RangeResumeDownloader.stream(
                    client = downloadClient,
                    url = downloadUrl,
                    sink = out,
                    trackId = trackId,
                    startOffset = offset,
                    expectedTotalBytes = expectedTotal,
                    onProgress = { written, total ->
                        if (!metaPersisted && total != null && total > 0L) {
                            metaPersisted = true
                            writeResumeMeta(metaFile, ResumeMeta(total, identity))
                        }
                        notificationCenter.trackProgress(trackId, written, total)
                    },
                )
            }
        }

        try {
            try {
                transfer(resumeFrom, knownTotal)
            } catch (e: RangeResumeDownloader.ResumeMismatchException) {
                // The server no longer serves the payload the partial came
                // from (offset or total drifted). Discard and restart clean -
                // once; a second mismatch propagates as a real failure.
                android.util.Log.w(
                    "DownloadRepo",
                    "Resume mismatch for $trackId; discarding partial and restarting from zero: ${e.message}",
                )
                tempFile.delete()
                metaFile.delete()
                transfer(0L, null)
            }
        } catch (e: Exception) {
            // Keep the partial (and its sidecar) on pause so resume can
            // continue; delete both on any real failure or cancel.
            if (!e.isPauseCancellation()) {
                tempFile.delete()
                metaFile.delete()
            }
            throw e
        }

        metaFile.delete()
        if (!tempFile.renameTo(targetFile)) {
            try {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            } catch (e: Exception) {
                targetFile.delete()
                tempFile.delete()
                throw IOException("Failed to copy download to target: ${e.message}")
            }
        }
        if (!targetFile.exists() || targetFile.length() == 0L) {
            throw IOException("Failed to write download file: ${targetFile.absolutePath}")
        }
        return targetFile.absolutePath to targetFile.length()
    }

    private suspend fun writeDownloadToFolder(
        safeAlbumId: String,
        fileName: String,
        format: AudioFormat,
        downloadUrl: String,
        trackId: String,
    ): Pair<String, Long> {
        val treeUriStr = settingsDataStore.getDedicatedFolderTreeUriSync()
            ?: throw IOException("Dedicated folder URI missing")
        val treeUri = treeUriStr.toUri()
        val downloadsRoot = DedicatedFolderPaths.downloadsDir(context, treeUri)
            ?: throw IOException("Dedicated folder not accessible")
        val albumDir = downloadsRoot.findFile(safeAlbumId)
            ?: downloadsRoot.createDirectory(safeAlbumId)
            ?: throw IOException("Failed to create album dir in folder: $safeAlbumId")

        albumDir.findFile(fileName)?.delete()
        val mime = when (format.extension) {
            "flac" -> "audio/flac"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "webm" -> "audio/webm"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }
        val newFile = albumDir.createFile(mime, fileName)
            ?: throw IOException("Failed to create audio file in folder: $fileName")

        val size: Long
        try {
            size = context.contentResolver.openOutputStream(newFile.uri, "wt")?.use { out ->
                streamWithResume(
                    url = downloadUrl,
                    trackId = trackId,
                    sink = out,
                )
            } ?: throw IOException("Failed to open output stream for $fileName")
        } catch (e: Exception) {
            try {
                newFile.delete()
            } catch (_: Exception) {}
            throw e
        }
        return newFile.uri.toString() to size
    }

    /** Path-existence check that handles both local file paths and content:// URIs. */
    private fun downloadPathExists(path: String): Boolean {
        if (path.isBlank()) return false
        return if (path.startsWith("content://")) {
            try {
                val uri = path.toUri()
                val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                doc?.exists() == true
            } catch (_: Exception) {
                false
            }
        } else {
            File(path).exists()
        }
    }

    /** Delete helper that handles both local file paths and content:// URIs. */
    private fun deleteByPath(path: String) {
        if (path.isBlank()) return
        if (path.startsWith("content://")) {
            try {
                val uri = path.toUri()
                androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)?.delete()
            } catch (_: Exception) {}
        } else {
            try {
                File(path).delete()
            } catch (_: Exception) {}
        }
    }

    private suspend fun resolvePurchaseInfo(track: Track): PurchaseInfo? {
        val albumEntity = albumDao.getById(track.albumId) ?: return null
        val itemId = albumEntity.saleItemId ?: return null
        val itemType = albumEntity.saleItemType ?: return null
        return PurchaseInfo(itemId, itemType)
    }

    private suspend fun resolveHqDownloadUrl(purchaseInfo: PurchaseInfo, preferredFormat: AudioFormat): Pair<String, AudioFormat>? {
        return try {
            val urls = downloadScraper.getDownloadUrls(purchaseInfo)
            // Try preferred format first, then fall back through quality tiers
            val format = when {
                urls.containsKey(preferredFormat) -> preferredFormat
                urls.containsKey(AudioFormat.FLAC) -> AudioFormat.FLAC
                urls.containsKey(AudioFormat.MP3_320) -> AudioFormat.MP3_320
                urls.containsKey(AudioFormat.MP3_V0) -> AudioFormat.MP3_V0
                urls.containsKey(AudioFormat.AAC) -> AudioFormat.AAC
                urls.containsKey(AudioFormat.OGG_VORBIS) -> AudioFormat.OGG_VORBIS
                else -> return null
            }
            val url = urls[format] ?: return null
            Pair(url, format)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    /**
     * OkHttp client scoped to download transfers. Differences vs. the shared
     * [client]:
     *
     * - 90-second read timeout (shared is 30s). A slow-network song download
     *   can legitimately stall 30s+ between chunks on an LTE/3G connection;
     *   with 30s we'd fail downloads that would have succeeded in 35s.
     * - HTTP/1.1 only. `googlevideo.com` CDN nodes sporadically reset HTTP/2
     *   streams mid-body when the request isn't a browser/Media3 shape; HTTP/1.1
     *   is stable on the same endpoints (observed across yt-dlp, NewPipe,
     *   Metrolist issues).
     * - No cookie jar. A stale login / consent cookie can 403 the CDN.
     */
    private val downloadClient: OkHttpClient by lazy {
        client.newBuilder()
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .cookieJar(okhttp3.CookieJar.NO_COOKIES)
            .build()
    }

    /** Thin wrapper: preserves the call-site shape and forwards byte progress to the download notification chip. */
    private suspend fun streamWithResume(url: String, trackId: String, sink: OutputStream, startOffset: Long = 0L): Long =
        RangeResumeDownloader.stream(
            client = downloadClient,
            url = url,
            sink = sink,
            trackId = trackId,
            startOffset = startOffset,
            onProgress = { written, total -> notificationCenter.trackProgress(trackId, written, total) },
        )

    /**
     * Stable identity of the *content* behind a download URL, persisted in the
     * resume sidecar. googlevideo URLs rotate host/expiry/signature params on
     * every resolve but keep serving the same bytes for a given `itag`; for
     * everything else the URL minus its query is the best stable handle.
     */
    private fun resumeSourceIdentity(url: String): String {
        val httpUrl = url.toHttpUrlOrNull() ?: return url.substringBefore('?')
        val host = httpUrl.host
        return if (host == "googlevideo.com" || host.endsWith(".googlevideo.com")) {
            "itag=" + (httpUrl.queryParameter("itag") ?: "")
        } else {
            httpUrl.newBuilder().query(null).build().toString()
        }
    }

    @Suppress("SwallowedException")
    private fun readResumeMeta(metaFile: File): ResumeMeta? = try {
        if (metaFile.exists()) metaJson.decodeFromString<ResumeMeta>(metaFile.readText()) else null
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }

    @Suppress("SwallowedException")
    private fun writeResumeMeta(metaFile: File, meta: ResumeMeta) {
        try {
            metaFile.writeText(metaJson.encodeToString(ResumeMeta.serializer(), meta))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Best-effort: without a sidecar the next attempt simply restarts
            // from zero instead of resuming.
        }
    }

    override suspend fun isTrackDownloaded(trackId: String): Boolean = downloadDao.getByTrackId(trackId) != null

    override suspend fun getDownloadInfo(trackId: String): DownloadInfo? {
        val download = downloadDao.getByTrackId(trackId) ?: return null
        if (!downloadPathExists(download.filePath)) return null
        val format = AudioFormat.fromKey(download.format) ?: AudioFormat.MP3_128
        return DownloadInfo(filePath = download.filePath, format = format)
    }

    override suspend fun deleteAlbumDownloads(albumId: String) {
        val downloads = downloadDao.getByAlbumId(albumId)
        for (download in downloads) {
            deleteDownload(download.trackId)
        }
    }

    override fun getDownloadedTrackIds(): Flow<List<String>> = downloadDao.getAllTrackIds()

    override fun getDownloadedAlbumIds(): Flow<List<String>> = downloadDao.getDownloadedAlbumIds()

    override suspend fun deleteDownload(trackId: String) {
        val download = downloadDao.getByTrackId(trackId) ?: return

        // Delete the file, handling both local paths and SAF content URIs.
        deleteByPath(download.filePath)
        // Drop any paused partial + resume sidecar for the same target so a
        // later re-download starts clean.
        if (!download.filePath.startsWith("content://") && download.filePath.isNotBlank()) {
            deleteByPath(download.filePath + ".tmp")
            deleteByPath(download.filePath + ".tmp.meta")
        }

        downloadDao.delete(trackId)
        storageTracker.notifyChanged()
    }

    override suspend fun clearAll() = withContext(ioDispatcher) {
        // Drop every DB row + every file under downloads/ (including the
        // images subdir managed by Coil). ExoPlayer's media_cache is cleared
        // through the live SimpleCache instance (MediaCacheClearer) - the
        // @Singleton cache stays open for the whole process lifetime, and
        // deleting its directory underneath it desyncs the index and surfaces
        // CacheExceptions on the next playback.
        val all = downloadDao.getAllSync()
        for (row in all) {
            deleteByPath(row.filePath)
            try {
                downloadDao.delete(row.trackId)
            } catch (_: Exception) {}
        }
        try {
            com.dustvalve.next.android.data.asset.StoragePaths.imagesDir(context).deleteRecursively()
        } catch (_: Exception) {}
        try {
            mediaCacheClearer.clearAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never fall back to deleteRecursively while the cache is open;
            // stale cached media is harmless, a desynced index is not.
            android.util.Log.w("DownloadRepo", "Media cache clear failed; skipping", e)
        }
        storageTracker.notifyChanged()
    }
}
