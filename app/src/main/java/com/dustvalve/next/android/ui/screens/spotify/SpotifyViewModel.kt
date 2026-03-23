package com.dustvalve.next.android.ui.screens.spotify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.RecentSearchDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.local.db.entity.RecentSearchEntity
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.repository.SpotifyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class SpotifyUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
    val selectedFilter: String? = null,
)

@HiltViewModel
class SpotifyViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val spotifyRepository: SpotifyRepository,
    private val playlistRepository: PlaylistRepository,
    private val trackDao: TrackDao,
    private val database: DustvalveNextDatabase,
    private val recentSearchDao: RecentSearchDao,
    private val favoriteDao: FavoriteDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpotifyUiState())
    val uiState: StateFlow<SpotifyUiState> = _uiState.asStateFlow()

    val recentSearches: StateFlow<List<String>> = recentSearchDao.getRecent("spotify", 8)
        .map { entities -> entities.map { it.query } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistoryEnabled: StateFlow<Boolean> = settingsDataStore.searchHistoryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private var searchJob: Job? = null

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
                it.copy(
                    results = emptyList(),
                    isLoading = false,
                    hasMore = false,
                    error = null,
                )
            }
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
        viewModelScope.launch { recentSearchDao.delete(query, "spotify") }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { recentSearchDao.clearAll("spotify") }
    }

    private fun saveRecentSearch(query: String) {
        if (!searchHistoryEnabled.value) return
        viewModelScope.launch {
            recentSearchDao.insert(RecentSearchEntity(query = query.trim(), source = "spotify"))
            recentSearchDao.deleteOld(source = "spotify", keepCount = 20)
        }
    }

    fun onFilterSelected(filter: String?) {
        _uiState.update {
            it.copy(
                selectedFilter = filter,
                results = emptyList(),
                hasMore = false,
            )
        }
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { performSearch(query) }
        }
    }

    fun loadMore() {
        // Spotify search does not support pagination in the current bridge
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private suspend fun performSearch(query: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            val results = spotifyRepository.search(query, _uiState.value.selectedFilter)
            _uiState.update {
                it.copy(
                    results = results,
                    isLoading = false,
                    hasMore = false,
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update {
                it.copy(isLoading = false, error = e.message ?: "Search failed")
            }
        }
    }

    fun importPlaylist(playlistUri: String, name: String) {
        viewModelScope.launch {
            try {
                val (tracks, _) = spotifyRepository.getPlaylistTracks(playlistUri)
                database.withTransaction {
                    trackDao.insertAll(tracks.map { it.toEntity() })
                    val playlist = playlistRepository.createPlaylist(name)
                    playlistRepository.addTracksToPlaylist(playlist.id, tracks.map { it.id })
                }
                favoriteDao.insert(FavoriteEntity(id = playlistUri, type = "spotify_playlist"))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(error = "Failed to import playlist: ${e.message}") }
            }
        }
    }

    suspend fun getTrackInfo(uri: String): Track {
        return spotifyRepository.getTrackInfo(uri)
    }
}
