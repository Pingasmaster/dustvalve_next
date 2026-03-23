package com.dustvalve.next.android.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.CacheEntryDao
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.CacheEntryEntity
import com.dustvalve.next.android.data.local.db.entity.DownloadEntity
import com.dustvalve.next.android.data.mapper.toDomain
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.remote.DustvalveDownloadScraper
import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.PurchaseInfo
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.DownloadInfo
import com.dustvalve.next.android.domain.repository.DownloadRepository
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
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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
    private val cacheEntryDao: CacheEntryDao,
    private val storageTracker: StorageTracker,
    private val downloadScraper: DustvalveDownloadScraper,
    private val settingsDataStore: SettingsDataStore,
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
                File(existingDownload.filePath).exists()
            ) {
                return@withContext
            }
            // Delete old lower-quality file before upgrading
            try { File(existingDownload.filePath).delete() } catch (_: Exception) {}
        }

        val safeAlbumId = NetworkUtils.sanitizeFileName(track.albumId)
        val safeTrackId = NetworkUtils.sanitizeFileName(track.id)

        val downloadDir = File(context.filesDir, "downloads/$safeAlbumId")
        if (!downloadDir.mkdirs() && !downloadDir.exists()) {
            throw IOException("Failed to create download directory: ${downloadDir.absolutePath}")
        }

        val targetFile = File(downloadDir, "$safeTrackId.${format.extension}")
        val tempFile = File(downloadDir, "$safeTrackId.${format.extension}.tmp")

        if (!downloadUrl.startsWith("https://")) {
            throw IOException("Download URL must use HTTPS: ${downloadUrl.take(50)}")
        }

        downloadFile(downloadUrl, tempFile, track.id)

        // Atomic rename on success — fall back to copy+delete if rename fails
        if (!tempFile.renameTo(targetFile)) {
            try {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            } catch (e: Exception) {
                // Clean up partial target file on copy failure
                targetFile.delete()
                tempFile.delete()
                throw IOException("Failed to copy download to target: ${e.message}")
            }
        }

        if (!targetFile.exists() || targetFile.length() == 0L) {
            throw IOException("Failed to write download file: ${targetFile.absolutePath}")
        }

        val fileSize = targetFile.length()

        // Atomically insert track, download record, and cache entry
        database.withTransaction {
            if (trackDao.getById(track.id) == null) {
                trackDao.insertAll(listOf(track.toEntity()))
            }

            downloadDao.insert(
                DownloadEntity(
                    trackId = track.id,
                    albumId = track.albumId,
                    filePath = targetFile.absolutePath,
                    sizeBytes = fileSize,
                    format = format.key,
                )
            )

            cacheEntryDao.insert(
                CacheEntryEntity(
                    key = "download_${track.id}",
                    type = "download",
                    sizeBytes = fileSize,
                    lastAccessed = System.currentTimeMillis(),
                    isUserDownload = true,
                    filePath = targetFile.absolutePath,
                )
            )
        }

        storageTracker.notifyCacheChanged()
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
                        if (!File(download.filePath).exists()) return@mapNotNull null
                        val entity = trackEntitiesById[download.trackId] ?: return@mapNotNull null
                        entity.toDomain(isFavorite = download.trackId in favoriteIds).copy(
                            streamUrl = android.net.Uri.fromFile(File(download.filePath)).toString()
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
                if (!File(download.filePath).exists()) return@mapNotNull null
                trackEntitiesById[download.trackId]?.toDomain(
                    isFavorite = download.trackId in favoriteIds
                )?.copy(streamUrl = android.net.Uri.fromFile(File(download.filePath)).toString())
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun isTrackDownloaded(trackId: String): Boolean {
        return downloadDao.getByTrackId(trackId) != null
    }

    override suspend fun getDownloadInfo(trackId: String): DownloadInfo? {
        val download = downloadDao.getByTrackId(trackId) ?: return null
        if (!File(download.filePath).exists()) return null
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

    override suspend fun exportDownloads(
        destinationUri: String,
        onProgress: (exported: Int, total: Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val downloads = downloadDao.getAllSync()
        if (downloads.isEmpty()) return@withContext 0

        val trackIds = downloads.map { it.trackId }
        val albumIds = downloads.map { it.albumId }.distinct()
        val trackEntitiesById = trackDao.getByIds(trackIds).associateBy { it.id }
        val albumEntitiesById = albumDao.getByIds(albumIds).associateBy { it.id }

        val treeUri = Uri.parse(destinationUri)
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("Cannot access selected folder")

        val dirCache = mutableMapOf<String, DocumentFile>()
        var processed = 0
        var actuallyExported = 0
        val total = downloads.size

        for (download in downloads) {
            coroutineContext.ensureActive()

            val sourceFile = File(download.filePath)
            if (!sourceFile.exists()) {
                onProgress(++processed, total)
                continue
            }

            val trackEntity = trackEntitiesById[download.trackId]
            val albumEntity = albumEntitiesById[download.albumId]
            val artist = trackEntity?.artist ?: "Unknown Artist"
            val albumTitle = albumEntity?.title ?: trackEntity?.albumTitle ?: "Unknown Album"
            val trackTitle = trackEntity?.title ?: download.trackId
            val trackNumber = (trackEntity?.trackNumber ?: 0).toString().padStart(2, '0')
            val format = AudioFormat.fromKey(download.format) ?: AudioFormat.MP3_128

            val folderName = NetworkUtils.sanitizeFileName("$artist - $albumTitle")
            val albumDir = dirCache.getOrPut(folderName) {
                rootDoc.findFile(folderName) ?: rootDoc.createDirectory(folderName)
                ?: throw IOException("Failed to create folder: $folderName")
            }

            val fileName = NetworkUtils.sanitizeFileName("$trackNumber - $trackTitle") + ".${format.extension}"
            val mimeType = when (format.extension) {
                "flac" -> "audio/flac"
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "ogg" -> "audio/ogg"
                else -> "application/octet-stream"
            }

            // Remove existing file with same name (re-export)
            albumDir.findFile(fileName)?.delete()

            val newFile = albumDir.createFile(mimeType, fileName)
                ?: throw IOException("Failed to create file: $fileName")

            context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output, 8192)
                }
            } ?: throw IOException("Failed to open output stream for: $fileName")

            actuallyExported++
            onProgress(++processed, total)
        }

        actuallyExported
    }

    override suspend fun deleteDownload(trackId: String) {
        val download = downloadDao.getByTrackId(trackId) ?: return

        // Delete the file
        try {
            File(download.filePath).delete()
        } catch (_: Exception) {
            // Best-effort file deletion
        }

        // Remove from database atomically
        database.withTransaction {
            downloadDao.delete(trackId)
            cacheEntryDao.deleteByKey("download_$trackId")
        }

        storageTracker.notifyCacheChanged()
    }
}
