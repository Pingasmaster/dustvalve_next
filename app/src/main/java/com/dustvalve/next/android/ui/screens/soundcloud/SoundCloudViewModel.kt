package com.dustvalve.next.android.ui.screens.soundcloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.R
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.dao.RecentSearchDao
import com.dustvalve.next.android.data.local.db.entity.RecentSearchEntity
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SoundCloudHomeFeed
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.SoundCloudRepository
import com.dustvalve.next.android.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class SoundCloudGenreChip(val slug: String, val label: String)

data class SoundCloudUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: UiText? = null,
    val selectedGenre: String = "all-music",
    val feed: SoundCloudHomeFeed? = null,
    val isHomeLoading: Boolean = false,
    val homeError: UiText? = null,
)

val soundCloudGenreChips = listOf(
    SoundCloudGenreChip("all-music", "All music"),
    SoundCloudGenreChip("electronic", "Electronic"),
    SoundCloudGenreChip("hiphoprap", "Hip hop / Rap"),
    SoundCloudGenreChip("pop", "Pop"),
    SoundCloudGenreChip("rock", "Rock"),
    SoundCloudGenreChip("danceedm", "Dance / EDM"),
    SoundCloudGenreChip("ambient", "Ambient"),
    SoundCloudGenreChip("soundtrack", "Soundtrack"),
    SoundCloudGenreChip("classical", "Classical"),
    SoundCloudGenreChip("alternative", "Alternative"),
)

@HiltViewModel
class SoundCloudViewModel @Inject constructor(
    private val soundCloudRepository: SoundCloudRepository,
    private val recentSearchDao: RecentSearchDao,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SoundCloudUiState())
    val uiState: StateFlow<SoundCloudUiState> = _uiState.asStateFlow()

    val recentSearches: StateFlow<List<String>> = recentSearchDao.getRecent("soundcloud", 8)
        .map { entities -> entities.map { it.query } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchHistoryEnabled: StateFlow<Boolean> = combine(
        settingsDataStore.searchHistoryEnabled,
        settingsDataStore.searchHistorySoundcloud,
    ) { global, perSource -> global && perSource }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private var searchJob: Job? = null
    private var homeJob: Job? = null

    init {
        loadHome()
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun onSearch() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null, results = emptyList()) }
            try {
                if (searchHistoryEnabled.value) {
                    recentSearchDao.insert(RecentSearchEntity(query = query, source = "soundcloud"))
                }
                val results = soundCloudRepository.search(query)
                _uiState.update { it.copy(results = results, isSearching = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                setSearchFailed(e.message)
            } catch (e: IllegalStateException) {
                setSearchFailed(e.message)
            } catch (e: IllegalArgumentException) {
                setSearchFailed(e.message)
            }
        }
    }

    private fun setSearchFailed(message: String?) {
        _uiState.update {
            it.copy(
                isSearching = false,
                searchError = UiText.orResource(message, R.string.common_search_failed),
            )
        }
    }

    fun clearSearchError() {
        _uiState.update { it.copy(searchError = null) }
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch {
            recentSearchDao.delete(query, "soundcloud")
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            recentSearchDao.clearAll("soundcloud")
        }
    }

    fun selectGenre(slug: String) {
        if (slug == _uiState.value.selectedGenre && _uiState.value.feed != null) return
        _uiState.update { it.copy(selectedGenre = slug) }
        loadHome(slug)
    }

    fun retryHome() {
        loadHome(_uiState.value.selectedGenre)
    }

    private fun loadHome(genre: String = _uiState.value.selectedGenre) {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            _uiState.update { it.copy(isHomeLoading = true, homeError = null) }
            try {
                val feed = soundCloudRepository.getHome(genre)
                _uiState.update {
                    it.copy(feed = feed, isHomeLoading = false, homeError = null, selectedGenre = genre)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                setHomeFailed(e.message)
            } catch (e: IllegalStateException) {
                setHomeFailed(e.message)
            } catch (e: IllegalArgumentException) {
                setHomeFailed(e.message)
            }
        }
    }

    private fun setHomeFailed(message: String?) {
        _uiState.update {
            it.copy(
                isHomeLoading = false,
                homeError = UiText.orResource(message, R.string.soundcloud_error_load),
            )
        }
    }

    suspend fun getTrack(urlOrId: String): Track = soundCloudRepository.getTrack(urlOrId)

    suspend fun resolveCollectionTracks(url: String): List<Track> = soundCloudRepository.getCollection(url).tracks
}
