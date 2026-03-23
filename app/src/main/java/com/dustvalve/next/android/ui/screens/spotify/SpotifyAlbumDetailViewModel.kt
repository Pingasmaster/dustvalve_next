package com.dustvalve.next.android.ui.screens.spotify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.SpotifyRepository
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class SpotifyAlbumDetailUiState(
    val albumName: String = "",
    val albumUri: String = "",
    val imageUrl: String? = null,
    val artistName: String = "",
    val artistUri: String = "",
    val releaseDate: String? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isFavorite: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadedTrackIds: Set<String> = emptySet(),
)

@HiltViewModel
class SpotifyAlbumDetailViewModel @Inject constructor(
    private val spotifyRepository: SpotifyRepository,
    private val favoriteDao: FavoriteDao,
    private val downloadRepository: DownloadRepository,
    private val downloadAlbumUseCase: DownloadAlbumUseCase,
    private val trackDao: TrackDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpotifyAlbumDetailUiState())
    val uiState: StateFlow<SpotifyAlbumDetailUiState> = _uiState.asStateFlow()

    private var loadedUri: String? = null

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

    fun loadAlbum(uri: String, name: String, imageUrl: String?) {
        if (loadedUri == uri && _uiState.value.tracks.isNotEmpty()) return
        loadedUri = uri
        _uiState.update {
            it.copy(
                albumUri = uri, albumName = name, imageUrl = imageUrl,
                isLoading = true, error = null, tracks = emptyList(),
            )
        }
        viewModelScope.launch {
            try {
                val (tracks, albumInfo) = spotifyRepository.getAlbumTracks(uri)
                val isFav = favoriteDao.isFavorite(uri)

                // Persist tracks
                if (tracks.isNotEmpty()) {
                    trackDao.insertAll(tracks.map { it.toEntity() })
                }

                _uiState.update {
                    it.copy(
                        tracks = tracks,
                        albumName = albumInfo.name,
                        artistName = albumInfo.artist,
                        artistUri = albumInfo.artistUri,
                        imageUrl = albumInfo.imageUrl ?: imageUrl,
                        releaseDate = albumInfo.releaseDate,
                        isLoading = false,
                        isFavorite = isFav,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load album")
                }
            }
        }
    }

    fun toggleFavorite() {
        val uri = _uiState.value.albumUri
        if (uri.isBlank()) return
        val prev = _uiState.value.isFavorite
        _uiState.update { it.copy(isFavorite = !prev) }
        viewModelScope.launch {
            try {
                if (prev) {
                    favoriteDao.delete(uri)
                } else {
                    favoriteDao.insert(FavoriteEntity(id = uri, type = "spotify_album"))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isFavorite = prev) }
            }
        }
    }

    fun downloadAll() {
        val tracks = _uiState.value.tracks
        if (tracks.isEmpty() || _uiState.value.isDownloading) return
        _uiState.update { it.copy(isDownloading = true) }
        viewModelScope.launch {
            try {
                for (track in tracks) {
                    if (track.id !in _uiState.value.downloadedTrackIds) {
                        downloadAlbumUseCase.downloadTrack(track)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            _uiState.update { it.copy(isDownloading = false) }
        }
    }

    fun deleteAllDownloads() {
        viewModelScope.launch {
            for (track in _uiState.value.tracks) {
                try {
                    downloadAlbumUseCase.deleteTrackDownload(track.id)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }
    }
}
