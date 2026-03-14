package com.dustvalve.next.android.ui.screens.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val isDownloading: Boolean = false,
    val downloadedTrackIds: Set<String> = emptySet(),
    val error: String? = null,
    val snackbarMessage: String? = null,
    val isSnackbarError: Boolean = false,
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val downloadAlbumUseCase: DownloadAlbumUseCase,
    private val downloadRepository: DownloadRepository,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    private var currentPlaylistId: String? = null
    private var downloadJob: Job? = null
    var retryAction: (() -> Unit)? = null
        private set

    init {
        collectDownloadedTrackIds()
    }

    private fun collectDownloadedTrackIds() {
        viewModelScope.launch {
            downloadRepository.getDownloadedTrackIds()
                .catch { /* ignore */ }
                .collect { ids ->
                    _uiState.update { it.copy(downloadedTrackIds = ids.toSet()) }
                }
        }
    }

    fun loadPlaylist(playlistId: String) {
        if (currentPlaylistId == playlistId) return
        currentPlaylistId = playlistId

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            combine(
                playlistRepository.getPlaylistById(playlistId),
                playlistRepository.getTracksInPlaylist(playlistId),
            ) { playlist, tracks ->
                playlist to tracks
            }
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            error = e.message ?: "Failed to load playlist",
                            isLoading = false,
                        )
                    }
                }
                .collect { (playlist, tracks) ->
                    _uiState.update {
                        it.copy(
                            playlist = playlist,
                            tracks = tracks,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
        }
    }

    fun downloadAll() {
        if (downloadJob?.isActive == true) return
        val tracks = _uiState.value.tracks
        val playlist = _uiState.value.playlist
        if (tracks.isEmpty()) return

        _uiState.update { it.copy(isDownloading = true) }
        downloadJob = viewModelScope.launch {
            try {
                tracks.forEach { track ->
                    downloadAlbumUseCase.downloadTrack(track)
                }
                // Enable auto-download for this playlist if the global setting is on
                if (settingsDataStore.getAutoDownloadFutureContentSync()) {
                    currentPlaylistId?.let { id ->
                        playlistRepository.setAutoDownload(id, true)
                    }
                }
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        snackbarMessage = "Downloaded all tracks in ${playlist?.name ?: "playlist"}",
                        isSnackbarError = false,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                retryAction = { downloadAll() }
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        snackbarMessage = e.message ?: "Download failed",
                        isSnackbarError = true,
                    )
                }
            }
        }
    }

    fun removeTrack(trackId: String) {
        viewModelScope.launch {
            try {
                currentPlaylistId?.let { playlistId ->
                    playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(error = e.message ?: "Failed to remove track") }
            }
        }
    }

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            try {
                currentPlaylistId?.let { playlistId ->
                    playlistRepository.moveTrackInPlaylist(playlistId, fromIndex, toIndex)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(error = e.message ?: "Failed to move track") }
            }
        }
    }

    fun refreshPlaylist() {
        currentPlaylistId?.let {
            currentPlaylistId = null
            loadPlaylist(it)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
