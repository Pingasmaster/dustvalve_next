package com.dustvalve.next.android.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.dao.RecentSearchDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.RecentSearchEntity
import com.dustvalve.next.android.data.mapper.toDomain
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.usecase.SearchDustvalveUseCase
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val selectedType: SearchResultType? = null,
    val isLoading: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null,
    val searchGeneration: Int = 0,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchDustvalveUseCase: SearchDustvalveUseCase,
    private val recentSearchDao: RecentSearchDao,
    private val trackDao: TrackDao,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val recentSearches: StateFlow<List<String>> = recentSearchDao.getRecent(8)
        .map { entities -> entities.map { it.query } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val localSearchEnabled: StateFlow<Boolean> = combine(
        settingsDataStore.localMusicEnabled,
        settingsDataStore.localMusicSearchEnabled,
    ) { enabled, searchEnabled -> enabled && searchEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        // Debounced search
        searchJob?.cancel()
        loadMoreJob?.cancel()
        if (query.isNotBlank()) {
            searchJob = viewModelScope.launch {
                delay(400L)
                performSearch(resetResults = true)
            }
        } else {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    isLoading = false,
                    page = 1,
                    hasMore = true,
                    error = null,
                    searchGeneration = it.searchGeneration + 1,
                )
            }
        }
    }

    fun onSearch() {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            saveRecentSearch(query)
            searchJob = viewModelScope.launch {
                performSearch(resetResults = true)
            }
        }
    }

    fun onTypeSelected(type: SearchResultType?) {
        _uiState.update {
            it.copy(
                selectedType = type,
                results = emptyList(),
                page = 1,
                hasMore = true,
                error = null,
                searchGeneration = it.searchGeneration + 1,
            )
        }
        if (_uiState.value.query.isNotBlank()) {
            searchJob?.cancel()
            loadMoreJob?.cancel()
            searchJob = viewModelScope.launch {
                performSearch(resetResults = true)
            }
        }
    }

    fun loadMore() {
        if (_uiState.value.isLoading || !_uiState.value.hasMore) return
        if (loadMoreJob?.isActive == true) return
        loadMoreJob = viewModelScope.launch {
            performSearch(resetResults = false)
        }
    }

    fun playLocalTrack(trackId: String, playerViewModel: PlayerViewModel) {
        viewModelScope.launch {
            try {
                val entity = withContext(Dispatchers.IO) { trackDao.getById(trackId) } ?: return@launch
                val track = entity.toDomain(false)
                playerViewModel.playTrack(track)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch { recentSearchDao.delete(query) }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { recentSearchDao.clearAll() }
    }

    private fun saveRecentSearch(query: String) {
        viewModelScope.launch {
            recentSearchDao.insert(RecentSearchEntity(query = query.trim()))
            recentSearchDao.deleteOld(keepCount = 20)
        }
    }

    private suspend fun performSearch(resetResults: Boolean) {
        val state = _uiState.value
        val query = state.query
        if (query.isBlank()) return

        val page = if (resetResults) 1 else state.page
        val isLocalFilter = state.selectedType == SearchResultType.LOCAL_TRACK

        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                page = page,
            )
        }

        try {
            // Fetch local results on first page if local search is enabled
            val localResults = if (resetResults && localSearchEnabled.value &&
                (state.selectedType == null || isLocalFilter)
            ) {
                searchLocalTracks(query)
            } else {
                emptyList()
            }

            // Fetch remote results (skip for LOCAL_TRACK filter)
            val remoteResults = if (!isLocalFilter) {
                searchDustvalveUseCase(
                    query = query,
                    page = page,
                    type = state.selectedType,
                )
            } else {
                emptyList()
            }

            _uiState.update {
                val mergedResults = if (resetResults) {
                    localResults + remoteResults
                } else {
                    val existingUrls = it.results.mapTo(HashSet()) { r -> r.url }
                    it.results + remoteResults.filter { r -> r.url !in existingUrls }
                }
                it.copy(
                    results = mergedResults,
                    isLoading = false,
                    page = page + 1,
                    hasMore = if (isLocalFilter) false else remoteResults.isNotEmpty(),
                    error = null,
                    searchGeneration = if (resetResults) it.searchGeneration + 1 else it.searchGeneration,
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = e.message ?: "Search failed",
                )
            }
        }
    }

    private suspend fun searchLocalTracks(query: String): List<SearchResult> {
        return withContext(Dispatchers.IO) {
            trackDao.searchLocalTracks(query).map { entity ->
                SearchResult(
                    type = SearchResultType.LOCAL_TRACK,
                    name = entity.title,
                    url = "local://${entity.id}",
                    imageUrl = entity.artUrl.ifBlank { null },
                    artist = entity.artist,
                    album = entity.albumTitle,
                    genre = null,
                    releaseDate = null,
                )
            }
        }
    }
}
