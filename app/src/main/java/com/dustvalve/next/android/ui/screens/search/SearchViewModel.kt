package com.dustvalve.next.android.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.data.local.db.dao.RecentSearchDao
import com.dustvalve.next.android.data.local.db.entity.RecentSearchEntity
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.usecase.SearchDustvalveUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val recentSearches: StateFlow<List<String>> = recentSearchDao.getRecent(8)
        .map { entities -> entities.map { it.query } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                page = page,
            )
        }

        try {
            val results = searchDustvalveUseCase(
                query = query,
                page = page,
                type = state.selectedType,
            )

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
                    page = page + 1,
                    hasMore = results.isNotEmpty(),
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
}
