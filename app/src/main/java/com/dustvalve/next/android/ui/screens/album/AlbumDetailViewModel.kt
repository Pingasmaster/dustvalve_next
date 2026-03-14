package com.dustvalve.next.android.ui.screens.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.repository.AlbumRepository
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import com.dustvalve.next.android.domain.usecase.GetAlbumDetailUseCase
import com.dustvalve.next.android.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class AlbumDetailUiState(
    val album: Album? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isDownloading: Boolean = false,
    val downloadingTrackIds: Set<String> = emptySet(),
    val downloadedTrackIds: Set<String> = emptySet(),
    val snackbarMessage: String? = null,
    val isSnackbarError: Boolean = false,
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val getAlbumDetailUseCase: GetAlbumDetailUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val downloadAlbumUseCase: DownloadAlbumUseCase,
    private val downloadRepository: DownloadRepository,
    private val albumRepository: AlbumRepository,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    private var favoriteJob: Job? = null
    private var loadJob: Job? = null
    private var loadedUrl: String? = null
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

    fun loadAlbum(url: String) {
        if (loadedUrl == url && _uiState.value.album != null) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val isNewUrl = loadedUrl != null && loadedUrl != url
            _uiState.update { it.copy(isLoading = true, error = null, album = if (isNewUrl) null else it.album) }
            try {
                albumRepository.getAlbumDetailFlow(url)
                    .collect { album ->
                        loadedUrl = url
                        _uiState.update {
                            it.copy(
                                album = album,
                                isLoading = false,
                                error = null,
                            )
                        }
                    }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load album",
                    )
                }
            }
        }
    }

    fun toggleFavorite() {
        if (favoriteJob?.isActive == true) return
        val album = _uiState.value.album ?: return
        val previousFavorite = album.isFavorite
        _uiState.update {
            it.copy(album = it.album?.copy(isFavorite = !previousFavorite))
        }
        favoriteJob = viewModelScope.launch {
            try {
                toggleFavoriteUseCase.toggleAlbumFavorite(album.id)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(album = it.album?.copy(isFavorite = previousFavorite))
                }
            }
        }
    }

    fun toggleTrackFavorite(trackId: String) {
        val album = _uiState.value.album ?: return
        val trackIndex = album.tracks.indexOfFirst { it.id == trackId }
        if (trackIndex == -1) return
        val track = album.tracks[trackIndex]
        val previousFavorite = track.isFavorite

        // Optimistic update
        val updatedTracks = album.tracks.toMutableList()
        updatedTracks[trackIndex] = track.copy(isFavorite = !previousFavorite)
        _uiState.update { it.copy(album = it.album?.copy(tracks = updatedTracks)) }

        viewModelScope.launch {
            try {
                toggleFavoriteUseCase.toggleTrackFavorite(trackId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Rollback
                val currentAlbum = _uiState.value.album ?: return@launch
                val rollbackTracks = currentAlbum.tracks.toMutableList()
                val idx = rollbackTracks.indexOfFirst { it.id == trackId }
                if (idx >= 0) {
                    rollbackTracks[idx] = rollbackTracks[idx].copy(isFavorite = previousFavorite)
                    _uiState.update { it.copy(album = it.album?.copy(tracks = rollbackTracks)) }
                }
            }
        }
    }

    fun downloadTrack(track: Track) {
        if (track.id in _uiState.value.downloadingTrackIds) return
        _uiState.update { it.copy(downloadingTrackIds = it.downloadingTrackIds + track.id) }
        viewModelScope.launch {
            try {
                downloadAlbumUseCase.downloadTrack(track)
                _uiState.update {
                    it.copy(snackbarMessage = "Downloaded '${track.title}'", isSnackbarError = false)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                retryAction = { downloadTrack(track) }
                _uiState.update {
                    it.copy(snackbarMessage = e.message ?: "Download failed", isSnackbarError = true)
                }
            } finally {
                _uiState.update { it.copy(downloadingTrackIds = it.downloadingTrackIds - track.id) }
            }
        }
    }

    fun downloadAlbum() {
        val album = _uiState.value.album ?: return
        if (_uiState.value.isDownloading) return

        _uiState.update { it.copy(isDownloading = true) }
        viewModelScope.launch {
            try {
                downloadAlbumUseCase(album)
                if (settingsDataStore.getAutoDownloadFutureContentSync()) {
                    albumRepository.setAutoDownload(album.id, true)
                }
                _uiState.update {
                    it.copy(isDownloading = false, snackbarMessage = "Downloaded '${album.title}'", isSnackbarError = false)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                retryAction = { downloadAlbum() }
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

    fun deleteAlbumDownloads() {
        val album = _uiState.value.album ?: return
        viewModelScope.launch {
            try {
                downloadAlbumUseCase.deleteAlbumDownloads(album.id)
                _uiState.update {
                    it.copy(snackbarMessage = "Deleted downloads for '${album.title}'", isSnackbarError = false)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(snackbarMessage = e.message ?: "Delete failed", isSnackbarError = true)
                }
            }
        }
    }

    fun deleteTrackDownload(track: Track) {
        viewModelScope.launch {
            try {
                downloadAlbumUseCase.deleteTrackDownload(track.id)
                _uiState.update {
                    it.copy(snackbarMessage = "Deleted '${track.title}'", isSnackbarError = false)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(snackbarMessage = e.message ?: "Delete failed", isSnackbarError = true)
                }
            }
        }
    }

    fun clearSnackbar() {
        retryAction = null
        _uiState.update { it.copy(snackbarMessage = null, isSnackbarError = false) }
    }
}
