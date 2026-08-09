package com.dustvalve.next.android.player

import android.content.Context
import android.media.AudioDeviceInfo
import androidx.annotation.OptIn
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
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

// Main is intentionally absent from AppDispatchers (see Dispatcher.kt):
// tests substitute it globally via Dispatchers.setMain, so qualifying
// it would only add ceremony.
@Suppress("RawDispatchersUse")
@Singleton
class PlaybackManager @Inject constructor(
    private val player: ExoPlayer,
    private val queueManager: QueueManager,
    // Constructor param (not a property): only feeds [mediaPreparer] below.
    @ApplicationContext context: Context,
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
     * handed to ExoPlayer as-is.
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

    /** In-flight on-demand stream resolution for playTrack; superseded by any newer play intent. */
    private var resolveJob: Job? = null

    /** Guards against duplicate STATE_ENDED handling */
    private val handlingPlaybackEnded = AtomicBoolean(false)

    /** Prevents calls to a released ExoPlayer */
    @Volatile
    private var released = false

    /**
     * Where the current track stood when [release] ran (service idle-stop or
     * task removal). The queue survives release, so the next [play] re-prepares
     * [resumeTrackId] and seeks back here instead of losing the session.
     */
    private var resumePositionMs = 0L
    private var resumeTrackId: String? = null

    private val positionTracker = PlaybackPositionTracker(
        player = player,
        scopeProvider = { scope },
        currentPosition = _currentPosition,
    )

    private val mediaPreparer = PlaybackMediaPreparer(
        player = player,
        queueManager = queueManager,
        context = context,
        flows = PlaybackMediaFlows(
            isPlaying = _isPlaying,
            playbackState = _playbackState,
            playbackError = _playbackError,
            currentPosition = _currentPosition,
        ),
        hooks = PlaybackMediaHooks(
            onSeekFlag = { positionTracker.seekInProgress = it },
            consumeResume = { track ->
                val resumeAt = resumePositionMs
                val match = resumeAt > 0L && track.id == resumeTrackId
                resumePositionMs = 0L
                resumeTrackId = null
                if (match) resumeAt else 0L
            },
            streamResolver = { streamResolver },
            streamIsStale = { streamIsStale },
        ),
    )

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                positionTracker.seekInProgress = false
                positionTracker.startUpdates()
            } else {
                positionTracker.stopUpdates()
                _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            _playbackState.value = state
            when (state) {
                Player.STATE_READY -> {
                    _duration.value = player.duration.coerceAtLeast(0L)
                    positionTracker.seekInProgress = false
                }

                Player.STATE_ENDED -> {
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
            positionTracker.seekInProgress = false
            _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
            positionTracker.stopUpdates()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            _duration.value = player.duration.coerceAtLeast(0L)
        }
    }

    init {
        player.addListener(playerListener)
        queueManager.onCurrentTrackRemoved = ::handleCurrentTrackRemoved
        positionTracker.startDemandGate()
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
                    positionTracker.stopUpdates()
                }
            }
        }
    }

    private fun handleCurrentTrackRemoved(removed: Track, newCurrent: Track?) {
        if (released) return
        if (player.currentMediaItem?.mediaId != removed.id) return
        if (newCurrent == null) {
            stop()
            return
        }
        val wasPlaying = player.isPlaying
        playTrack(newCurrent)
        if (!wasPlaying) player.pause()
    }

    fun playTrack(track: Track) {
        if (released) reinitialize()
        resolveJob?.cancel()
        resolveJob = null
        if (!mediaPreparer.trackNeedsResolution(track)) {
            mediaPreparer.playResolvedTrack(track)
            return
        }
        resolveJob = scope.launch {
            mediaPreparer.resolveAndPlay(track)
        }
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
        if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
            player.prepare()
        }
        if (player.playbackState == Player.STATE_ENDED) {
            when (_repeatMode.value) {
                RepeatMode.ONE -> {
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
                    val firstTrack = queueManager.resetToStart()
                    if (firstTrack != null) playTrack(firstTrack)
                    return
                }

                RepeatMode.OFF -> {
                    player.seekTo(0)
                    player.play()
                    return
                }
            }
        }
        player.play()
    }

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
        positionTracker.stopUpdates()
    }

    fun togglePlayPause() {
        if (released) {
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
        positionTracker.seekInProgress = true
        _currentPosition.value = clampedPosition
        player.seekTo(clampedPosition)

        scope.launch {
            delay(SEEK_SETTLE_TIMEOUT_MS)
            if (positionTracker.seekInProgress && !player.isPlaying) {
                positionTracker.seekInProgress = false
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
            val firstTrack = queueManager.resetToStart()
            if (firstTrack != null) playTrack(firstTrack)
        }
    }

    fun skipPrevious() {
        if (released) reinitialize()
        if (player.currentPosition > SKIP_PREVIOUS_RESTART_THRESHOLD_MS) {
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

    @OptIn(UnstableApi::class)
    fun hotSwapSource(filePath: String, trackId: String) {
        if (released) return
        val currentMediaId = player.currentMediaItem?.mediaId ?: return
        if (currentMediaId != trackId) return

        val currentPos = player.currentPosition.coerceAtLeast(0L)
        val wasPlaying = player.isPlaying
        val currentMetadata = player.mediaMetadata

        val resolvedUri = if (filePath.startsWith("/")) File(filePath).toUri().toString() else filePath

        val mediaItem = MediaItem.Builder()
            .setUri(resolvedUri)
            .setMediaId(trackId)
            .setMediaMetadata(currentMetadata)
            .build()

        positionTracker.seekInProgress = true
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
        player.repeatMode = Player.REPEAT_MODE_OFF
    }

    @OptIn(UnstableApi::class)
    fun setPreferredAudioDevice(device: AudioDeviceInfo?) {
        if (released) return
        player.setPreferredAudioDevice(device)
    }

    internal fun release() {
        released = true
        mediaPreparer.markServiceStopped()
        positionTracker.stopUpdates()
        handlingPlaybackEnded.set(false)
        resumeTrackId = player.currentMediaItem?.mediaId
        resumePositionMs = player.currentPosition.coerceAtLeast(0L)
        if (resumePositionMs == 0L) resumePositionMs = _currentPosition.value
        player.removeListener(playerListener)
        player.stop()
        player.clearMediaItems()
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
        mediaPreparer.markServiceStopped()
        positionTracker.seekInProgress = false
        handlingPlaybackEnded.set(false)
        positionTracker.startDemandGate()
        player.addListener(playerListener)
        if (player.mediaItemCount > 0) {
            _isPlaying.value = player.isPlaying
            _playbackState.value = player.playbackState
            _duration.value = player.duration.coerceAtLeast(0L)
            _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
            if (player.isPlaying) {
                positionTracker.startUpdates()
            }
        }
    }

    private companion object {
        private const val SEEK_SETTLE_TIMEOUT_MS = 500L
        private const val SKIP_PREVIOUS_RESTART_THRESHOLD_MS = 3000L
    }
}
