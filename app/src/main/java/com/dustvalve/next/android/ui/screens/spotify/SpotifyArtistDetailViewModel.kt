package com.dustvalve.next.android.ui.screens.spotify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.data.local.db.dao.ArtistDao
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.ArtistEntity
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.data.remote.spotify.AlbumInfo
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

data class SpotifyArtistDetailUiState(
    val artistName: String = "",
    val artistUri: String = "",
    val imageUrl: String? = null,
    val topTracks: List<Track> = emptyList(),
    val albums: List<AlbumInfo> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isFavorite: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadedTrackIds: Set<String> = emptySet(),
)

@HiltViewModel
class SpotifyArtistDetailViewModel @Inject constructor(
    private val spotifyRepository: SpotifyRepository,
    private val favoriteDao: FavoriteDao,
    private val downloadRepository: DownloadRepository,
    private val downloadAlbumUseCase: DownloadAlbumUseCase,
    private val artistDao: ArtistDao,
    private val trackDao: TrackDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpotifyArtistDetailUiState())
    val uiState: StateFlow<SpotifyArtistDetailUiState> = _uiState.asStateFlow()

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

    fun loadArtist(uri: String, name: String, imageUrl: String?) {
        if (loadedUri == uri && _uiState.value.topTracks.isNotEmpty()) return
        loadedUri = uri
        _uiState.update {
            it.copy(
                artistName = name, artistUri = uri, imageUrl = imageUrl,
                isLoading = true, error = null, topTracks = emptyList(), albums = emptyList(),
            )
        }
        viewModelScope.launch {
            try {
                val artistInfo = spotifyRepository.getArtistInfo(uri)
                val isFav = favoriteDao.isFavorite(uri)

                // Persist artist
                artistDao.insert(
                    ArtistEntity(
                        id = uri,
                        name = artistInfo.name,
                        url = uri,
                        imageUrl = artistInfo.imageUrl ?: imageUrl,
                        bio = null,
                        location = null,
                        source = "spotify",
                    )
                )

                // Persist tracks
                if (artistInfo.topTracks.isNotEmpty()) {
                    trackDao.insertAll(artistInfo.topTracks.map { it.toEntity() })
                }

                _uiState.update {
                    it.copy(
                        topTracks = artistInfo.topTracks,
                        albums = artistInfo.albums,
                        artistName = artistInfo.name,
                        imageUrl = artistInfo.imageUrl ?: imageUrl,
                        isLoading = false,
                        isFavorite = isFav,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load artist")
                }
            }
        }
    }

    fun toggleFavorite() {
        val state = _uiState.value
        val uri = state.artistUri
        if (uri.isBlank()) return
        val prev = state.isFavorite
        _uiState.update { it.copy(isFavorite = !prev) }
        viewModelScope.launch {
            try {
                if (prev) {
                    favoriteDao.delete(uri)
                } else {
                    artistDao.insert(
                        ArtistEntity(
                            id = uri,
                            name = state.artistName,
                            url = uri,
                            imageUrl = state.imageUrl,
                            bio = null,
                            location = null,
                            source = "spotify",
                        )
                    )
                    favoriteDao.insert(FavoriteEntity(id = uri, type = "artist"))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isFavorite = prev) }
            }
        }
    }

    fun downloadAll() {
        val tracks = _uiState.value.topTracks
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
            for (track in _uiState.value.topTracks) {
                try {
                    downloadAlbumUseCase.deleteTrackDownload(track.id)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }
    }
}
