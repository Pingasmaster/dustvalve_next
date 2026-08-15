package com.dustvalve.next.android.data.repository

import android.content.Context
import android.os.StatFs
import androidx.room.withTransaction
import com.dustvalve.next.android.cache.StorageTracker
import com.dustvalve.next.android.data.local.DatabaseGateway
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.DownloadEntity
import com.dustvalve.next.android.data.mapper.toDomain
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.data.remote.DustvalveStreamResolver
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import com.dustvalve.next.android.di.qualifiers.MediaHttp
import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.DownloadInfo
import com.dustvalve.next.android.domain.repository.DownloadProgressReporter
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.MediaCacheClearer
import com.dustvalve.next.android.domain.repository.SoundCloudRepository
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import com.dustvalve.next.android.download.downloadEachDeferringFailures
import com.dustvalve.next.android.util.NetworkUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class DownloadRepositoryImpl(
    private val database: DustvalveNextDatabase,
    private val downloadDao: DownloadDao,
    private val trackDao: TrackDao,
    // MediaHttp: no callTimeout - a track download on a slow connection
    // legitimately outlives the base client's 30s whole-call cap.
    private val client: OkHttpClient,
    private val storageTracker: StorageTracker,
    private val youtubeRepository: YouTubeRepository,
    private val soundCloudRepository: SoundCloudRepository,
    private val dustvalveStreamResolver: DustvalveStreamResolver,
    private val notificationCenter: DownloadProgressReporter,
    private val mediaCacheClearer: MediaCacheClearer,
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : DownloadRepository {

    @Inject constructor(
        gateway: DatabaseGateway,
        @MediaHttp client: OkHttpClient,
        storageTracker: StorageTracker,
        youtubeRepository: YouTubeRepository,
        soundCloudRepository: SoundCloudRepository,
        dustvalveStreamResolver: DustvalveStreamResolver,
        notificationCenter: DownloadProgressReporter,
        mediaCacheClearer: MediaCacheClearer,
        @ApplicationContext context: Context,
        @Dispatcher(AppDispatchers.IO) ioDispatcher: CoroutineDispatcher,
    ) : this(
        gateway.database,
        gateway.downloadDao,
        gateway.trackDao,
        client,
        storageTracker,
        youtubeRepository,
        soundCloudRepository,
        dustvalveStreamResolver,
        notificationCenter,
        mediaCacheClearer,
        context,
        ioDispatcher,
    )

    /**
     * Sidecar persisted next to a partial `.tmp` file. On resume the recorded
     * total size and source identity must still match the freshly re-resolved
     * download URL - YouTube re-resolution can hand back a *different* stream
     * variant for the same track, and appending its bytes onto a partial from
     * another variant splices a corrupt file that still passes the size check.
     */
    @Serializable
    internal data class ResumeMeta(val expectedTotalBytes: Long, val sourceIdentity: String)

    private val sourceResolver = DownloadSourceResolver(
        youtubeRepository = youtubeRepository,
        soundCloudRepository = soundCloudRepository,
        dustvalveStreamResolver = dustvalveStreamResolver,
    )

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
     * - No cookie jar. A stale consent cookie can 403 the CDN.
     * - No HTTP disk cache. Audio bodies must not compete with JSON in the
     *   shared OkHttp Cache (Range requests through that cache stall).
     */
    private val downloadClient: OkHttpClient by lazy {
        client.newBuilder()
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .cookieJar(okhttp3.CookieJar.NO_COOKIES)
            .cache(null)
            .build()
    }

    private val fileWriter by lazy {
        DownloadFileWriter(
            context = context,
            downloadClient = downloadClient,
            notificationCenter = notificationCenter,
        )
    }

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
            totalTracks = album.tracks.count { isDownloadCandidate(it) },
            kind = DownloadProgressReporter.BatchKind.ALBUM,
        ) {
            downloadAlbumInner(album)
        }
    }

    private suspend fun downloadAlbumInner(album: Album) {
        val downloadable = album.tracks.filter { isDownloadCandidate(it) }
        val skipped = album.tracks.size - downloadable.size
        if (downloadable.isEmpty()) {
            throw IOException("No tracks available for download - all ${album.tracks.size} tracks lack stream URLs")
        }

        // A track that cannot be fetched (age-restricted YouTube video, dead
        // stream URL) is skipped on the spot and retried once after the rest
        // of the album; only a second failure gives up on it. Each attempt is
        // logged as it fails, so a cancellation part-way through still leaves
        // the diagnostics behind. CancellationException is never absorbed by
        // the runner, so structured concurrency is respected.
        val result = downloadEachDeferringFailures(
            items = downloadable,
            onAttemptFailed = { track, e ->
                android.util.Log.w("DownloadRepo", "Download attempt failed for track ${track.id}: ${e.message}")
            },
        ) { track -> downloadTrack(track) }

        if (result.hasUnavailable) {
            val skippedMsg = if (skipped > 0) " ($skipped tracks unavailable for streaming)" else ""
            throw IOException(
                "Failed to download ${result.unavailable.size} of ${album.tracks.size} tracks$skippedMsg: ${result.error?.message}",
            )
        }
        if (skipped > 0) {
            android.util.Log.w(
                "DownloadRepo",
                "Downloaded ${album.tracks.size - skipped} of ${album.tracks.size} tracks; $skipped tracks lacked stream URLs",
            )
        }
    }

    @Suppress("UnusedParameter")
    override suspend fun downloadTrack(track: Track, formatOverride: AudioFormat?) = withContext(ioDispatcher) {
        // formatOverride kept for API compatibility; free/stream downloads are
        // always mp3-128. HQ purchase format selection was login-only.
        // Serialize concurrent calls for the same track. The loser of the race
        // waits, then short-circuits via the existing same-or-higher-quality
        // check in downloadTrackInner once the winner has committed its file.
        withTrackLock(track.id) {
            notificationCenter.trackStarted(track.id, track.title)
            var success = false
            try {
                downloadTrackInner(track)
                success = true
            } finally {
                notificationCenter.trackFinished(track.id, success)
            }
        }
    }

    @Suppress("ThrowsCount")
    private suspend fun downloadTrackInner(track: Track) {
        val (downloadUrl, format) = sourceResolver.resolve(track)

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
        // Provisional extension from the resolved stream format; MIME / magic
        // sniffing inside writeDownloadToInternal may replace it before commit.
        val provisionalName = "$safeTrackId.${format.extension}"

        if (!downloadUrl.startsWith("https://")) {
            throw IOException("Download URL must use HTTPS: ${downloadUrl.take(ERROR_URL_PREVIEW_CHARS)}")
        }

        assertEnoughFreeSpace()

        // Downloads always land in app-private storage: the write goes via a
        // temp sibling and an atomic rename.
        val (finalPath, fileSize) = fileWriter.write(safeAlbumId, provisionalName, downloadUrl, track.id)

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

    /**
     * Refuses to start a transfer when the data partition is nearly full.
     * Pinned user downloads are never evicted by the storage-limit slider, so
     * a low-disk device would otherwise keep accepting downloads until writes
     * fail mid-file. Leaves [MIN_FREE_BYTES] of headroom.
     *
     * Best-effort: when StatFs cannot measure (JVM unit tests, sealed paths)
     * the check is skipped and the eventual write failure surfaces instead.
     */
    private fun assertEnoughFreeSpace() {
        val free = try {
            StatFs(context.filesDir.absolutePath).availableBytes
        } catch (_: IllegalArgumentException) {
            return
        } catch (_: SecurityException) {
            return
        }
        // Android stubs / broken mounts often report 0; treat as unmeasurable.
        if (free <= 0L) return
        if (free < MIN_FREE_BYTES) {
            throw IOException(
                "Not enough free storage to download " +
                    "(${free / BYTES_PER_MB} MB free; need ${MIN_FREE_BYTES / BYTES_PER_MB} MB)",
            )
        }
    }

    private fun downloadPathExists(path: String): Boolean = path.isNotBlank() && File(path).exists()

    private fun deleteByPath(path: String) {
        if (path.isBlank()) return
        try {
            File(path).delete()
        } catch (_: SecurityException) {
        }
    }

    override suspend fun isTrackDownloaded(trackId: String): Boolean {
        val row = downloadDao.getByTrackId(trackId) ?: return false
        return downloadPathExists(row.filePath)
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

    override fun getDownloadedTrackIds(): Flow<List<String>> = downloadDao.getAll().map { rows ->
        rows.filter { downloadPathExists(it.filePath) }.map { it.trackId }
    }

    override fun getDownloadedAlbumIds(): Flow<List<String>> = downloadDao.getAll().map { rows ->
        // Same file-backed filter as getDownloadedTrackIds: a DB row alone is
        // not enough (process death between insert and rename, wiped files).
        rows.filter { downloadPathExists(it.filePath) }.map { it.albumId }.distinct()
    }

    // Raw and unfiltered on purpose: DownloadController's orphan-file
    // reconciliation owns the isNotBlank filtering and its own exception
    // handling.
    override suspend fun getAllDownloadFilePaths(): List<String> = downloadDao.getAllSync().map { it.filePath }

    /**
     * Drops download rows whose file is gone (process death between rename and
     * insert is the inverse case; this covers delete-outside-app / wiped files).
     * Called from [com.dustvalve.next.android.download.DownloadController]
     * cold-start sweep.
     */
    override suspend fun purgeOrphanDownloadRows(): Int {
        val all = downloadDao.getAllSync()
        var removed = 0
        for (row in all) {
            if (!downloadPathExists(row.filePath)) {
                try {
                    downloadDao.delete(row.trackId)
                    removed++
                } catch (_: android.database.SQLException) {
                } catch (_: IllegalStateException) {
                }
            }
        }
        if (removed > 0) storageTracker.notifyChanged()
        return removed
    }

    override suspend fun deleteDownload(trackId: String) {
        val download = downloadDao.getByTrackId(trackId) ?: return

        deleteByPath(download.filePath)
        // Drop any paused partial + resume sidecar for the same target so a
        // later re-download starts clean.
        if (download.filePath.isNotBlank()) {
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
            } catch (_: android.database.SQLException) {
            } catch (_: IllegalStateException) {
            }
        }
        try {
            com.dustvalve.next.android.data.asset.StoragePaths.imagesDir(context).deleteRecursively()
        } catch (_: IOException) {
        } catch (_: SecurityException) {
        }
        try {
            com.dustvalve.next.android.data.asset.StoragePaths.coversDir(context).deleteRecursively()
        } catch (_: IOException) {
        } catch (_: SecurityException) {
        }
        try {
            mediaCacheClearer.clearAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // Never fall back to deleteRecursively while the cache is open;
            // stale cached media is harmless, a desynced index is not.
            android.util.Log.w("DownloadRepo", "Media cache clear failed; skipping", e)
        } catch (e: IllegalStateException) {
            android.util.Log.w("DownloadRepo", "Media cache clear failed; skipping", e)
        }
        storageTracker.notifyChanged()
    }

    private companion object {
        /** Truncation length for unsafe download URLs in error messages. */
        private const val ERROR_URL_PREVIEW_CHARS = 50

        /**
         * SoundCloud tracks ship with streamUrl=null and resolve on demand;
         * YouTube/Bandcamp already carry a usable URL (watch page or CDN).
         * HLS-only / DRM SoundCloud tracks are not file-download candidates.
         */
        fun isDownloadCandidate(track: Track): Boolean {
            if (track.isStreamOnlyOrBlocked) return false
            return track.streamUrl != null || track.source == TrackSource.SOUNDCLOUD
        }

        /** Refuse new downloads when free space drops below this headroom. */
        private const val MIN_FREE_BYTES = 64L * 1024L * 1024L

        private const val BYTES_PER_MB = 1024L * 1024L
    }
}
