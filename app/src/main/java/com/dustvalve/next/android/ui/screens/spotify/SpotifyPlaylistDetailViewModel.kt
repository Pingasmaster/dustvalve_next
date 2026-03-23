package com.dustvalve.next.android.ui.screens.spotify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.PlaylistDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.PlaylistRepository
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

data class SpotifyPlaylistDetailUiState(
    val playlistName: String = "",
    val playlistUri: String = "",
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isImported: Boolean = false,
    val isImporting: Boolean = false,
    val importedPlaylistId: String? = null,
    val isFavorite: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadedTrackIds: Set<String> = emptySet(),
)

@HiltViewModel
class SpotifyPlaylistDetailViewModel @Inject constructor(
    private val spotifyRepository: SpotifyRepository,
    private val playlistRepository: PlaylistRepository,
    private val trackDao: TrackDao,
    private val database: DustvalveNextDatabase,
    private val favoriteDao: FavoriteDao,
    private val playlistDao: PlaylistDao,
    private val downloadRepository: DownloadRepository,
    private val downloadAlbumUseCase: DownloadAlbumUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpotifyPlaylistDetailUiState())
    val uiState: StateFlow<SpotifyPlaylistDetailUiState> = _uiState.asStateFlow()

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

    fun loadPlaylist(uri: String, name: String) {
        if (loadedUri == uri && _uiState.value.tracks.isNotEmpty()) return
        loadedUri = uri
        _uiState.update { it.copy(playlistUri = uri, playlistName = name, isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val (tracks, fetchedName) = spotifyRepository.getPlaylistTracks(uri)
                val isFav = favoriteDao.isFavorite(uri)
                val displayName = if (_uiState.value.playlistName.isBlank()) fetchedName else _uiState.value.playlistName
                val existingPlaylist = playlistDao.getPlaylistByName(displayName)
                _uiState.update {
                    it.copy(
                        tracks = tracks,
                        playlistName = displayName,
                        isLoading = false,
                        isFavorite = isFav,
                        isImported = existingPlaylist != null,
                        importedPlaylistId = existingPlaylist?.id,
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
                var playlistId: String? = null
                database.withTransaction {
                    trackDao.insertAll(state.tracks.map { it.toEntity() })
                    val playlist = playlistRepository.createPlaylist(state.playlistName)
                    playlistId = playlist.id
                    playlistRepository.addTracksToPlaylist(playlist.id, state.tracks.map { it.id })
                }
                _uiState.update { it.copy(isImported = true, isImporting = false, importedPlaylistId = playlistId) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isImporting = false, error = "Failed to import: ${e.message}") }
            }
        }
    }

    fun toggleFavorite() {
        val uri = _uiState.value.playlistUri
        if (uri.isBlank()) return
        val prev = _uiState.value.isFavorite
        _uiState.update { it.copy(isFavorite = !prev) }
        viewModelScope.launch {
            try {
                if (prev) {
                    favoriteDao.delete(uri)
                    val playlistId = _uiState.value.importedPlaylistId
                        ?: playlistDao.getPlaylistByName(_uiState.value.playlistName)?.id
                    if (playlistId != null) {
                        playlistRepository.deletePlaylist(playlistId)
                        _uiState.update { it.copy(isImported = false, importedPlaylistId = null) }
                    }
                } else {
                    favoriteDao.insert(FavoriteEntity(id = uri, type = "spotify_playlist"))
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
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }
    }
}
