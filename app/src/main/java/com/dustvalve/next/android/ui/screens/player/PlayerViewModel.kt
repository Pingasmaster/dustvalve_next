package com.dustvalve.next.android.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.media.AudioManager
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.domain.model.RepeatMode
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.LibraryRepository
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import com.dustvalve.next.android.player.PlaybackManager
import com.dustvalve.next.android.player.QueueManager
import com.dustvalve.next.android.util.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

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
    val downloadedTrackIds: Set<String> = emptySet(),
    val downloadingTrackId: String? = null,
    val playlists: List<Playlist> = emptyList(),
    val snackbarMessage: String? = null,
    val isSnackbarError: Boolean = false,
    val currentPlaybackFormat: AudioFormat? = null,
    val currentSourcePath: String? = null,
    val wavyProgressBar: Boolean = true,
    val userPlaylistTrackIds: Set<String> = emptySet(),
    val volumeLevel: Float = 1f,
    val maxVolumeLevel: Int = 15,
    val showInlineVolumeSlider: Boolean = false,
    val showVolumeButton: Boolean = false,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    private val libraryRepository: LibraryRepository,
    private val downloadAlbumUseCase: DownloadAlbumUseCase,
    private val downloadRepository: DownloadRepository,
    private val playlistRepository: PlaylistRepository,
    private val settingsDataStore: SettingsDataStore,
    private val youtubeRepository: YouTubeRepository,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _extraState = MutableStateFlow(ExtraState())

    private data class ExtraState(
        val downloadedTrackIds: Set<String> = emptySet(),
        val downloadingTrackId: String? = null,
        val playlists: List<Playlist> = emptyList(),
        val snackbarMessage: String? = null,
        val isSnackbarError: Boolean = false,
        val currentPlaybackFormat: AudioFormat? = null,
        val currentSourcePath: String? = null,
        val userPlaylistTrackIds: Set<String> = emptySet(),
    )

    private var progressiveDownloadJob: Job? = null

    init {
        collectDownloadedTrackIds()
        collectPlaylists()
        collectUserPlaylistTrackIds()
    }

    /**
     * Resolves the best available stream URL for a track:
     * 1. If downloaded locally at same-or-higher quality → use local file
     * 2. Otherwise → use the track's stream URL (mp3-128)
     *
     * Also triggers a progressive background download if enabled.
     */
    private suspend fun resolveTrackForPlayback(track: Track): Track {
        // Local tracks already have a content:// URI — use as-is
        if (track.isLocal) {
            _extraState.update {
                it.copy(
                    currentPlaybackFormat = null,
                    currentSourcePath = track.streamUrl,
                )
            }
            return track
        }

        // YouTube tracks: check download first, then resolve stream URL live
        if (track.source == TrackSource.YOUTUBE) {
            val ytDownloadInfo = downloadRepository.getDownloadInfo(track.id)
            if (ytDownloadInfo != null) {
                _extraState.update {
                    it.copy(
                        currentPlaybackFormat = ytDownloadInfo.format,
                        currentSourcePath = ytDownloadInfo.filePath,
                    )
                }
                return track.copy(streamUrl = android.net.Uri.fromFile(File(ytDownloadInfo.filePath)).toString())
            }
            // Resolve stream URL from YouTube
            return try {
                val streamUrl = youtubeRepository.getStreamUrl(track.streamUrl ?: return track)
                _extraState.update {
                    it.copy(currentPlaybackFormat = null, currentSourcePath = null)
                }
                track.copy(streamUrl = streamUrl)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _extraState.update {
                    it.copy(
                        snackbarMessage = "Couldn't load audio stream",
                        isSnackbarError = true,
                    )
                }
                track // Fall back, ExoPlayer will handle gracefully
            }
        }

        // Check for existing local download
        val downloadInfo = downloadRepository.getDownloadInfo(track.id)
        if (downloadInfo != null && downloadInfo.format.qualityRank >= AudioFormat.MP3_128.qualityRank) {
            // Already have a same-or-higher quality local file — use it
            _extraState.update {
                it.copy(
                    currentPlaybackFormat = downloadInfo.format,
                    currentSourcePath = downloadInfo.filePath,
                )
            }
            return track.copy(streamUrl = android.net.Uri.fromFile(File(downloadInfo.filePath)).toString())
        }

        // No local download — use original stream URL (mp3-128)
        _extraState.update {
            it.copy(
                currentPlaybackFormat = AudioFormat.MP3_128,
                currentSourcePath = null,
            )
        }
        return track
    }

    /**
     * Triggers a progressive HQ download in the background after playback starts.
     * The next time this track is played, the local HQ file will be used.
     */
    private fun triggerProgressiveDownload(track: Track) {
        if (track.isLocal) return // Local tracks don't need downloading
        if (track.source == TrackSource.YOUTUBE) return // YouTube tracks use a different download path
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

                // On metered + save-data enabled → download MP3-320 instead of preferred format
                val saveOnMetered = settingsDataStore.getSaveDataOnMeteredSync()
                val formatOverride = if (saveOnMetered && NetworkUtils.isMeteredConnection(appContext)) {
                    AudioFormat.MP3_320
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
                        playbackManager.hotSwapSource(downloadInfo.filePath, track.id)
                        _extraState.update {
                            it.copy(
                                currentPlaybackFormat = downloadInfo.format,
                                currentSourcePath = downloadInfo.filePath,
                            )
                        }
                    }
                }

                // Current track download complete — precache next track in queue
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

        // Already downloaded — nothing to do
        val existing = downloadRepository.getDownloadInfo(nextTrack.id)
        if (existing != null && existing.format.qualityRank >= AudioFormat.MP3_128.qualityRank) return

        // Already being manually downloaded — don't duplicate
        if (_extraState.value.downloadingTrackId == nextTrack.id) return

        try {
            val saveOnMetered = settingsDataStore.getSaveDataOnMeteredSync()
            val formatOverride = if (saveOnMetered && NetworkUtils.isMeteredConnection(appContext)) {
                AudioFormat.MP3_320
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
        ) { shuffle, repeat, index -> Triple(shuffle, repeat, index) }
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
        )
    }.combine(settingsDataStore.wavyProgressBar) { state, wavy ->
        state.copy(wavyProgressBar = wavy)
    }.combine(
        combine(
            settingsDataStore.showInlineVolumeSlider,
            settingsDataStore.showVolumeButton,
        ) { inline, button -> inline to button }
    ) { state, (inline, button) ->
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        state.copy(
            showInlineVolumeSlider = inline,
            showVolumeButton = button,
            maxVolumeLevel = maxVol,
            volumeLevel = if (maxVol > 0) curVol.toFloat() / maxVol else 1f,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlayerUiState(),
    )

    fun setVolume(level: Float) {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVol = (level * maxVol).toInt().coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
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
        viewModelScope.launch {
            val resolved = resolveTrackForPlayback(track)
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
        viewModelScope.launch {
            // Resolve the target track first for immediate playback, then resolve the rest
            val mutableTracks = tracks.toMutableList()
            mutableTracks[index] = resolveTrackForPlayback(tracks[index])
            playbackManager.playQueue(mutableTracks, index)
            triggerProgressiveDownload(tracks[index])
            try {
                libraryRepository.addToRecent(tracks[index])
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            // Resolve remaining tracks in background for seamless queue transitions
            resolveRemainingTracks(mutableTracks, index)
        }
    }

    fun playAlbum(tracks: List<Track>, startIndex: Int) {
        viewModelScope.launch {
            // Resolve the target track first for immediate playback, then resolve the rest
            val mutableTracks = tracks.toMutableList()
            mutableTracks[startIndex] = resolveTrackForPlayback(tracks[startIndex])
            playbackManager.playQueue(mutableTracks, startIndex)
            triggerProgressiveDownload(tracks[startIndex])
            try {
                libraryRepository.addToRecent(tracks[startIndex])
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            // Resolve remaining tracks in background for seamless queue transitions
            resolveRemainingTracks(mutableTracks, startIndex)
        }
    }

    private suspend fun resolveRemainingTracks(tracks: MutableList<Track>, skipIndex: Int) {
        val originalTrackId = tracks.getOrNull(skipIndex)?.id
        for (i in tracks.indices) {
            if (i == skipIndex) continue
            tracks[i] = resolveTrackForPlayback(tracks[i])
        }
        // Only update the queue if the user hasn't switched to different content
        val currentTrack = queueManager.currentTrack.value
        if (currentTrack != null && currentTrack.id == originalTrackId) {
            val currentIndex = queueManager.currentIndex.value
            queueManager.setQueue(tracks, currentIndex)
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
                val newIsFavorite = libraryRepository.toggleTrackFavorite(track.id)
                // Update the track's isFavorite in the queue using the actual DB state
                val currentQueue = queueManager.queue.value
                val currentIndex = queueManager.currentIndex.value
                if (currentIndex !in currentQueue.indices) return@launch
                val updatedQueue = currentQueue.toMutableList()
                for (i in updatedQueue.indices) {
                    if (updatedQueue[i].id == track.id) {
                        updatedQueue[i] = updatedQueue[i].copy(isFavorite = newIsFavorite)
                    }
                }
                queueManager.setQueue(updatedQueue, currentIndex)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // DB call failed — don't update queue, UI stays in sync with DB
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
                downloadAlbumUseCase.downloadTrack(track)
                _extraState.update {
                    it.copy(
                        downloadingTrackId = null,
                        snackbarMessage = "Downloaded '${track.title}'",
                        isSnackbarError = false,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _extraState.update {
                    it.copy(
                        downloadingTrackId = null,
                        snackbarMessage = e.message ?: "Download failed",
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
                        snackbarMessage = "Deleted '${track.title}'",
                        isSnackbarError = false,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _extraState.update {
                    it.copy(
                        snackbarMessage = e.message ?: "Delete failed",
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
                        snackbarMessage = "Added to ${playlist?.name ?: "playlist"}",
                        isSnackbarError = false,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _extraState.update {
                    it.copy(
                        snackbarMessage = e.message ?: "Failed to add to playlist",
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
                        snackbarMessage = "Added to ${playlist.name}",
                        isSnackbarError = false,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _extraState.update {
                    it.copy(
                        snackbarMessage = e.message ?: "Failed to create playlist",
                        isSnackbarError = true,
                    )
                }
            }
        }
    }

    fun clearSnackbar() {
        _extraState.update { it.copy(snackbarMessage = null) }
    }
}
