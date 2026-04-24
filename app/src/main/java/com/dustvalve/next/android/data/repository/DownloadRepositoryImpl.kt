package com.dustvalve.next.android.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.DownloadEntity
import com.dustvalve.next.android.data.mapper.toDomain
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.remote.DustvalveDownloadScraper
import com.dustvalve.next.android.data.storage.folder.DedicatedFolderPaths
import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.PurchaseInfo
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.DownloadInfo
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import com.dustvalve.next.android.util.NetworkUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import com.dustvalve.next.android.cache.StorageTracker
import com.dustvalve.next.android.data.local.db.dao.AlbumDao
import com.dustvalve.next.android.data.local.db.dao.getByIds
import com.dustvalve.next.android.data.local.db.dao.getFavoriteIds
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.core.net.toUri
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val database: DustvalveNextDatabase,
    private val downloadDao: DownloadDao,
    private val trackDao: TrackDao,
    private val favoriteDao: FavoriteDao,
    private val albumDao: AlbumDao,
    private val client: OkHttpClient,
    private val storageTracker: StorageTracker,
    private val downloadScraper: DustvalveDownloadScraper,
    private val settingsDataStore: SettingsDataStore,
    private val youtubeRepository: YouTubeRepository,
    @param:ApplicationContext private val context: Context,
) : DownloadRepository {

    override suspend fun downloadAlbum(album: Album) {
        if (album.tracks.isEmpty()) {
            throw IOException("No tracks to download — album has an empty track list")
        }
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
            throw IOException("No tracks available for download — all ${album.tracks.size} tracks lack stream URLs")
        }
        if (errors.isNotEmpty()) {
            val skippedMsg = if (skipped > 0) " ($skipped tracks unavailable for streaming)" else ""
            throw IOException(
                "Failed to download ${errors.size} of ${album.tracks.size} tracks$skippedMsg: ${errors.first().message}"
            )
        }
        if (skipped > 0) {
            android.util.Log.w("DownloadRepo", "Downloaded ${album.tracks.size - skipped} of ${album.tracks.size} tracks; $skipped tracks lacked stream URLs")
        }
    }

    override suspend fun downloadTrack(track: Track, formatOverride: AudioFormat?) = withContext(Dispatchers.IO) {
        // Resolve purchase info for HQ download
        val purchaseInfo = resolvePurchaseInfo(track)

        val preferredFormatKey = settingsDataStore.getDownloadFormatSync()
        val preferredFormat = formatOverride ?: AudioFormat.fromKey(preferredFormatKey) ?: AudioFormat.FLAC

        // Try HQ download for purchased content, fall back to mp3-128 stream
        val (downloadUrl, format) = if (purchaseInfo != null) {
            resolveHqDownloadUrl(purchaseInfo, preferredFormat)
                ?: (track.streamUrl to AudioFormat.MP3_128)
        } else if (track.source == TrackSource.YOUTUBE) {
            // YouTube tracks store watch page URL in streamUrl; resolve actual audio stream.
            // Queue tracks may have resolved googlevideo.com URLs — reconstruct the watch URL.
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
                return@withContext
            }
            // Delete old lower-quality file before upgrading
            try { deleteByPath(existingDownload.filePath) } catch (_: Exception) {}
        }

        val safeAlbumId = NetworkUtils.sanitizeFileName(track.albumId)
        val safeTrackId = NetworkUtils.sanitizeFileName(track.id)
        val fileName = "$safeTrackId.${format.extension}"

        if (!downloadUrl.startsWith("https://")) {
            throw IOException("Download URL must use HTTPS: ${downloadUrl.take(50)}")
        }

        // Branch: SAF folder mode vs. app-internal mode. Both write via a
        // temp sibling then replace on success.
        val (finalPath, fileSize) = if (settingsDataStore.getDedicatedFolderEnabledSync()) {
            writeDownloadToFolder(safeAlbumId, fileName, format, downloadUrl, track.id)
        } else {
            writeDownloadToInternal(safeAlbumId, fileName, format, downloadUrl, track.id)
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
                )
            )
        }

        storageTracker.notifyChanged()
    }

    private suspend fun writeDownloadToInternal(
        safeAlbumId: String,
        fileName: String,
        format: AudioFormat,
        downloadUrl: String,
        trackId: String,
    ): Pair<String, Long> {
        val downloadDir = File(context.filesDir, "downloads/$safeAlbumId")
        if (!downloadDir.mkdirs() && !downloadDir.exists()) {
            throw IOException("Failed to create download directory: ${downloadDir.absolutePath}")
        }
        val targetFile = File(downloadDir, fileName)
        val tempFile = File(downloadDir, "$fileName.tmp")

        downloadFile(downloadUrl, tempFile, trackId)

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

        val request = Request.Builder().url(downloadUrl).build()
        val call = client.newCall(request)
        var size = 0L
        try {
            coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { cause ->
                if (cause != null) call.cancel()
            }
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body
                val expectedLength = response.header("Content-Length")?.toLongOrNull()
                context.contentResolver.openOutputStream(newFile.uri, "wt")?.use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            out.write(buffer, 0, read)
                            size += read
                        }
                    }
                } ?: throw IOException("Failed to open output stream for $fileName")
                if (size == 0L) throw IOException("Downloaded file is empty for track: $trackId")
                if (expectedLength != null && expectedLength > 0 && size != expectedLength) {
                    throw IOException("Size mismatch: expected $expectedLength bytes but wrote $size for track: $trackId")
                }
            }
        } catch (e: Exception) {
            try { newFile.delete() } catch (_: Exception) {}
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
            } catch (_: Exception) { false }
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
            try { File(path).delete() } catch (_: Exception) {}
        }
    }

    private suspend fun resolvePurchaseInfo(track: Track): PurchaseInfo? {
        val albumEntity = albumDao.getById(track.albumId)
        return if (albumEntity?.saleItemId != null && albumEntity.saleItemType != null) {
            PurchaseInfo(albumEntity.saleItemId, albumEntity.saleItemType)
        } else {
            null
        }
    }

    private suspend fun resolveHqDownloadUrl(
        purchaseInfo: PurchaseInfo,
        preferredFormat: AudioFormat,
    ): Pair<String, AudioFormat>? {
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

    private suspend fun downloadFile(url: String, tempFile: File, trackId: String) {
        val request = Request.Builder()
            .url(url)
            .build()

        val call = client.newCall(request)
        try {
            coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { cause ->
                if (cause != null) call.cancel()
            }
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body
                val expectedLength = response.header("Content-Length")?.toLongOrNull()

                tempFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }

                val writtenBytes = tempFile.length()
                if (writtenBytes == 0L) {
                    throw IOException("Downloaded file is empty for track: $trackId")
                }
                if (expectedLength != null && expectedLength > 0 && writtenBytes != expectedLength) {
                    throw IOException("Size mismatch: expected $expectedLength bytes but wrote $writtenBytes for track: $trackId")
                }
                if (expectedLength == null && writtenBytes < 1024) {
                    throw IOException("Download suspiciously small ($writtenBytes bytes) without Content-Length header for track: $trackId")
                }
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    override fun getDownloadedAlbums(): Flow<List<Album>> {
        return downloadDao.getAll().map { downloads ->
            if (downloads.isEmpty()) return@map emptyList()

            // Batch-fetch all favorite IDs and track entities to avoid N+1
            val allTrackIds = downloads.map { it.trackId }
            val favoriteIds = favoriteDao.getFavoriteIds(allTrackIds).toSet()
            val trackEntitiesById = trackDao.getByIds(allTrackIds)
                .associateBy { it.id }

            val grouped = downloads.groupBy { it.albumId }

            // Batch-fetch album entities and favorite status to avoid N+1 queries
            val albumIds = grouped.keys.toList()
            val albumEntitiesById = albumDao.getByIds(albumIds).associateBy { it.id }
            val favoriteAlbumIds = favoriteDao.getFavoriteIds(albumIds).toSet()

            grouped.mapNotNull { (albumId, albumDownloads) ->
                    val tracks = albumDownloads.mapNotNull { download ->
                        // Skip downloads whose files no longer exist on disk
                        if (!downloadPathExists(download.filePath)) return@mapNotNull null
                        val entity = trackEntitiesById[download.trackId] ?: return@mapNotNull null
                        entity.toDomain(isFavorite = download.trackId in favoriteIds).copy(
                            streamUrl = playableStreamUrl(download.filePath)
                        )
                    }
                    if (tracks.isEmpty()) return@mapNotNull null

                    val albumEntity = albumEntitiesById[albumId]
                    val firstTrack = tracks.first()

                    Album(
                        id = albumId,
                        url = albumEntity?.url ?: "",
                        title = albumEntity?.title ?: firstTrack.albumTitle,
                        artist = albumEntity?.artist ?: firstTrack.artist,
                        artistUrl = albumEntity?.artistUrl ?: "",
                        artUrl = albumEntity?.artUrl ?: firstTrack.artUrl,
                        releaseDate = albumEntity?.releaseDate,
                        about = albumEntity?.about,
                        tracks = tracks.sortedBy { it.trackNumber },
                        tags = emptyList(),
                        isFavorite = albumId in favoriteAlbumIds,
                    )
                }
        }.flowOn(Dispatchers.IO)
    }

    override fun getDownloadedTracks(): Flow<List<Track>> {
        return downloadDao.getAll().map { downloads ->
            if (downloads.isEmpty()) return@map emptyList()

            // Batch-fetch favorite IDs and track entities to avoid N+1
            val allTrackIds = downloads.map { it.trackId }
            val favoriteIds = favoriteDao.getFavoriteIds(allTrackIds).toSet()
            val trackEntitiesById = trackDao.getByIds(allTrackIds).associateBy { it.id }

            downloads.mapNotNull { download ->
                // Skip downloads whose files no longer exist on disk
                if (!downloadPathExists(download.filePath)) return@mapNotNull null
                trackEntitiesById[download.trackId]?.toDomain(
                    isFavorite = download.trackId in favoriteIds
                )?.copy(streamUrl = playableStreamUrl(download.filePath))
            }
        }.flowOn(Dispatchers.IO)
    }

    /** Produces a streamable URI for ExoPlayer from either a local path or a tree URI. */
    private fun playableStreamUrl(filePath: String): String =
        if (filePath.startsWith("content://")) filePath
        else android.net.Uri.fromFile(File(filePath)).toString()

    override suspend fun isTrackDownloaded(trackId: String): Boolean {
        return downloadDao.getByTrackId(trackId) != null
    }

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

    override fun getDownloadedTrackIds(): Flow<List<String>> {
        return downloadDao.getAllTrackIds()
    }

    override fun getDownloadedAlbumIds(): Flow<List<String>> {
        return downloadDao.getDownloadedAlbumIds()
    }

    override suspend fun deleteDownload(trackId: String) {
        val download = downloadDao.getByTrackId(trackId) ?: return

        // Delete the file, handling both local paths and SAF content URIs.
        deleteByPath(download.filePath)

        downloadDao.delete(trackId)
        storageTracker.notifyChanged()
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        // Drop every DB row + every file under downloads/ (including the
        // images subdir managed by Coil). ExoPlayer's media_cache lives in
        // cacheDir/media_cache and is wiped here too — Media3's SimpleCache
        // tolerates a directory wipe between sessions because we don't
        // delete it while the cache is open.
        val all = downloadDao.getAllSync()
        for (row in all) {
            deleteByPath(row.filePath)
            try { downloadDao.delete(row.trackId) } catch (_: Exception) {}
        }
        try { com.dustvalve.next.android.data.asset.StoragePaths.imagesDir(context).deleteRecursively() } catch (_: Exception) {}
        try { com.dustvalve.next.android.data.asset.StoragePaths.mediaCacheDir(context).deleteRecursively() } catch (_: Exception) {}
        storageTracker.notifyChanged()
    }
}
