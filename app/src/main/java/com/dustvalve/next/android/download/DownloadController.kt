package com.dustvalve.next.android.download

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Process-wide owner of download execution. Downloads used to run as anonymous
 * `viewModelScope` coroutines that died when the screen closed and could not be
 * cancelled from outside the UI. The controller centralizes them so they:
 *
 * - survive navigation / the hosting ViewModel being cleared,
 * - can be paused / resumed / cancelled from the notification's action buttons
 *   (which arrive via [DownloadActionReceiver], outside any ViewModel), and
 * - run under a foreground [DownloadService] that keeps the process alive.
 *
 * The controller owns the work [CoroutineScope] and a serial queue; the service
 * is a thin shell that holds the foreground notification and keeps the process
 * alive while [isActive] is true. Work items delegate to the **existing**
 * [DownloadRepository] / [DownloadAlbumUseCase] methods, preserving their
 * `withBatch` nesting and `CancellationException` rethrow contracts.
 *
 * Queue is in-memory only (pre-alpha; DB schema stays v1). A process death
 * loses pending work - partial `.tmp` files are GC'd on cold start.
 */
@Singleton
class DownloadController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val downloadAlbumUseCase: DownloadAlbumUseCase,
    private val notificationCenter: DownloadNotificationCenter,
    private val downloadDao: DownloadDao,
    @param:Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    sealed interface DownloadWork {
        val id: Long
        val label: String

        data class AlbumWork(override val id: Long, val album: Album) : DownloadWork {
            override val label get() = album.title
        }

        data class ArtistWork(override val id: Long, val artist: Artist) : DownloadWork {
            override val label get() = artist.name
        }

        data class PlaylistWork(override val id: Long, val playlistLabel: String, val tracks: List<Track>) : DownloadWork {
            override val label get() = playlistLabel
        }

        data class TrackWork(override val id: Long, val track: Track, val formatOverride: AudioFormat?) : DownloadWork {
            override val label get() = track.title
        }
    }

    sealed interface DownloadEvent {
        val workId: Long

        data class Completed(override val workId: Long, val label: String) : DownloadEvent
        data class Failed(override val workId: Long, val label: String, val error: Throwable) : DownloadEvent
    }

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val seq = AtomicLong(0L)

    /** Guards [queue] and [loopRunning]. Plain monitor - all ops are non-suspending. */
    private val lock = Any()
    private val queue = ArrayDeque<DownloadWork>()
    private var loopRunning = false
    private var loopJob: Job? = null

    @Volatile
    private var activeJob: Job? = null

    @Volatile
    private var activeWork: DownloadWork? = null

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _isActive = MutableStateFlow(false)

    /** True while any work is queued or running (drives the foreground-service lifecycle). */
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    // replay so a blocking awaiter that subscribes just *after* an
    // already-in-flight work finished still sees its terminal event (the
    // downloadTrackBlocking de-dup path joins an existing work). Unique work
    // ids mean replayed events for other works are simply filtered out.
    private val _events = MutableSharedFlow<DownloadEvent>(replay = 64, extraBufferCapacity = 32)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    private fun nextId() = seq.incrementAndGet()

    private val coldStartPurgeStarted = AtomicBoolean(false)
    private val coldStartPurgeDone = CompletableDeferred<Unit>()

    /**
     * Best-effort cold-start sweep of the internal downloads tree, launched
     * once (idempotent). Two passes:
     *
     * 1. Delete partial `.tmp` files (and their `.tmp.meta` resume sidecars)
     *    left by a previous process - the in-memory queue that could have
     *    resumed them died with it.
     * 2. Delete completed files no downloads row references - a process death
     *    between the temp-file rename and the DB insert leaves an invisible
     *    file that would otherwise never be reclaimed.
     *
     * Safe because callers that enqueue work at startup (the auto-download
     * coordinator) await [awaitColdStartPurge] before enqueueing. Only covers
     * app-internal downloads; SAF/folder mode restarts from 0 anyway. Call
     * from Application.onCreate.
     */
    @Suppress("TooGenericExceptionCaught")
    fun purgeStalePartialsOnColdStart() {
        if (!coldStartPurgeStarted.compareAndSet(false, true)) return
        scope.launch {
            try {
                val downloads = File(context.filesDir, "downloads")
                if (downloads.isDirectory) {
                    purgeStalePartials(downloads)
                    reconcileOrphanFiles(downloads)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cold-start downloads sweep failed", e)
            } finally {
                coldStartPurgeDone.complete(Unit)
            }
        }
    }

    /**
     * Suspends until the cold-start sweep has finished, starting it if it
     * hasn't run yet. Startup enqueuers (the auto-download favorites
     * coordinator) call this so the purge can't race a fresh in-progress
     * `.tmp` written by work they enqueue.
     */
    suspend fun awaitColdStartPurge() {
        purgeStalePartialsOnColdStart()
        coldStartPurgeDone.await()
    }

    private fun purgeStalePartials(downloads: File) {
        downloads.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".tmp") || it.name.endsWith(".tmp.meta")) }
            .forEach { file ->
                try {
                    file.delete()
                } catch (_: SecurityException) {
                    // Ignore partials we can't delete.
                }
            }
    }

    /**
     * Deletes files under the internal downloads tree that no downloads row
     * references. Conservative: Coil's `images` subtree is skipped (its files
     * are managed by Coil, not Room), files touched within the last
     * [ORPHAN_GRACE_MS] are skipped (a rename racing its DB insert right now
     * must not be swept), and any DB failure skips the pass entirely.
     * `content://` rows (SAF mode) point outside this tree and are ignored.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun reconcileOrphanFiles(downloads: File) {
        val known = try {
            downloadDao.getAllSync()
                .asSequence()
                .map { it.filePath }
                .filter { it.isNotBlank() && !it.startsWith("content://") }
                .map { File(it).absolutePath }
                .toHashSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Skipping orphan-file reconcile; downloads query failed", e)
            return
        }
        val imagesRoot = File(downloads, "images")
        val now = System.currentTimeMillis()
        downloads.walkTopDown()
            .onEnter { dir -> dir != imagesRoot }
            .filter { it.isFile }
            .forEach { file ->
                val fresh = now - file.lastModified() < ORPHAN_GRACE_MS
                if (!fresh && file.absolutePath !in known) {
                    try {
                        file.delete()
                    } catch (_: SecurityException) {
                        // Ignore orphans we can't delete.
                    }
                }
            }
    }

    /**
     * Fire-and-forget single-track enqueue with de-dup. Used by the
     * auto-download coordinator, which re-fires the same tracks on every
     * favorites/downloaded change; skip a track already queued or in flight.
     */
    fun enqueueTrack(track: Track, formatOverride: AudioFormat? = null) {
        synchronized(lock) {
            if (isTrackQueuedOrActive(track.id)) return
            queue.addLast(DownloadWork.TrackWork(nextId(), track, formatOverride))
        }
        onWorkAdded()
    }

    /**
     * Suspends until the album finishes (or throws on failure), but the
     * download itself runs on the controller's own scope + foreground service.
     * If the caller's scope is cancelled (e.g. the screen closes) only the
     * *await* is cancelled; the download keeps going. This lets ViewModels keep
     * their existing spinner/snackbar logic with a one-line call swap.
     */
    suspend fun downloadAlbumBlocking(album: Album) = awaitWork(DownloadWork.AlbumWork(nextId(), album))

    suspend fun downloadArtistBlocking(artist: Artist) = awaitWork(DownloadWork.ArtistWork(nextId(), artist))

    suspend fun downloadPlaylistBlocking(label: String, tracks: List<Track>) = awaitWork(DownloadWork.PlaylistWork(nextId(), label, tracks))

    suspend fun downloadTrackBlocking(track: Track, formatOverride: AudioFormat? = null) {
        val (work, alreadyQueued) = synchronized(lock) {
            val existing = queue.firstOrNull { it is DownloadWork.TrackWork && it.track.id == track.id }
                ?: (activeWork as? DownloadWork.TrackWork)?.takeIf { it.track.id == track.id }
            if (existing != null) {
                existing to true
            } else {
                DownloadWork.TrackWork(nextId(), track, formatOverride) to false
            }
        }
        awaitWork(work, alreadyQueued)
    }

    private fun isTrackQueuedOrActive(trackId: String): Boolean = queue.any { it is DownloadWork.TrackWork && it.track.id == trackId } ||
        (activeWork as? DownloadWork.TrackWork)?.track?.id == trackId

    private suspend fun awaitWork(work: DownloadWork, alreadyQueued: Boolean = false) {
        coroutineScope {
            // Subscribe to events BEFORE the work can emit (UNDISPATCHED runs the
            // collector up to its first suspension synchronously), so a fast
            // download can't complete in the gap and leave us waiting forever.
            val awaiter = async(start = CoroutineStart.UNDISPATCHED) {
                events.first { it.workId == work.id }
            }
            if (alreadyQueued) {
                onWorkAdded()
            } else {
                synchronized(lock) { queue.addLast(work) }
                onWorkAdded()
            }
            when (val event = awaiter.await()) {
                is DownloadEvent.Failed -> throw event.error
                is DownloadEvent.Completed -> Unit
            }
        }
    }

    private fun onWorkAdded() {
        // isActive writes are serialized with queue state under [lock]: the
        // drain path only writes false while it *observes* an empty queue
        // inside the lock, so this true-write (after the enqueue) can never be
        // overwritten by a stale loop that saw the pre-enqueue queue.
        synchronized(lock) { _isActive.value = true }
        startServiceIfPossible()
        ensureLoop()
    }

    /** Pauses the in-flight transfer, keeping its partial `.tmp` so [resume] can continue. */
    fun pause() {
        if (_isPaused.value) return
        _isPaused.value = true
        notificationCenter.setPaused(true)
        activeJob?.cancel(PausedDownloadException())
    }

    /** Resumes paused work; the interrupted track continues from its `.tmp` offset. */
    fun resume() {
        if (!_isPaused.value) return
        _isPaused.value = false
        notificationCenter.setPaused(false)
        startServiceIfPossible()
        ensureLoop()
    }

    /** Cancels everything queued + in-flight and discards partials. */
    fun cancelAll() {
        val cleared = synchronized(lock) {
            val c = queue.toList()
            queue.clear()
            // Same ordering rule as runLoop: the false-write happens inside
            // the critical section that emptied the queue, so it can't land
            // after a concurrent enqueue's true-write.
            _isActive.value = false
            c
        }
        _isPaused.value = false
        notificationCenter.setPaused(false)
        // Plain cancellation (not a pause) so the repository deletes the partial
        // .tmp; the active work emits its own terminal event in runWork.
        activeJob?.cancel()
        // Wake any awaiters of queued-but-never-run work so they don't hang.
        cleared.forEach {
            _events.tryEmit(DownloadEvent.Failed(it.id, it.label, CancellationException("download cancelled")))
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun startServiceIfPossible() {
        // Downloads run on the controller scope regardless; the service only
        // keeps the process alive + hosts the foreground notification. A
        // background start can be refused (Android 12+ FGS limits) - e.g. an
        // auto-download fired while backgrounded - so degrade gracefully to the
        // plain (non-FGS) notification the center already posts.
        try {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Could not start DownloadService (likely background FGS limit); downloading without it", e)
        }
    }

    private fun ensureLoop() {
        val start = synchronized(lock) {
            if (loopRunning) {
                false
            } else {
                loopRunning = true
                true
            }
        }
        if (start) loopJob = scope.launch { runLoop() }
    }

    private suspend fun runLoop() {
        while (true) {
            if (_isPaused.value) _isPaused.first { !it }
            val work = synchronized(lock) {
                val w = queue.firstOrNull()
                if (w == null) {
                    // Release the loop AND flip isActive inside the same
                    // critical section that observed the empty queue. Writing
                    // false outside the lock raced a concurrent enqueue: the
                    // new loop's work could already be running when the stale
                    // false-write landed, stopping the foreground service
                    // mid-download with nothing to re-arm it.
                    loopRunning = false
                    _isActive.value = false
                }
                w
            }
            if (work == null) return
            runWork(work)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runWork(work: DownloadWork) {
        var failure: Throwable? = null
        val job = scope.launch {
            try {
                execute(work)
            } catch (e: CancellationException) {
                failure = e
                throw e
            } catch (e: Throwable) {
                failure = e
            }
        }
        activeJob = job
        activeWork = work
        job.join()
        activeJob = null
        activeWork = null

        val err = failure
        when {
            // Paused: leave the item at the head so resume re-runs it (the
            // already-downloaded tracks are skipped cheaply, the in-flight one
            // resumes from its .tmp offset). Loop back to the pause gate.
            err != null && err.isPauseCancellation() -> Unit

            // Real failure OR a plain cancel (cancelAll): drop it and emit a
            // terminal event so any blocking awaiter unblocks.
            err != null -> {
                synchronized(lock) { queue.remove(work) }
                _events.tryEmit(DownloadEvent.Failed(work.id, work.label, err))
            }

            else -> {
                synchronized(lock) { queue.remove(work) }
                _events.tryEmit(DownloadEvent.Completed(work.id, work.label))
            }
        }
    }

    private suspend fun execute(work: DownloadWork) {
        when (work) {
            is DownloadWork.AlbumWork -> downloadRepository.downloadAlbum(work.album)
            is DownloadWork.ArtistWork -> downloadAlbumUseCase.downloadArtist(work.artist)
            is DownloadWork.PlaylistWork -> downloadAlbumUseCase.downloadPlaylist(work.playlistLabel, work.tracks)
            is DownloadWork.TrackWork -> downloadRepository.downloadTrack(work.track, work.formatOverride)
        }
    }

    companion object {
        private const val TAG = "DownloadController"

        /**
         * Files younger than this are never treated as orphans by the
         * cold-start reconcile - a just-renamed file whose DB insert is still
         * in flight must survive the sweep.
         */
        private const val ORPHAN_GRACE_MS = 10L * 60L * 1000L
    }
}
