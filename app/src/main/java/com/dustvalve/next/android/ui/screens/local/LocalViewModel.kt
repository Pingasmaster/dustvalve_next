package com.dustvalve.next.android.ui.screens.local

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.dao.RecentSearchDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.RecentSearchEntity
import com.dustvalve.next.android.data.mapper.toDomain
import com.dustvalve.next.android.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class LocalUiState(
    val query: String = "",
    val searchResults: List<Track> = emptyList(),
    val isSearching: Boolean = false,
)

@HiltViewModel
class LocalViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val recentSearchDao: RecentSearchDao,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalUiState())
    val uiState: StateFlow<LocalUiState> = _uiState.asStateFlow()

    val recentSearches: StateFlow<List<String>> = recentSearchDao.getRecent("local", 8)
        .map { entities -> entities.map { it.query } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistoryEnabled: StateFlow<Boolean> = settingsDataStore.searchHistoryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val allLocalTracks: StateFlow<List<Track>> = trackDao.getLocalTracks()
        .map { entities -> entities.map { it.toDomain(isFavorite = false) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isNotBlank()) {
            searchJob = viewModelScope.launch {
                delay(300L)
                performSearch(query)
            }
        } else {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
        }
    }

    fun onSearch() {
        searchJob?.cancel()
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            saveRecentSearch(query)
            searchJob = viewModelScope.launch { performSearch(query) }
        }
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch { recentSearchDao.delete(query, "local") }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { recentSearchDao.clearAll("local") }
    }

    private fun saveRecentSearch(query: String) {
        if (!searchHistoryEnabled.value) return
        viewModelScope.launch {
            recentSearchDao.insert(RecentSearchEntity(query = query.trim(), source = "local"))
            recentSearchDao.deleteOld(source = "local", keepCount = 20)
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.update { it.copy(isSearching = true) }
        try {
            val results = withContext(Dispatchers.IO) {
                trackDao.searchLocalTracks(query).map { it.toDomain(isFavorite = false) }
            }
            _uiState.update { it.copy(searchResults = results, isSearching = false) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update { it.copy(isSearching = false) }
        }
    }
}
