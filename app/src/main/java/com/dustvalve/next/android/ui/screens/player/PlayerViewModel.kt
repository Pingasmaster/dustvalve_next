package com.dustvalve.next.android.ui.screens.player

import android.media.AudioDeviceInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.domain.model.RepeatMode
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.player.QueueEntry
import com.dustvalve.next.android.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PlayerUiState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentQueueIndex: Int = -1,
    val isPlaying: Boolean = false,
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
    val playerDebugOverlay: Boolean = false,
)

/** 5 Hz position tick, split from [PlayerUiState]; see [PlayerViewModel.positionState]. */
data class PlaybackPositionState(val positionMs: Long = 0L, val durationMs: Long = 0L)

@HiltViewModel
class PlayerViewModel @Inject constructor(core: PlayerCoreDeps, libraryDeps: PlayerLibraryDeps) : ViewModel() {

    private val playbackManager = core.playbackManager
    private val queueManager = core.queueManager
    private val settingsDataStore = core.settingsDataStore

    private val _extraState = MutableStateFlow(PlayerExtraState())

    internal val audio = PlayerAudioController(core.appContext, playbackManager)

    internal val play = PlayerPlayCoordinator(
        scope = viewModelScope,
        extraState = _extraState,
        core = core,
    )

    internal val library = PlayerLibraryCoordinator(
        scope = viewModelScope,
        extraState = _extraState,
        core = core,
        libraryDeps = libraryDeps,
        currentTrack = { queueManager.currentTrack.value },
    )

    init {
        PlayerStateCollectors(
            scope = viewModelScope,
            extraState = _extraState,
            playbackManager = playbackManager,
            queueManager = queueManager,
            downloadRepository = core.downloadRepository,
            playlistRepository = libraryDeps.playlistRepository,
            favoriteRepository = libraryDeps.favoriteRepository,
            playbackStreamResolver = core.playbackStreamResolver,
        ).start()
        val resolver = core.playbackStreamResolver
        playbackManager.streamIsStale = resolver::isResolutionStale
        playbackManager.streamResolver = { track -> resolver.resolveOnDemand(track) }
        playbackManager.onPlayAfterError = { track ->
            resolver.clearAutoRetry(track.id)
            resolver.invalidateResolution(track.id)
        }
        audio.register()
    }

    override fun onCleared() {
        audio.unregister()
    }

    val uiState: StateFlow<PlayerUiState> = combine(
        queueManager.currentTrack,
        queueManager.queue,
        playbackManager.isPlaying,
    ) { currentTrack, queue, isPlaying ->
        PlayerUiState(
            currentTrack = currentTrack,
            queue = queue,
            isPlaying = isPlaying,
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
        val maxVol = audio.streamMaxVolume
        val curVol = audio.streamVolume
        state.copy(
            showInlineVolumeSlider = inline,
            showVolumeButton = button,
            maxVolumeLevel = maxVol,
            volumeLevel = if (maxVol > 0) curVol.toFloat() / maxVol else 1f,
        )
    }.combine(
        combine(audio.audioDevices, audio.activeAudioDevice) { devices, active -> devices to active },
    ) { state, (devices, active) ->
        state.copy(
            audioOutputDevices = devices,
            activeAudioDevice = active,
        )
    }.combine(settingsDataStore.playerDebugOverlay) { state, carousel ->
        state.copy(playerDebugOverlay = carousel)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlayerUiState(),
    )

    val positionState: StateFlow<PlaybackPositionState> = combine(
        playbackManager.currentPosition,
        playbackManager.duration,
    ) { position, duration ->
        PlaybackPositionState(positionMs = position, durationMs = duration)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlaybackPositionState(),
    )

    val queueEntries: StateFlow<List<QueueEntry>> = queueManager.entries
}
