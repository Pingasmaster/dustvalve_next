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
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import com.dustvalve.next.android.download.isPauseCancellation
import com.dustvalve.next.android.util.NetworkUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
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
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : DownloadRepository {

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
        notificationCenter.trackStarted(track.id, track.title)
        var success = false
        try {
            downloadTrackInner(track, formatOverride)
            success = true
        } finally {
            notificationCenter.trackFinished(track.id, success)
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
            // Delete old lower-quality file before upgrading
            try {
                deleteByPath(existingDownload.filePath)
            } catch (_: Exception) {}
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

        // A leftover .tmp means a prior transfer was paused - resume from its
        // current length via an HTTP Range request (append mode) instead of
        // restarting from 0.
        val resumeFrom = if (tempFile.exists()) tempFile.length() else 0L
        try {
            FileOutputStream(tempFile, resumeFrom > 0L).use { out ->
                streamWithResume(
                    url = downloadUrl,
                    trackId = trackId,
                    sink = out,
                    startOffset = resumeFrom,
                )
            }
        } catch (e: Exception) {
            // Keep the partial on pause so resume can continue; delete on any
            // real failure or cancel.
            if (!e.isPauseCancellation()) tempFile.delete()
            throw e
        }

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

        downloadDao.delete(trackId)
        storageTracker.notifyChanged()
    }

    override suspend fun clearAll() = withContext(ioDispatcher) {
        // Drop every DB row + every file under downloads/ (including the
        // images subdir managed by Coil). ExoPlayer's media_cache lives in
        // cacheDir/media_cache and is wiped here too - Media3's SimpleCache
        // tolerates a directory wipe between sessions because we don't
        // delete it while the cache is open.
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
            com.dustvalve.next.android.data.asset.StoragePaths.mediaCacheDir(context).deleteRecursively()
        } catch (_: Exception) {}
        storageTracker.notifyChanged()
    }
}
