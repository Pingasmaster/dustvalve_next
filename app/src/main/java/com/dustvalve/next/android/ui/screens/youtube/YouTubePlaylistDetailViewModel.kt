package com.dustvalve.next.android.ui.screens.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

@HiltViewModel
class YouTubePlaylistDetailViewModel @Inject constructor(
    private val youtubeRepository: YouTubeRepository,
    private val playlistRepository: PlaylistRepository,
    private val trackDao: TrackDao,
    private val database: DustvalveNextDatabase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(YouTubePlaylistDetailUiState())
    val uiState: StateFlow<YouTubePlaylistDetailUiState> = _uiState.asStateFlow()

    private var loadedUrl: String? = null

    fun loadPlaylist(url: String, name: String) {
        if (loadedUrl == url && _uiState.value.tracks.isNotEmpty()) return
        loadedUrl = url
        _uiState.update { it.copy(playlistUrl = url, playlistName = name, isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val (tracks, fetchedName) = youtubeRepository.getPlaylistTracks(url)
                _uiState.update {
                    it.copy(
                        tracks = tracks,
                        playlistName = if (it.playlistName.isBlank()) fetchedName else it.playlistName,
                        isLoading = false,
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
}
