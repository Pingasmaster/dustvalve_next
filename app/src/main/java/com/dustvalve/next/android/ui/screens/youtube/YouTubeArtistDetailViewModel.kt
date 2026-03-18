package com.dustvalve.next.android.ui.screens.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class YouTubeArtistDetailUiState(
    val artistName: String = "",
    val artistUrl: String = "",
    val imageUrl: String? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class YouTubeArtistDetailViewModel @Inject constructor(
    private val youtubeRepository: YouTubeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(YouTubeArtistDetailUiState())
    val uiState: StateFlow<YouTubeArtistDetailUiState> = _uiState.asStateFlow()

    private var loadedUrl: String? = null
    private var nextPage: Any? = null

    fun loadArtist(url: String, name: String, imageUrl: String?) {
        if (loadedUrl == url && _uiState.value.tracks.isNotEmpty()) return
        loadedUrl = url
        nextPage = null
        _uiState.update {
            it.copy(
                artistName = name, artistUrl = url, imageUrl = imageUrl,
                isLoading = true, error = null, tracks = emptyList(), hasMore = true,
            )
        }
        viewModelScope.launch {
            try {
                val (tracks, channelName, newNextPage) = youtubeRepository.getChannelVideos(url)
                nextPage = newNextPage
                _uiState.update {
                    it.copy(
                        tracks = tracks,
                        artistName = channelName ?: name,
                        isLoading = false,
                        hasMore = newNextPage != null && tracks.isNotEmpty(),
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

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore || state.artistUrl.isBlank()) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                val (tracks, _, newNextPage) = youtubeRepository.getChannelVideos(
                    state.artistUrl, nextPage,
                )
                nextPage = newNextPage
                _uiState.update {
                    val existingIds = it.tracks.mapTo(HashSet()) { t -> t.id }
                    val newTracks = tracks.filter { t -> t.id !in existingIds }
                    it.copy(
                        tracks = it.tracks + newTracks,
                        isLoadingMore = false,
                        hasMore = newNextPage != null && tracks.isNotEmpty(),
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }
}
