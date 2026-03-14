package com.dustvalve.next.android.player

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
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
import androidx.core.net.toUri
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackManager @Inject constructor(
    private val player: ExoPlayer,
    private val queueManager: QueueManager,
    @ApplicationContext private val context: Context,
) {

    private var scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
                android.util.Log.e("PlaybackManager", "Unhandled coroutine error", throwable)
            }
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

    private var positionUpdateJob: Job? = null

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
                    // Wrap around to beginning of queue
                    val currentQueue = queueManager.queue.value
                    if (currentQueue.isNotEmpty()) {
                        queueManager.setQueue(currentQueue, 0)
                        playTrack(currentQueue.first())
                    }
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

    private fun ensureServiceStarted() {
        if (serviceStarted) return
        val intent = Intent(context, PlaybackService::class.java)
        ContextCompat.startForegroundService(context, intent)
        serviceStarted = true
    }

    @OptIn(UnstableApi::class)
    fun playTrack(track: Track) {
        if (released) return
        val url = track.streamUrl
        if (url.isNullOrBlank()) {
            android.util.Log.w("PlaybackManager", "Cannot play track '${track.title}': streamUrl is null")
            _playbackState.value = Player.STATE_IDLE
            _isPlaying.value = false
            return
        }
        ensureServiceStarted()

        // Reset seek flag so position updates resume immediately for the new track
        seekInProgress = false

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
        player.play()
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (released) return
        queueManager.setQueue(tracks, startIndex)
        val track = tracks.getOrNull(startIndex) ?: return
        playTrack(track)
    }

    fun play() {
        if (released) return
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
                    // Restart from the beginning of the queue
                    val queue = queueManager.queue.value
                    if (queue.isNotEmpty()) {
                        queueManager.setQueue(queue, 0)
                        playTrack(queue.first())
                    }
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

    fun pause() {
        if (released) return
        player.pause()
    }

    fun togglePlayPause() {
        if (released) return
        if (player.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun skipToQueueIndex(index: Int) {
        if (released) return
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
        if (released) return
        val nextTrack = queueManager.next()
        if (nextTrack != null) {
            playTrack(nextTrack)
        } else if (_repeatMode.value == RepeatMode.ALL) {
            val queue = queueManager.queue.value
            if (queue.isNotEmpty()) {
                queueManager.setQueue(queue, 0)
                playTrack(queue.first())
            }
        }
    }

    fun skipPrevious() {
        if (released) return
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
        // Always keep ExoPlayer repeat off — our custom handlePlaybackEnded handles all repeat logic
        player.repeatMode = Player.REPEAT_MODE_OFF
    }

    internal fun release() {
        released = true
        serviceStarted = false
        stopPositionUpdates()
        handlingPlaybackEnded.set(false)
        player.removeListener(playerListener)
        player.stop()
        player.clearMediaItems()
        scope.cancel()
    }

    internal fun reinitialize() {
        if (!released) return
        scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate +
                kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
                    android.util.Log.e("PlaybackManager", "Unhandled coroutine error", throwable)
                }
        )
        released = false
        serviceStarted = false
        seekInProgress = false
        handlingPlaybackEnded.set(false)
        player.addListener(playerListener)
        _isPlaying.value = player.isPlaying
        _playbackState.value = player.playbackState
        _duration.value = player.duration.coerceAtLeast(0L)
        _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
        if (player.isPlaying) {
            startPositionUpdates()
        }
    }
}
