package com.dustvalve.next.android.ui.screens.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.model.SearchResult
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
    val error: String? = null,
    val selectedFilter: String? = null,
)

data class YouTubeCategory(
    val name: String,
    val id: String,
)

@HiltViewModel
class YouTubeViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(YouTubeUiState())
    val uiState: StateFlow<YouTubeUiState> = _uiState.asStateFlow()

    val lastVideoId: StateFlow<String?> = settingsDataStore.lastYoutubeVideoId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var searchJob: Job? = null

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
                performSearch(query)
            }
        } else {
            _uiState.update {
                it.copy(results = emptyList(), isLoading = false, error = null)
            }
        }
    }

    fun onSearch() {
        searchJob?.cancel()
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            searchJob = viewModelScope.launch { performSearch(query) }
        }
    }

    fun onFilterSelected(filter: String?) {
        _uiState.update { it.copy(selectedFilter = filter, results = emptyList()) }
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { performSearch(query) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun loadRecommendations() {
        viewModelScope.launch {
            // TODO: Load recommendations from last played YouTube video via NewPipe Extractor
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            // TODO: Search via NewPipe Extractor
            delay(100) // Placeholder
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    isLoading = false,
                    error = "YouTube search coming soon",
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
            // TODO: Import YouTube playlist via NewPipe Extractor
        }
    }
}
