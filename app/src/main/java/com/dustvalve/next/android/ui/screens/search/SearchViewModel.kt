package com.dustvalve.next.android.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.R
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.LocalMusicRepository
import com.dustvalve.next.android.domain.repository.RecentSearchRepository
import com.dustvalve.next.android.domain.usecase.GetAlbumDetailUseCase
import com.dustvalve.next.android.domain.usecase.SearchDustvalveUseCase
import com.dustvalve.next.android.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
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
    val error: UiText? = null,
    val searchGeneration: Int = 0,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchDustvalveUseCase: SearchDustvalveUseCase,
    private val getAlbumDetailUseCase: GetAlbumDetailUseCase,
    private val recentSearchRepository: RecentSearchRepository,
    private val localMusicRepository: LocalMusicRepository,
    private val settingsDataStore: SettingsDataStore,
    @param:Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val recentSearches: StateFlow<List<String>> = recentSearchRepository.getRecent("bandcamp")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistoryEnabled: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
        settingsDataStore.searchHistoryEnabled,
        settingsDataStore.searchHistoryBandcamp,
    ) { global, perSource -> global && perSource }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val localSearchEnabled: StateFlow<Boolean> = settingsDataStore.localMusicEnabled
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
                delay(SEARCH_DEBOUNCE_MS)
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

    // No play*(other ViewModel) overloads here: a ViewModel must never take
    // another ViewModel as a parameter (see MainActivity's forwarding note).
    // The UI awaits resolve*() and hands the Track to PlayerViewModel itself.
    suspend fun resolveLocalTrack(trackId: String): Track? = withContext(ioDispatcher) {
        localMusicRepository.getLocalTrack(trackId)
    }

    suspend fun resolveBandcampTrack(trackUrl: String, trackName: String): Track? {
        val album = getAlbumDetailUseCase(trackUrl)
        return album.tracks.find { it.title.equals(trackName, ignoreCase = true) }
            ?: album.tracks.firstOrNull()
    }

    suspend fun resolveBandcampAlbumTracks(albumUrl: String): List<Track> = getAlbumDetailUseCase(albumUrl).tracks

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch { recentSearchRepository.remove(query, "bandcamp") }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { recentSearchRepository.clear("bandcamp") }
    }

    private fun saveRecentSearch(query: String) {
        if (!searchHistoryEnabled.value) return
        viewModelScope.launch {
            // The repository stores the query verbatim, so keep trimming here
            // (see RecentSearchRepository.add's kdoc); trim = true caps the
            // bandcamp history at 20 entries, as before.
            recentSearchRepository.add(query.trim(), "bandcamp")
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
                    error = UiText.orResource(e.message, R.string.common_search_failed),
                )
            }
        }
    }

    private suspend fun searchLocalTracks(query: String): List<SearchResult> = withContext(ioDispatcher) {
        // The repository decorates tracks with favorite flags; this mapping
        // deliberately ignores the flag (SearchResult carries none), so the
        // output is identical to the previous entity-based mapping.
        localMusicRepository.searchLocalTracks(query).map { track ->
            SearchResult(
                type = SearchResultType.LOCAL_TRACK,
                name = track.title,
                url = "local://${track.id}",
                imageUrl = track.artUrl.ifBlank { null },
                artist = track.artist,
                album = track.albumTitle,
                genre = null,
                releaseDate = null,
            )
        }
    }

    private companion object {
        private const val SEARCH_DEBOUNCE_MS = 400L
    }
}
