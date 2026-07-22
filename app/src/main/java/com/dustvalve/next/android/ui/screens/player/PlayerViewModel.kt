package com.dustvalve.next.android.ui.screens.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.dustvalve.next.android.R
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.remote.DustvalveStreamResolver
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.domain.model.RepeatMode
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.LibraryRepository
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import com.dustvalve.next.android.download.DownloadController
import com.dustvalve.next.android.player.PlaybackManager
import com.dustvalve.next.android.player.QueueEntry
import com.dustvalve.next.android.player.QueueManager
import com.dustvalve.next.android.util.NetworkUtils
import com.dustvalve.next.android.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

data class PlayerUiState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentQueueIndex: Int = -1,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isMiniPlayerVisible: Boolean = false,
    val isLoadingTrack: Boolean = false,
    val downloadedTrackIds: Set<String> = emptySet(),
    val downloadingTrackId: String? = null,
    val playlists: List<Playlist> = emptyList(),
    val snackbarMessage: UiText? = null,
    val isSnackbarError: Boolean = false,
    val currentPlaybackFormat: AudioFormat? = null,
    val currentSourcePath: String? = null,
    val progressBarStyle: String = "wavy",
    val progressBarSizeDp: Int = 24,
    val userPlaylistTrackIds: Set<String> = emptySet(),
    val volumeLevel: Float = 1f,
    val maxVolumeLevel: Int = 15,
    val showInlineVolumeSlider: Boolean = false,
    val showVolumeButton: Boolean = false,
    val audioOutputDevices: List<AudioDeviceInfo> = emptyList(),
    val activeAudioDevice: AudioDeviceInfo? = null,
    val albumCoverLongPressCarousel: Boolean = true,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    private val libraryRepository: LibraryRepository,
    private val downloadAlbumUseCase: DownloadAlbumUseCase,
    private val downloadController: DownloadController,
    private val downloadRepository: DownloadRepository,
    private val playlistRepository: PlaylistRepository,
    private val favoriteDao: com.dustvalve.next.android.data.local.db.dao.FavoriteDao,
    private val settingsDataStore: SettingsDataStore,
    private val youtubeRepository: YouTubeRepository,
    private val dustvalveStreamResolver: DustvalveStreamResolver,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _extraState = MutableStateFlow(ExtraState())

    private data class ExtraState(
        val downloadedTrackIds: Set<String> = emptySet(),
        val downloadingTrackId: String? = null,
        val playlists: List<Playlist> = emptyList(),
        val snackbarMessage: UiText? = null,
        val isSnackbarError: Boolean = false,
        val currentPlaybackFormat: AudioFormat? = null,
        val currentSourcePath: String? = null,
        val userPlaylistTrackIds: Set<String> = emptySet(),
        val isLoadingTrack: Boolean = false,
    )

    private var progressiveDownloadJob: Job? = null
    private var playJob: Job? = null

    /**
     * Generation token for isLoadingTrack: bumped by every play request so a
     * cancelled play job's finally-block can't clear the loading indicator the
     * NEWER job just set (rapid double-tap race).
     */
    private var loadingGeneration = 0

    /**
     * trackId -> wall-clock ms when its remote stream URL was last resolved.
     * Remote stream URLs (YouTube googlevideo, Bandcamp CDN) expire after a
     * few hours; entries older than [STREAM_URL_TTL_MS] are re-resolved before
     * playback instead of handing ExoPlayer a dead link. Main-thread only.
     */
    private val streamResolvedAtMs = HashMap<String, Long>()

    /**
     * One-shot auto-recovery guard: track ids that already got their single
     * automatic re-resolve for the current failure. Re-armed when the track
     * subsequently reaches STATE_READY (a genuine recovery), so a later expiry
     * of the same track can recover again without ever looping on a
     * permanently dead stream.
     */
    private val autoRetriedTrackIds = mutableSetOf<String>()

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _audioDevices = MutableStateFlow(getOutputDevices())
    private val _activeAudioDevice = MutableStateFlow<AudioDeviceInfo?>(null)

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            _audioDevices.value = getOutputDevices()
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            _audioDevices.value = getOutputDevices()
            // Clear active device if it was removed
            val active = _activeAudioDevice.value
            if (active != null && _audioDevices.value.none { it.id == active.id }) {
                _activeAudioDevice.value = null
                playbackManager.setPreferredAudioDevice(null)
            }
        }
    }

    private fun getOutputDevices(): List<AudioDeviceInfo> = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter {
            it.type !in setOf(
                AudioDeviceInfo.TYPE_TELEPHONY,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            )
        }
        .distinctBy { it.type to (it.productName?.toString() ?: "") }
        .toList()

    init {
        collectDownloadedTrackIds()
        collectPlaylists()
        collectUserPlaylistTrackIds()
        collectFavoriteTrackIds()
        collectPlaybackErrors()
        collectPlaybackReadyForRetryReset()
        // Resolution hooks for skip/jump/auto-advance: PlaybackManager calls
        // these when a queue entry still carries an unresolved watch-page URL
        // or a TTL-stale stream URL. Deliberately NOT cleared in onCleared():
        // the singletons outlive this activity-scoped ViewModel, and skip from
        // the media notification must keep resolving while the UI is closed
        // (the next MainActivity's ViewModel overwrites them).
        playbackManager.streamIsStale = ::isStreamResolutionStale
        playbackManager.streamResolver = { track -> resolveStreamOnDemand(track) }
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
    }

    // Surface player errors as a snackbar - unless a one-shot automatic
    // re-resolve can transparently recover an expired stream URL first.
    // Historically onPlayerError only logged to logcat, so a failed
    // stream/file looked like "track shown, stuck at 0:00, play button dead"
    // with zero feedback.
    private fun collectPlaybackErrors() {
        viewModelScope.launch {
            playbackManager.playbackError.collect { error ->
                if (error == null) return@collect
                playbackManager.clearPlaybackError()
                if (tryAutoRecoverStream(error)) return@collect
                _extraState.update {
                    it.copy(
                        snackbarMessage = UiText.StringResource(R.string.snackbar_audio_stream_failed),
                        isSnackbarError = true,
                    )
                }
            }
        }
    }

    // A track that reaches READY genuinely played: re-arm its one-shot
    // auto-recovery so a future expiry (multi-hour listening session) can be
    // recovered again. A dead stream never reaches READY, so this cannot
    // create a retry loop.
    private fun collectPlaybackReadyForRetryReset() {
        viewModelScope.launch {
            playbackManager.playbackState.collect { state ->
                if (state == Player.STATE_READY) {
                    queueManager.currentTrack.value?.id?.let { autoRetriedTrackIds.remove(it) }
                }
            }
        }
    }

    /**
     * Error codes that typically mean the resolved stream URL went stale
     * (expired CDN token -> HTTP 403/410/404 or an HTML error page) and a
     * fresh resolution can fix it.
     */
    private fun isRecoverableStreamError(error: PlaybackException): Boolean = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        -> true

        else -> false
    }

    /**
     * One-shot automatic recovery from an expired stream URL: re-resolve the
     * current track (YouTube via the repository, Bandcamp via
     * [DustvalveStreamResolver]), patch the queue entry in place and retry
     * playback at the position where it failed. Returns false when recovery is
     * not applicable or failed - the caller then surfaces the normal error UI.
     */
    private suspend fun tryAutoRecoverStream(error: PlaybackException): Boolean {
        val track = queueManager.currentTrack.value
        // Order matters: the one-shot guard (add) must only trip once the
        // failure is actually recoverable for this track.
        val canRecover = track != null && !track.isLocal &&
            isRecoverableStreamError(error) && autoRetriedTrackIds.add(track.id)
        if (!canRecover || track == null) return false
        val resumeAt = playbackManager.currentPosition.value
        _extraState.update { it.copy(isLoadingTrack = true) }
        val fresh = try {
            reResolveStream(track)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        } finally {
            _extraState.update { it.copy(isLoadingTrack = false) }
        }
        if (fresh?.streamUrl == null) return false
        queueManager.applyResolvedTracks(mapOf(fresh.id to fresh))
        playbackManager.playTrack(fresh)
        if (resumeAt > 0L) playbackManager.seekTo(resumeAt)
        return true
    }

    /** Fetches a genuinely fresh stream URL, bypassing any cached/stale one. */
    private suspend fun reResolveStream(track: Track): Track? = when (track.source) {
        TrackSource.YOUTUBE -> {
            // The queue entry's streamUrl now holds the (expired) resolved
            // googlevideo URL, so rebuild the watch URL from the yt_<videoId>
            // id - the same reconstruction resolveTrackForPlayback uses.
            val videoId = track.id.removePrefix("yt_").takeIf { it.isNotBlank() && it != track.id }
            videoId?.let {
                val freshUrl = youtubeRepository.getStreamUrl("https://www.youtube.com/watch?v=$it")
                recordStreamResolved(track.id)
                track.copy(streamUrl = freshUrl)
            }
        }

        TrackSource.BANDCAMP -> reResolveBandcampStream(track)

        else -> null
    }

    private suspend fun reResolveBandcampStream(track: Track): Track? {
        val pageUrl = track.albumUrl.takeIf { it.isNotBlank() } ?: track.bandcampTrackUrl
        if (pageUrl.isNullOrBlank()) return null
        // The resolver returns track.streamUrl untouched when it is set, so
        // blank it to force a fresh album-page fetch.
        val freshUrl = dustvalveStreamResolver.resolveStreamUrl(track.copy(streamUrl = null), pageUrl) ?: return null
        recordStreamResolved(track.id)
        return track.copy(streamUrl = freshUrl)
    }

    private fun recordStreamResolved(trackId: String) {
        streamResolvedAtMs[trackId] = System.currentTimeMillis()
    }

    /**
     * Installed as [PlaybackManager.streamIsStale]. Only tracks whose remote
     * resolution timestamp we know can be stale; scrape-time Bandcamp URLs
     * without a timestamp are played optimistically and covered by the
     * error-path auto-recovery instead.
     */
    private fun isStreamResolutionStale(track: Track): Boolean {
        val streamUrl = track.streamUrl
        if (track.isLocal || streamUrl == null || !streamUrl.startsWith("http")) return false
        val resolvedAt = streamResolvedAtMs[track.id] ?: return false
        return System.currentTimeMillis() - resolvedAt > STREAM_URL_TTL_MS
    }

    /**
     * Installed as [PlaybackManager.streamResolver]: runs the same resolution
     * as the direct-tap path for entries that skip/auto-advance found
     * unresolved or stale. Returns null when the track cannot be made
     * playable, letting PlaybackManager's bounded skip-unplayable logic take
     * over.
     */
    private suspend fun resolveStreamOnDemand(track: Track): Track? {
        val resolved = try {
            if (track.source == TrackSource.BANDCAMP &&
                !track.streamUrl.isNullOrBlank() &&
                isStreamResolutionStale(track) &&
                downloadRepository.getDownloadInfo(track.id) == null
            ) {
                // resolveTrackForPlayback would hand back the same stale URL.
                reResolveBandcampStream(track)
            } else {
                resolveTrackForPlayback(track, updateState = false)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
        return resolved?.takeIf { !it.streamUrl.isNullOrBlank() }
    }

    // Reactively patch the queue's per-track isFavorite from the DB so
    // toggles done from album view / favorites tab show up on the player.
    private fun collectFavoriteTrackIds() {
        viewModelScope.launch {
            favoriteDao.getAllByType("track")
                .catch { /* ignore */ }
                .collect { entities ->
                    queueManager.applyFavoriteIds(entities.map { it.id }.toSet())
                }
        }
    }

    override fun onCleared() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }

    /**
     * Resolves the best available stream URL for a track:
     * 1. If downloaded locally at same-or-higher quality -> use local file
     * 2. Otherwise -> use the track's stream URL (mp3-128)
     *
     * Also triggers a progressive background download if enabled.
     */
    private suspend fun resolveTrackForPlayback(track: Track, updateState: Boolean = true): Track {
        // Local tracks already have a content:// URI - use as-is
        if (track.isLocal) {
            if (updateState) {
                _extraState.update {
                    it.copy(
                        currentPlaybackFormat = null,
                        currentSourcePath = track.streamUrl,
                    )
                }
            }
            return track
        }

        // YouTube tracks: check download first, then resolve stream URL live
        if (track.source == TrackSource.YOUTUBE) {
            val ytDownloadInfo = downloadRepository.getDownloadInfo(track.id)
            if (ytDownloadInfo != null) {
                if (updateState) {
                    _extraState.update {
                        it.copy(
                            currentPlaybackFormat = ytDownloadInfo.format,
                            currentSourcePath = ytDownloadInfo.filePath,
                        )
                    }
                }
                return track.copy(streamUrl = ytDownloadInfo.streamUri)
            }
            // Resolve stream URL from YouTube. Belt-and-braces: if the track
            // arrived without a watch URL (older cached entries carried
            // streamUrl = null) OR already carries a previously resolved (and
            // possibly expired) googlevideo URL, reconstruct the watch URL
            // from the yt_<videoId> id the same way DownloadRepositoryImpl
            // does, instead of silently bailing with an unplayable track.
            val watchUrl = track.streamUrl
                ?.takeIf { it.contains("youtube.com/watch") || it.contains("youtu.be/") }
                ?: track.id.removePrefix("yt_").takeIf { it.isNotBlank() && it != track.id }
                    ?.let { "https://www.youtube.com/watch?v=$it" }
            return try {
                val streamUrl = youtubeRepository.getStreamUrl(watchUrl ?: return track.copy(streamUrl = null))
                recordStreamResolved(track.id)
                if (updateState) {
                    _extraState.update {
                        it.copy(currentPlaybackFormat = null, currentSourcePath = null)
                    }
                }
                track.copy(streamUrl = streamUrl)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Only surface the failure when resolving the track the user
                // asked to play NOW. Background pre-resolution of the rest of
                // the queue (updateState = false) must not raise a spurious
                // "stream failed" snackbar over perfectly healthy playback.
                if (updateState) {
                    _extraState.update {
                        it.copy(
                            snackbarMessage = UiText.StringResource(R.string.snackbar_audio_stream_failed),
                            isSnackbarError = true,
                        )
                    }
                }
                // Return track with null streamUrl so callers skip playback
                // instead of passing the YouTube watch page URL to ExoPlayer
                track.copy(streamUrl = null)
            }
        }

        // Check for existing local download
        val downloadInfo = downloadRepository.getDownloadInfo(track.id)
        if (downloadInfo != null && downloadInfo.format.qualityRank >= AudioFormat.MP3_128.qualityRank) {
            // Already have a same-or-higher quality local file - use it
            if (updateState) {
                _extraState.update {
                    it.copy(
                        currentPlaybackFormat = downloadInfo.format,
                        currentSourcePath = downloadInfo.filePath,
                    )
                }
            }
            return track.copy(streamUrl = downloadInfo.streamUri)
        }

        // No local download - use original stream URL (mp3-128)
        if (updateState) {
            _extraState.update {
                it.copy(
                    currentPlaybackFormat = AudioFormat.MP3_128,
                    currentSourcePath = null,
                )
            }
        }
        return track
    }

    /**
     * Triggers a progressive HQ download in the background after playback starts.
     * The next time this track is played, the local HQ file will be used.
     */
    private fun triggerProgressiveDownload(track: Track) {
        if (track.isLocal) return // Local tracks don't need downloading
        progressiveDownloadJob?.cancel()
        progressiveDownloadJob = viewModelScope.launch {
            try {
                val progressiveEnabled = settingsDataStore.getProgressiveDownloadSync()
                if (!progressiveEnabled) return@launch

                // Check if already downloaded at HQ
                val existingDownload = downloadRepository.getDownloadInfo(track.id)
                if (existingDownload != null && existingDownload.format.qualityRank > AudioFormat.MP3_128.qualityRank) {
                    precacheNextTrack()
                    return@launch
                }

                // On metered + save-data enabled -> download MP3-320 instead of preferred format
                // YouTube always gets best available stream, so skip format override
                val formatOverride = if (track.source != TrackSource.YOUTUBE) {
                    val saveOnMetered = settingsDataStore.getSaveDataOnMeteredSync()
                    if (saveOnMetered && NetworkUtils.isMeteredConnection(appContext)) {
                        AudioFormat.MP3_320
                    } else {
                        null
                    }
                } else {
                    null
                }

                // Download in background
                downloadRepository.downloadTrack(track, formatOverride)

                // Seamless hot-swap: if the same track is still playing, switch to local HQ file
                val downloadInfo = downloadRepository.getDownloadInfo(track.id)
                if (downloadInfo != null) {
                    val currentTrack = queueManager.currentTrack.value
                    if (currentTrack != null && currentTrack.id == track.id) {
                        val seamlessUpgrade = settingsDataStore.getSeamlessQualityUpgradeSync()
                        if (seamlessUpgrade) {
                            playbackManager.hotSwapSource(downloadInfo.streamUri, track.id)
                        }
                        _extraState.update {
                            it.copy(
                                currentPlaybackFormat = downloadInfo.format,
                                currentSourcePath = downloadInfo.filePath,
                            )
                        }
                    }
                }

                // Current track download complete - precache next track in queue
                precacheNextTrack()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Best-effort: progressive download failure is silent
            }
        }
    }

    /**
     * Precaches the next track in the queue by downloading it in the background.
     * Skips if the next track is already downloaded or currently being downloaded.
     */
    private suspend fun precacheNextTrack() {
        val queue = queueManager.queue.value
        val currentIndex = queueManager.currentIndex.value
        val nextTrack = queue.getOrNull(currentIndex + 1) ?: return

        // Already downloaded - nothing to do
        val existing = downloadRepository.getDownloadInfo(nextTrack.id)
        if (existing != null && existing.format.qualityRank >= AudioFormat.MP3_128.qualityRank) return

        // Already being manually downloaded - don't duplicate
        if (_extraState.value.downloadingTrackId == nextTrack.id) return

        try {
            val formatOverride = if (nextTrack.source != TrackSource.YOUTUBE) {
                val saveOnMetered = settingsDataStore.getSaveDataOnMeteredSync()
                if (saveOnMetered && NetworkUtils.isMeteredConnection(appContext)) {
                    AudioFormat.MP3_320
                } else {
                    null
                }
            } else {
                null
            }
            downloadRepository.downloadTrack(nextTrack, formatOverride)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Best-effort: precache failure is silent
        }
    }

    private fun collectDownloadedTrackIds() {
        viewModelScope.launch {
            downloadRepository.getDownloadedTrackIds()
                .catch { /* ignore */ }
                .collect { ids ->
                    _extraState.update { it.copy(downloadedTrackIds = ids.toSet()) }
                }
        }
    }

    private fun collectPlaylists() {
        viewModelScope.launch {
            playlistRepository.getAllPlaylists()
                .catch { /* ignore */ }
                .collect { playlists ->
                    _extraState.update { it.copy(playlists = playlists) }
                }
        }
    }

    private fun collectUserPlaylistTrackIds() {
        viewModelScope.launch {
            playlistRepository.getTrackIdsInUserPlaylists()
                .catch { /* ignore */ }
                .collect { ids ->
                    _extraState.update { it.copy(userPlaylistTrackIds = ids) }
                }
        }
    }

    val uiState: StateFlow<PlayerUiState> = combine(
        queueManager.currentTrack,
        queueManager.queue,
        playbackManager.isPlaying,
        playbackManager.currentPosition,
        playbackManager.duration,
    ) { currentTrack, queue, isPlaying, currentPosition, duration ->
        PlayerUiState(
            currentTrack = currentTrack,
            queue = queue,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            isMiniPlayerVisible = currentTrack != null,
        )
    }.combine(
        combine(
            playbackManager.shuffleEnabled,
            playbackManager.repeatMode,
            queueManager.currentIndex,
        ) { shuffle, repeat, index -> Triple(shuffle, repeat, index) },
    ) { state, (shuffle, repeat, index) ->
        state.copy(
            shuffleEnabled = shuffle,
            repeatMode = repeat,
            currentQueueIndex = index,
        )
    }.combine(_extraState) { state, extra ->
        state.copy(
            downloadedTrackIds = extra.downloadedTrackIds,
            downloadingTrackId = extra.downloadingTrackId,
            playlists = extra.playlists,
            snackbarMessage = extra.snackbarMessage,
            isSnackbarError = extra.isSnackbarError,
            currentPlaybackFormat = extra.currentPlaybackFormat,
            currentSourcePath = extra.currentSourcePath,
            userPlaylistTrackIds = extra.userPlaylistTrackIds,
            isLoadingTrack = extra.isLoadingTrack,
        )
    }.combine(
        combine(
            settingsDataStore.progressBarStyle,
            settingsDataStore.progressBarSizeDp,
        ) { style, sizeDp -> style to sizeDp },
    ) { state, (style, sizeDp) ->
        state.copy(progressBarStyle = style, progressBarSizeDp = sizeDp)
    }.combine(
        combine(
            settingsDataStore.showInlineVolumeSlider,
            settingsDataStore.showVolumeButton,
        ) { inline, button -> inline to button },
    ) { state, (inline, button) ->
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        state.copy(
            showInlineVolumeSlider = inline,
            showVolumeButton = button,
            maxVolumeLevel = maxVol,
            volumeLevel = if (maxVol > 0) curVol.toFloat() / maxVol else 1f,
        )
    }.combine(
        combine(_audioDevices, _activeAudioDevice) { devices, active -> devices to active },
    ) { state, (devices, active) ->
        state.copy(
            audioOutputDevices = devices,
            activeAudioDevice = active,
        )
    }.combine(settingsDataStore.albumCoverLongPressCarousel) { state, carousel ->
        state.copy(albumCoverLongPressCarousel = carousel)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlayerUiState(),
    )

    fun setAudioOutputDevice(device: AudioDeviceInfo?) {
        _activeAudioDevice.value = device
        playbackManager.setPreferredAudioDevice(device)
    }

    fun setVolume(level: Float) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVol = (level * maxVol).roundToInt().coerceIn(0, maxVol)
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        } catch (_: SecurityException) {
            // Do-Not-Disturb / volume policy can forbid the change. This is
            // called from a drag-gesture snapshotFlow collector; an uncaught
            // SecurityException there kills the whole app.
        }
    }

    fun onPlayPause() {
        playbackManager.togglePlayPause()
    }

    fun onNext() {
        playbackManager.skipNext()
    }

    fun onPrevious() {
        playbackManager.skipPrevious()
    }

    fun onSeek(ms: Long) {
        playbackManager.seekTo(ms)
    }

    fun onStop() {
        playbackManager.stop()
        queueManager.clear()
    }

    fun onToggleShuffle() {
        val newValue = !playbackManager.shuffleEnabled.value
        playbackManager.setShuffleEnabled(newValue)
    }

    fun onToggleRepeat() {
        val nextMode = when (playbackManager.repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        playbackManager.setRepeatMode(nextMode)
    }

    fun playTrack(track: Track) {
        val generation = ++loadingGeneration
        playJob?.cancel()
        playJob = viewModelScope.launch {
            val isYouTubeStream = track.source == TrackSource.YOUTUBE &&
                downloadRepository.getDownloadInfo(track.id) == null

            // Resolve FIRST, mutate the queue only on success: replacing the
            // queue before resolution destroyed the previous queue on failure
            // and left the player/UI desynced (old audio under new track's UI).
            if (isYouTubeStream) {
                playbackManager.pause()
                _extraState.update { it.copy(isLoadingTrack = true) }
            }

            val resolved = try {
                resolveTrackForPlayback(track)
            } finally {
                if (isYouTubeStream && generation == loadingGeneration) {
                    _extraState.update { it.copy(isLoadingTrack = false) }
                }
            }

            // Stream resolution failed: leave the previous queue and player
            // fully intact - resolveTrackForPlayback already raised the
            // error snackbar.
            if (resolved.streamUrl == null) return@launch
            queueManager.setQueue(listOf(resolved), 0)
            playbackManager.playTrack(resolved)
            triggerProgressiveDownload(track)
            try {
                libraryRepository.addToRecent(track)
                if (track.source == TrackSource.YOUTUBE) {
                    settingsDataStore.setLastYoutubeVideoId(track.id.removePrefix("yt_"))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun playTrackInList(tracks: List<Track>, index: Int) {
        val generation = ++loadingGeneration
        playJob?.cancel()
        playJob = viewModelScope.launch {
            val targetTrack = tracks[index]
            val isYouTubeStream = targetTrack.source == TrackSource.YOUTUBE &&
                downloadRepository.getDownloadInfo(targetTrack.id) == null

            if (isYouTubeStream) {
                playbackManager.pause()
                queueManager.setQueue(tracks, index)
                _extraState.update { it.copy(isLoadingTrack = true) }
            }

            val resolvedTarget = try {
                resolveTrackForPlayback(targetTrack)
            } finally {
                if (isYouTubeStream && generation == loadingGeneration) {
                    _extraState.update { it.copy(isLoadingTrack = false) }
                }
            }

            val queueTracks = tracks.toMutableList().also { it[index] = resolvedTarget }
            playbackManager.playQueue(queueTracks, index)
            triggerProgressiveDownload(targetTrack)
            try {
                libraryRepository.addToRecent(targetTrack)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            // Resolve remaining tracks in background for seamless queue transitions
            resolveRemainingTracks(queueTracks, index)
        }
    }

    fun playAlbum(tracks: List<Track>, startIndex: Int) {
        val generation = ++loadingGeneration
        playJob?.cancel()
        playJob = viewModelScope.launch {
            val targetTrack = tracks[startIndex]
            val isYouTubeStream = targetTrack.source == TrackSource.YOUTUBE &&
                downloadRepository.getDownloadInfo(targetTrack.id) == null

            if (isYouTubeStream) {
                playbackManager.pause()
                queueManager.setQueue(tracks, startIndex)
                _extraState.update { it.copy(isLoadingTrack = true) }
            }

            val resolvedTarget = try {
                resolveTrackForPlayback(targetTrack)
            } finally {
                if (isYouTubeStream && generation == loadingGeneration) {
                    _extraState.update { it.copy(isLoadingTrack = false) }
                }
            }

            val queueTracks = tracks.toMutableList().also { it[startIndex] = resolvedTarget }
            playbackManager.playQueue(queueTracks, startIndex)
            triggerProgressiveDownload(targetTrack)
            try {
                libraryRepository.addToRecent(targetTrack)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            // Resolve remaining tracks in background for seamless queue transitions
            resolveRemainingTracks(queueTracks, startIndex)
        }
    }

    private suspend fun resolveRemainingTracks(tracks: List<Track>, skipIndex: Int) {
        // Patch each track into the live queue AS IT RESOLVES, by id and in
        // place: partial progress immediately benefits skips, and - unlike the
        // old wholesale setQueue at the end - queue edits made during the long
        // resolution window (playNext/add/remove/reorder/shuffle) survive.
        // [tracks] is a private copy; the list installed in the queue is never
        // mutated here.
        for (i in tracks.indices) {
            if (i == skipIndex) continue
            val original = tracks[i]
            val resolved = resolveTrackForPlayback(original, updateState = false)
            if (resolved != original) {
                queueManager.applyResolvedTracks(mapOf(resolved.id to resolved))
            }
        }
    }

    fun skipToQueueIndex(index: Int) {
        playbackManager.skipToQueueIndex(index)
        viewModelScope.launch {
            try {
                queueManager.queue.value.getOrNull(index)?.let { libraryRepository.addToRecent(it) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    private var favoriteJob: Job? = null

    fun onToggleFavorite() {
        if (favoriteJob?.isActive == true) return
        val track = uiState.value.currentTrack ?: return
        favoriteJob = viewModelScope.launch {
            try {
                libraryRepository.toggleTrackFavorite(track.id)
                // Queue state is patched via collectFavoriteTrackIds -> applyFavoriteIds,
                // which preserves the unshuffle snapshot. setQueue here would null it.
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    private var downloadJob: Job? = null

    fun onDownloadTrack() {
        val track = uiState.value.currentTrack ?: return
        if (track.isLocal) return // Local tracks are already on-device
        if (_extraState.value.downloadingTrackId != null) return
        _extraState.update { it.copy(downloadingTrackId = track.id) }
        downloadJob = viewModelScope.launch {
            try {
                downloadController.downloadTrackBlocking(track)
                _extraState.update {
                    it.copy(
                        downloadingTrackId = null,
                        snackbarMessage = UiText.StringResource(R.string.snackbar_downloaded, listOf(track.title)),
                        isSnackbarError = false,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _extraState.update {
                    it.copy(
                        downloadingTrackId = null,
                        snackbarMessage =
                        e.message?.let { UiText.DynamicString(it) } ?: UiText.StringResource(R.string.snackbar_download_failed),
                        isSnackbarError = true,
                    )
                }
            }
        }
    }

    fun onDeleteTrackDownload() {
        val track = uiState.value.currentTrack ?: return
        viewModelScope.launch {
            try {
                downloadAlbumUseCase.deleteTrackDownload(track.id)
                _extraState.update {
                    it.copy(
                        snackbarMessage = UiText.StringResource(R.string.snackbar_deleted, listOf(track.title)),
                        isSnackbarError = false,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _extraState.update {
                    it.copy(
                        snackbarMessage =
                        e.message?.let { UiText.DynamicString(it) } ?: UiText.StringResource(R.string.snackbar_delete_failed),
                        isSnackbarError = true,
                    )
                }
            }
        }
    }

    fun addToPlaylist(playlistId: String) {
        val track = uiState.value.currentTrack ?: return
        viewModelScope.launch {
            try {
                playlistRepository.addTrackToPlaylist(playlistId, track.id)
                val playlist = _extraState.value.playlists.find { it.id == playlistId }
                _extraState.update {
                    it.copy(
                        snackbarMessage = UiText.StringResource(
                            R.string.snackbar_added_to_playlist,
                            listOf(playlist?.name ?: UiText.StringResource(R.string.playlist_fallback_name)),
                        ),
                        isSnackbarError = false,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _extraState.update {
                    it.copy(
                        snackbarMessage =
                        e.message?.let { UiText.DynamicString(it) } ?: UiText.StringResource(R.string.snackbar_add_to_playlist_failed),
                        isSnackbarError = true,
                    )
                }
            }
        }
    }

    fun createPlaylistAndAddTrack(name: String, shapeKey: String?, iconUrl: String?) {
        val track = uiState.value.currentTrack ?: return
        viewModelScope.launch {
            try {
                val playlist = playlistRepository.createPlaylist(name, shapeKey, iconUrl)
                playlistRepository.addTrackToPlaylist(playlist.id, track.id)
                _extraState.update {
                    it.copy(
                        snackbarMessage = UiText.StringResource(R.string.snackbar_added_to_playlist, listOf(playlist.name)),
                        isSnackbarError = false,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _extraState.update {
                    it.copy(
                        snackbarMessage =
                        e.message?.let { UiText.DynamicString(it) } ?: UiText.StringResource(R.string.snackbar_create_playlist_failed),
                        isSnackbarError = true,
                    )
                }
            }
        }
    }

    /**
     * Live queue entries with stable per-insertion uids. The queue sheet keys
     * its rows and commits edits by uid, so duplicate Track.ids can't crash
     * the LazyColumn and edits can't land on a stale positional index.
     */
    val queueEntries: StateFlow<List<QueueEntry>> = queueManager.entries

    fun playQueueEntry(uid: Long) {
        val index = queueManager.entries.value.indexOfFirst { it.uid == uid }
        if (index >= 0) skipToQueueIndex(index)
    }

    fun removeQueueEntry(uid: Long) {
        queueManager.removeEntry(uid)
    }

    fun moveQueueEntry(fromUid: Long, toUid: Long) {
        queueManager.moveEntry(fromUid, toUid)
    }

    fun toggleFavoriteById(trackId: String) {
        viewModelScope.launch {
            try {
                libraryRepository.toggleTrackFavorite(trackId)
                // Same as onToggleFavorite: applyFavoriteIds runs via the DB observer
                // and preserves the unshuffle snapshot.
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun playNext(track: Track) {
        queueManager.playNext(track)
        _extraState.update {
            it.copy(
                snackbarMessage = UiText.StringResource(R.string.snackbar_playing_next, listOf(track.title)),
                isSnackbarError = false,
            )
        }
    }

    fun addToQueue(track: Track) {
        queueManager.addToQueue(track)
        _extraState.update {
            it.copy(
                snackbarMessage = UiText.PluralsResource(R.plurals.snackbar_added_n_to_queue, 1),
                isSnackbarError = false,
            )
        }
    }

    fun addAllToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        for (t in tracks) queueManager.addToQueue(t)
        _extraState.update {
            it.copy(
                snackbarMessage = UiText.PluralsResource(R.plurals.snackbar_added_n_to_queue, tracks.size),
                isSnackbarError = false,
            )
        }
    }

    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            try {
                playlistRepository.addTrackToPlaylist(playlistId, trackId)
                val playlist = _extraState.value.playlists.find { it.id == playlistId }
                _extraState.update {
                    it.copy(
                        snackbarMessage = UiText.StringResource(
                            R.string.snackbar_added_to_playlist,
                            listOf(playlist?.name ?: UiText.StringResource(R.string.playlist_fallback_name)),
                        ),
                        isSnackbarError = false,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _extraState.update {
                    it.copy(
                        snackbarMessage =
                        e.message?.let { UiText.DynamicString(it) } ?: UiText.StringResource(R.string.snackbar_add_to_playlist_failed),
                        isSnackbarError = true,
                    )
                }
            }
        }
    }

    fun createPlaylistAndAddArbitraryTrack(name: String, shapeKey: String?, iconUrl: String?, trackId: String) {
        viewModelScope.launch {
            try {
                val playlist = playlistRepository.createPlaylist(name, shapeKey, iconUrl)
                playlistRepository.addTrackToPlaylist(playlist.id, trackId)
                _extraState.update {
                    it.copy(
                        snackbarMessage = UiText.StringResource(R.string.snackbar_added_to_playlist, listOf(playlist.name)),
                        isSnackbarError = false,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _extraState.update {
                    it.copy(
                        snackbarMessage =
                        e.message?.let { UiText.DynamicString(it) } ?: UiText.StringResource(R.string.snackbar_create_playlist_failed),
                        isSnackbarError = true,
                    )
                }
            }
        }
    }

    fun clearSnackbar() {
        _extraState.update { it.copy(snackbarMessage = null) }
    }

    /** Feedback for the full-player Album button when the video has no YTM album. */
    fun showNoAlbumSnackbar() {
        _extraState.update {
            it.copy(
                snackbarMessage = UiText.StringResource(R.string.snackbar_no_album_for_track),
                isSnackbarError = false,
            )
        }
    }

    companion object {
        /**
         * Freshness window for resolved remote stream URLs. YouTube googlevideo
         * links expire after ~6 h and Bandcamp CDN tokens after a few hours;
         * one hour is comfortably inside both, and a spurious re-resolve only
         * costs one metadata request before playback.
         */
        private const val STREAM_URL_TTL_MS = 60L * 60L * 1000L
    }
}
