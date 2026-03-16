package com.dustvalve.next.android.ui.screens.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class YouTubeUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val recommendations: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val selectedFilter: String? = null,
    val searchGeneration: Int = 0,
)

data class YouTubeCategory(
    val name: String,
    val id: String,
)

@HiltViewModel
class YouTubeViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val youtubeRepository: YouTubeRepository,
    private val playlistRepository: PlaylistRepository,
    private val trackDao: TrackDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(YouTubeUiState())
    val uiState: StateFlow<YouTubeUiState> = _uiState.asStateFlow()

    val lastVideoId: StateFlow<String?> = settingsDataStore.lastYoutubeVideoId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var searchJob: Job? = null
    private var nextPage: Any? = null

    val categories = listOf(
        YouTubeCategory("Music", "music"),
        YouTubeCategory("Trending", "trending"),
        YouTubeCategory("New Releases", "new_releases"),
        YouTubeCategory("Live", "live"),
        YouTubeCategory("Podcasts", "podcasts"),
    )

    init {
        loadRecommendations()
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isNotBlank()) {
            searchJob = viewModelScope.launch {
                delay(400L)
                performSearch(query, resetResults = true)
            }
        } else {
            nextPage = null
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    isLoading = false,
                    hasMore = true,
                    error = null,
                    searchGeneration = it.searchGeneration + 1,
                )
            }
        }
    }

    fun onSearch() {
        searchJob?.cancel()
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            searchJob = viewModelScope.launch { performSearch(query, resetResults = true) }
        }
    }

    fun onFilterSelected(filter: String?) {
        _uiState.update {
            it.copy(
                selectedFilter = filter,
                results = emptyList(),
                hasMore = true,
                searchGeneration = it.searchGeneration + 1,
            )
        }
        nextPage = null
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { performSearch(query, resetResults = true) }
        }
    }

    fun loadMore() {
        if (_uiState.value.isLoading || !_uiState.value.hasMore) return
        val query = _uiState.value.query
        if (query.isBlank()) return
        viewModelScope.launch { performSearch(query, resetResults = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun loadRecommendations() {
        viewModelScope.launch {
            try {
                val videoId = lastVideoId.value ?: return@launch
                val recommendations = youtubeRepository.getRecommendations(
                    "https://www.youtube.com/watch?v=$videoId"
                )
                _uiState.update { it.copy(recommendations = recommendations) }
            } catch (_: Exception) {
                // Best-effort — don't show error for recommendations
            }
        }
    }

    private suspend fun performSearch(query: String, resetResults: Boolean) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            val page = if (resetResults) null else nextPage
            val (results, newNextPage) = youtubeRepository.search(
                query = query,
                filter = _uiState.value.selectedFilter,
                page = page,
            )
            nextPage = newNextPage

            _uiState.update {
                val mergedResults = if (resetResults) {
                    results
                } else {
                    val existingUrls = it.results.mapTo(HashSet()) { r -> r.url }
                    it.results + results.filter { r -> r.url !in existingUrls }
                }
                it.copy(
                    results = mergedResults,
                    isLoading = false,
                    hasMore = newNextPage != null && results.isNotEmpty(),
                    searchGeneration = if (resetResults) it.searchGeneration + 1 else it.searchGeneration,
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update {
                it.copy(isLoading = false, error = e.message ?: "Search failed")
            }
        }
    }

    fun importPlaylist(playlistUrl: String, name: String) {
        viewModelScope.launch {
            try {
                val tracks = youtubeRepository.getPlaylistTracks(playlistUrl)
                // Persist tracks to DB
                trackDao.insertAll(tracks.map { it.toEntity() })
                // Create playlist and add tracks
                val playlist = playlistRepository.createPlaylist(name)
                playlistRepository.addTracksToPlaylist(playlist.id, tracks.map { it.id })
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(error = "Failed to import playlist: ${e.message}") }
            }
        }
    }

    suspend fun getTrackInfo(videoUrl: String): Track {
        return youtubeRepository.getTrackInfo(videoUrl)
    }
}
