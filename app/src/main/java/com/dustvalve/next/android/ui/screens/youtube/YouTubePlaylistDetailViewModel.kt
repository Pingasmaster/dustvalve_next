package com.dustvalve.next.android.ui.screens.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.repository.YouTubeRepository
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

data class YouTubePlaylistDetailUiState(
    val playlistName: String = "",
    val playlistUrl: String = "",
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isImported: Boolean = false,
    val isImporting: Boolean = false,
    val isFavorite: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadedTrackIds: Set<String> = emptySet(),
)

@HiltViewModel
class YouTubePlaylistDetailViewModel @Inject constructor(
    private val youtubeRepository: YouTubeRepository,
    private val playlistRepository: PlaylistRepository,
    private val trackDao: TrackDao,
    private val database: DustvalveNextDatabase,
    private val favoriteDao: FavoriteDao,
    private val downloadRepository: DownloadRepository,
    private val downloadAlbumUseCase: DownloadAlbumUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(YouTubePlaylistDetailUiState())
    val uiState: StateFlow<YouTubePlaylistDetailUiState> = _uiState.asStateFlow()

    private var loadedUrl: String? = null

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

    fun loadPlaylist(url: String, name: String) {
        if (loadedUrl == url && _uiState.value.tracks.isNotEmpty()) return
        loadedUrl = url
        _uiState.update { it.copy(playlistUrl = url, playlistName = name, isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val (tracks, fetchedName) = youtubeRepository.getPlaylistTracks(url)
                val isFav = favoriteDao.isFavorite(url)
                _uiState.update {
                    it.copy(
                        tracks = tracks,
                        playlistName = if (it.playlistName.isBlank()) fetchedName else it.playlistName,
                        isLoading = false,
                        isFavorite = isFav,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load playlist")
                }
            }
        }
    }

    fun importToLibrary() {
        val state = _uiState.value
        if (state.isImported || state.isImporting || state.tracks.isEmpty()) return
        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            try {
                database.withTransaction {
                    trackDao.insertAll(state.tracks.map { it.toEntity() })
                    val playlist = playlistRepository.createPlaylist(state.playlistName)
                    playlistRepository.addTracksToPlaylist(playlist.id, state.tracks.map { it.id })
                }
                _uiState.update { it.copy(isImported = true, isImporting = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isImporting = false, error = "Failed to import: ${e.message}") }
            }
        }
    }

    fun toggleFavorite() {
        val url = _uiState.value.playlistUrl
        if (url.isBlank()) return
        val prev = _uiState.value.isFavorite
        _uiState.update { it.copy(isFavorite = !prev) }
        viewModelScope.launch {
            try {
                if (prev) {
                    favoriteDao.delete(url)
                } else {
                    favoriteDao.insert(FavoriteEntity(id = url, type = "youtube_playlist"))
                    importToLibrary()
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
                } catch (_: Exception) { }
            }
        }
    }
}
