package com.dustvalve.next.android.player

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.dustvalve.next.android.domain.model.RepeatMode
import com.dustvalve.next.android.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

// Main is intentionally absent from AppDispatchers (see Dispatcher.kt):
// tests substitute it globally via Dispatchers.setMain, so qualifying
// it would only add ceremony.
@Suppress("RawDispatchersUse")
@Singleton
class PlaybackManager @Inject constructor(
    private val player: ExoPlayer,
    private val queueManager: QueueManager,
    @param:ApplicationContext private val context: Context,
) {

    private var scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
                android.util.Log.e("PlaybackManager", "Unhandled coroutine error", throwable)
            },
    )

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    /**
     * Last playback error, or null. Before this existed, onPlayerError only
     * logged: the UI showed the track at 0:00 with a play button that did
     * nothing and the user had no idea anything failed. The ViewModel surfaces
     * this as a snackbar and clears it via [clearPlaybackError].
     */
    private val _playbackError = MutableStateFlow<PlaybackException?>(null)
    val playbackError: StateFlow<PlaybackException?> = _playbackError.asStateFlow()

    /**
     * Optional stream resolution hook installed by the PlayerViewModel.
     * Consulted from skip/jump/auto-advance when a queue entry cannot be
     * handed to ExoPlayer as-is: null/blank streamUrl, a YouTube watch-page
     * URL that was never resolved, or a TTL-stale resolution flagged by
     * [streamIsStale]. Returns a playable replacement track (same id, fresh
     * streamUrl) or null when resolution failed.
     */
    var streamResolver: (suspend (Track) -> Track?)? = null

    /**
     * Optional staleness check installed by the PlayerViewModel (which keeps
     * per-track resolve timestamps). True = the track's resolved stream URL is
     * older than the freshness TTL and should be re-resolved before playback.
     */
    var streamIsStale: ((Track) -> Boolean)? = null

    fun clearPlaybackError() {
        _playbackError.value = null
    }

    private var positionUpdateJob: Job? = null

    /** In-flight on-demand stream resolution for playTrack; superseded by any newer play intent. */
    private var resolveJob: Job? = null

    /** Tracks whether a seek is in progress to avoid position update overwrite */
    @Volatile
    private var seekInProgress = false

    /** Guards against duplicate STATE_ENDED handling */
    private val handlingPlaybackEnded = AtomicBoolean(false)

    /** Prevents calls to a released ExoPlayer */
    @Volatile
    private var released = false

    /** Whether the PlaybackService has been started for this session */
    private var serviceStarted = false

    /**
     * Where the current track stood when [release] ran (service idle-stop or
     * task removal). The queue survives release, so the next [play] re-prepares
     * [resumeTrackId] and seeks back here instead of losing the session.
     */
    private var resumePositionMs = 0L
    private var resumeTrackId: String? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                // If playback resumes (e.g. after an in-cache seek that skips buffering),
                // clear seekInProgress so position updates aren't permanently suppressed
                seekInProgress = false
                startPositionUpdates()
            } else {
                stopPositionUpdates()
                _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            _playbackState.value = state
            when (state) {
                Player.STATE_READY -> {
                    _duration.value = player.duration.coerceAtLeast(0L)
                    seekInProgress = false
                }

                Player.STATE_ENDED -> {
                    // Defer to avoid re-entrant Player.Listener callbacks when
                    // handlePlaybackEnded calls playTrack -> setMediaItem/prepare/play.
                    // Guard prevents duplicate handling if STATE_ENDED fires rapidly.
                    if (handlingPlaybackEnded.compareAndSet(false, true)) {
                        scope.launch(Dispatchers.Main) {
                            try {
                                handlePlaybackEnded()
                            } finally {
                                handlingPlaybackEnded.set(false)
                            }
                        }
                    }
                }

                else -> {}
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            android.util.Log.e("PlaybackManager", "Player error: ${error.errorCodeName}", error)
            _playbackError.value = error
            _isPlaying.value = false
            _playbackState.value = Player.STATE_IDLE
            _duration.value = 0L
            seekInProgress = false
            stopPositionUpdates()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            _duration.value = player.duration.coerceAtLeast(0L)
        }
    }

    init {
        player.addListener(playerListener)
        queueManager.onCurrentTrackRemoved = ::handleCurrentTrackRemoved
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = scope.launch {
            while (isActive) {
                if (!seekInProgress) {
                    _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
                }
                delay(200L)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun handlePlaybackEnded() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                player.seekTo(0)
                player.play()
            }

            RepeatMode.ALL -> {
                val nextTrack = queueManager.next()
                if (nextTrack != null) {
                    playTrack(nextTrack)
                } else {
                    // Wrap around to the beginning WITHOUT rebuilding the queue:
                    // setQueue would null the pre-shuffle snapshot, silently
                    // breaking shuffle-off after one full pass.
                    val firstTrack = queueManager.resetToStart()
                    if (firstTrack != null) playTrack(firstTrack)
                }
            }

            RepeatMode.OFF -> {
                val nextTrack = queueManager.next()
                if (nextTrack != null) {
                    playTrack(nextTrack)
                } else {
                    _isPlaying.value = false
                    stopPositionUpdates()
                }
            }
        }
    }

    /**
     * Installed as [QueueManager.onCurrentTrackRemoved]: when the currently
     * playing queue entry is removed, advance the actual player to the new
     * current track (preserving play/pause state) instead of leaving the
     * removed track audible while the flows already point at its successor.
     */
    private fun handleCurrentTrackRemoved(removed: Track, newCurrent: Track?) {
        if (released) return
        // Only react when the player is actually on the removed track; a
        // pending playTrack may have swapped the media item already.
        if (player.currentMediaItem?.mediaId != removed.id) return
        if (newCurrent == null) {
            stop()
            return
        }
        val wasPlaying = player.isPlaying
        playTrack(newCurrent)
        if (!wasPlaying) player.pause()
    }

    private fun ensureServiceStarted() {
        if (serviceStarted) return
        val intent = Intent(context, PlaybackService::class.java)
        ContextCompat.startForegroundService(context, intent)
        serviceStarted = true
    }

    /** YouTube watch-page / short-link URLs are HTML pages, not media streams. */
    private fun isWatchPageUrl(url: String): Boolean = // Also matches music.youtube.com/watch and m.youtube.com/watch.
        url.contains("youtube.com/watch") || url.contains("youtu.be/")

    /**
     * True when [track] cannot be handed to ExoPlayer as-is. Defense in depth:
     * during the background-resolution window every not-yet-resolved YouTube
     * entry still carries its watch-page URL in streamUrl, and the old
     * isNullOrBlank-only guard let skip/jump/auto-advance feed ExoPlayer HTML.
     */
    private fun trackNeedsResolution(track: Track): Boolean {
        val url = track.streamUrl
        if (url.isNullOrBlank()) return true
        if (isWatchPageUrl(url)) return true
        if (!track.isLocal && streamIsStale?.invoke(track) == true) return true
        return false
    }

    fun playTrack(track: Track) {
        if (released) reinitialize()
        resolveJob?.cancel()
        resolveJob = null
        if (!trackNeedsResolution(track)) {
            playResolvedTrack(track)
            return
        }
        resolveJob = scope.launch {
            resolveAndPlay(track)
        }
    }

    /**
     * Resolves [first] (and, if it stays unplayable, its successors) before
     * playback. Preserves the long-standing bounded skip-unplayable behavior:
     * one dead track (region-blocked, deleted, failed resolution) must not
     * silently kill the rest of the queue, and the bound stops an
     * all-unplayable queue from looping forever.
     */
    @OptIn(UnstableApi::class)
    private suspend fun resolveAndPlay(first: Track) {
        val startIndex = queueManager.currentIndex.value
        var candidate = first
        var advanced = 0
        val queueSize = queueManager.queue.value.size
        while (true) {
            val playable = resolveCandidate(candidate)
            if (playable != null) {
                playResolvedTrack(playable)
                return
            }
            android.util.Log.w("PlaybackManager", "Cannot play track '${candidate.title}': no playable stream URL")
            // A single break keeps this within detekt's jump budget: stop once
            // the bound is hit or the queue has no further successor to try.
            val nextCandidate = if (advanced >= queueSize) null else queueManager.next()
            if (nextCandidate == null) break
            candidate = nextCandidate
            advanced++
        }
        // Give-up branch: no playable successor anywhere. Put currentIndex back
        // where this attempt started instead of leaving it walked to the queue
        // end, surface the error, and keep the flows truthful to the audible
        // state - the previously playing track (if any) is still sounding and
        // must not be reported as IDLE/stopped.
        if (startIndex >= 0 && startIndex != queueManager.currentIndex.value) {
            queueManager.skipToIndex(startIndex)
        }
        _playbackError.value = PlaybackException(
            "No playable stream URL for '${first.title}'",
            null,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )
        _isPlaying.value = player.isPlaying
        _playbackState.value = player.playbackState
    }

    /**
     * Returns a playable version of [track]: the track itself when its
     * streamUrl is already good, the [streamResolver]'s fresh resolution
     * (patched into the queue in-place) otherwise, or null when it cannot be
     * made playable.
     */
    @Suppress("TooGenericExceptionCaught") // resolver hook runs arbitrary repository code
    private suspend fun resolveCandidate(track: Track): Track? {
        if (!trackNeedsResolution(track)) return track
        val resolver = streamResolver ?: return null
        val resolved = try {
            resolver(track)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.w("PlaybackManager", "Stream resolution failed for '${track.title}'", e)
            null
        }
        val url = resolved?.streamUrl
        if (resolved == null || url.isNullOrBlank() || isWatchPageUrl(url)) return null
        queueManager.applyResolvedTracks(mapOf(resolved.id to resolved))
        return resolved
    }

    @OptIn(UnstableApi::class)
    private fun playResolvedTrack(track: Track) {
        val url = track.streamUrl ?: return
        ensureServiceStarted()

        // Reset seek flag so position updates resume immediately for the new track
        seekInProgress = false
        // A new track supersedes any previous failure.
        _playbackError.value = null

        // Include queue position info in metadata for notification display
        val currentIndex = queueManager.currentIndex.value
        val queueSize = queueManager.queue.value.size

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.albumTitle)
            .setTrackNumber(if (currentIndex >= 0) currentIndex + 1 else null)
            .setTotalTrackCount(if (queueSize > 0) queueSize else null)

        if (track.artUrl.isNotBlank()) {
            try {
                metadataBuilder.setArtworkUri(track.artUrl.toUri())
            } catch (_: Exception) {
                // Ignore malformed artwork URIs
            }
        }

        // Convert bare file paths to file:// URIs so ExoPlayer doesn't reject them as malformed URLs
        val resolvedUri = if (url.startsWith("/")) File(url).toUri().toString() else url

        val mediaItem = MediaItem.Builder()
            .setUri(resolvedUri)
            .setMediaId(track.id)
            .setMediaMetadata(metadataBuilder.build())
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        // Resume-after-idle-stop: the service teardown preserved the queue and
        // saved where playback stopped; the first re-prepare of that same track
        // picks up at the saved position.
        val resumeAt = resumePositionMs
        if (resumeAt > 0L && track.id == resumeTrackId) {
            player.seekTo(resumeAt)
            _currentPosition.value = resumeAt
        }
        resumePositionMs = 0L
        resumeTrackId = null
        player.play()
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (released) reinitialize()
        queueManager.setQueue(tracks, startIndex)
        val track = tracks.getOrNull(startIndex) ?: return
        playTrack(track)
    }

    fun play() {
        if (released) {
            resumeAfterRelease()
            return
        }
        // After a PlaybackException ExoPlayer sits in STATE_IDLE and ignores
        // play() until prepare() is called again. Without this, the play
        // button silently did nothing forever after any error. prepare()
        // keeps the current media item and position, so this resumes where
        // the failure happened.
        if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
            player.prepare()
        }
        if (player.playbackState == Player.STATE_ENDED) {
            // Respect repeat mode when restarting after playback ended
            when (_repeatMode.value) {
                RepeatMode.ONE -> {
                    // Replay the current track
                    val currentTrack = queueManager.currentTrack.value
                    if (currentTrack != null) {
                        playTrack(currentTrack)
                    } else {
                        player.seekTo(0)
                        player.play()
                    }
                    return
                }

                RepeatMode.ALL -> {
                    // Restart from the beginning of the queue, preserving the
                    // shuffle snapshot (setQueue would null it).
                    val firstTrack = queueManager.resetToStart()
                    if (firstTrack != null) playTrack(firstTrack)
                    return
                }

                RepeatMode.OFF -> {
                    // Replay the current track from the beginning
                    player.seekTo(0)
                    player.play()
                    return
                }
            }
        }
        player.play()
    }

    /**
     * The service idle-stop released the player but preserved the queue:
     * re-prepare the current track (at the saved position, via the resume
     * fields consumed in playResolvedTrack) and restart the service. Without
     * this, play() after an idle-stop was a silent no-op forever.
     */
    private fun resumeAfterRelease() {
        val current = queueManager.currentTrack.value ?: return
        playTrack(current)
    }

    fun pause() {
        if (released) return
        player.pause()
    }

    fun stop() {
        if (released) return
        resolveJob?.cancel()
        resolveJob = null
        resumePositionMs = 0L
        resumeTrackId = null
        player.stop()
        player.clearMediaItems()
        _playbackError.value = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        _playbackState.value = Player.STATE_IDLE
        stopPositionUpdates()
    }

    fun togglePlayPause() {
        if (released) {
            // After an idle-stop the player is released but the queue is
            // intact - route to play(), which revives the session.
            play()
            return
        }
        if (player.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun skipToQueueIndex(index: Int) {
        if (released) reinitialize()
        val track = queueManager.skipToIndex(index) ?: return
        playTrack(track)
    }

    fun seekTo(positionMs: Long) {
        if (released) return
        val clampedPosition = if (_duration.value > 0) {
            positionMs.coerceIn(0L, _duration.value)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        seekInProgress = true
        _currentPosition.value = clampedPosition
        player.seekTo(clampedPosition)

        // If the player is paused and the seek completes from cache, neither
        // onIsPlayingChanged(true) nor onPlaybackStateChanged(STATE_READY) may fire.
        // Post a delayed check to clear the flag so position updates aren't stuck.
        scope.launch {
            delay(500L)
            if (seekInProgress && !player.isPlaying) {
                seekInProgress = false
                _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
            }
        }
    }

    fun skipNext() {
        if (released) reinitialize()
        val nextTrack = queueManager.next()
        if (nextTrack != null) {
            playTrack(nextTrack)
        } else if (_repeatMode.value == RepeatMode.ALL) {
            // Wrap without setQueue so the shuffle snapshot survives the pass.
            val firstTrack = queueManager.resetToStart()
            if (firstTrack != null) playTrack(firstTrack)
        }
    }

    fun skipPrevious() {
        if (released) reinitialize()
        // If more than 3 seconds in, restart current track instead
        if (player.currentPosition > 3000L) {
            seekTo(0L)
            return
        }

        val prevTrack = queueManager.previous()
        if (prevTrack != null) {
            playTrack(prevTrack)
        } else {
            seekTo(0L)
        }
    }

    /**
     * Seamlessly switches the audio source mid-playback (e.g., from stream to local HQ file).
     * Preserves the current playback position.
     */
    @OptIn(UnstableApi::class)
    fun hotSwapSource(filePath: String, trackId: String) {
        if (released) return
        val currentMediaId = player.currentMediaItem?.mediaId ?: return
        if (currentMediaId != trackId) return // Track changed since download started

        val currentPos = player.currentPosition.coerceAtLeast(0L)
        val wasPlaying = player.isPlaying
        val currentMetadata = player.mediaMetadata

        // Convert bare file paths to file:// URIs so ExoPlayer doesn't reject them as malformed URLs
        val resolvedUri = if (filePath.startsWith("/")) File(filePath).toUri().toString() else filePath

        val mediaItem = MediaItem.Builder()
            .setUri(resolvedUri)
            .setMediaId(trackId)
            .setMediaMetadata(currentMetadata)
            .build()

        seekInProgress = true
        player.setMediaItem(mediaItem)
        player.prepare()
        player.seekTo(currentPos)
        if (wasPlaying) player.play()
    }

    fun setShuffleEnabled(enabled: Boolean) {
        if (released) return
        _shuffleEnabled.value = enabled
        if (enabled) {
            queueManager.shuffle()
        } else {
            queueManager.unshuffle()
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        if (released) return
        _repeatMode.value = mode
        // Always keep ExoPlayer repeat off - our custom handlePlaybackEnded handles all repeat logic
        player.repeatMode = Player.REPEAT_MODE_OFF
    }

    @OptIn(UnstableApi::class)
    fun setPreferredAudioDevice(device: AudioDeviceInfo?) {
        if (released) return
        player.setPreferredAudioDevice(device)
    }

    internal fun release() {
        released = true
        serviceStarted = false
        stopPositionUpdates()
        handlingPlaybackEnded.set(false)
        // Non-destructive teardown: the queue (preserved by QueueManager) plus
        // these resume fields let play() restore the session after the service
        // idle-stop. Capture BEFORE clearMediaItems resets the position.
        resumeTrackId = player.currentMediaItem?.mediaId
        resumePositionMs = player.currentPosition.coerceAtLeast(0L)
        if (resumePositionMs == 0L) resumePositionMs = _currentPosition.value
        player.removeListener(playerListener)
        player.stop()
        player.clearMediaItems()
        // Keep _currentPosition/_duration so the mini/full player still shows
        // where playback stopped; only the transport state goes idle.
        _isPlaying.value = false
        _playbackState.value = Player.STATE_IDLE
        scope.cancel()
    }

    internal fun reinitialize() {
        if (!released) return
        scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate +
                kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
                    android.util.Log.e("PlaybackManager", "Unhandled coroutine error", throwable)
                },
        )
        released = false
        serviceStarted = false
        seekInProgress = false
        handlingPlaybackEnded.set(false)
        player.addListener(playerListener)
        if (player.mediaItemCount > 0) {
            // Player still holds real state (e.g. service restart mid-session):
            // mirror it into the flows.
            _isPlaying.value = player.isPlaying
            _playbackState.value = player.playbackState
            _duration.value = player.duration.coerceAtLeast(0L)
            _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
            if (player.isPlaying) {
                startPositionUpdates()
            }
        }
        // else: keep the flows preserved by release() (last track position and
        // duration) so the UI doesn't flash back to 0:00 after an idle-stop.
    }
}
