package com.dustvalve.next.android.ui.screens.local

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.RecentSearchDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.RecentSearchEntity
import com.dustvalve.next.android.data.mapper.toDomain
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.LocalMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.annotation.StringRes
import com.dustvalve.next.android.R
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class LocalUiState(
    val query: String = "",
    val searchResults: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val searchFilter: LocalSearchFilter? = null,
)

enum class LocalSearchFilter { TRACKS, ARTISTS, ALBUMS }

enum class LocalSortOption(@param:StringRes val labelRes: Int) {
    TITLE_AZ(R.string.sort_title_az),
    ARTIST_AZ(R.string.sort_artist_az),
    ALBUM_AZ(R.string.sort_album_az),
    SHORTEST(R.string.sort_shortest),
    LONGEST(R.string.sort_longest),
    DATE_ADDED(R.string.sort_date_added),
    RELEASE_YEAR(R.string.sort_release_year),
}

enum class DurationRange(@param:StringRes val labelRes: Int) {
    UNDER_3(R.string.duration_under_3),
    THREE_TO_FIVE(R.string.duration_3_to_5),
    OVER_5(R.string.duration_over_5),
}

data class LocalFilterState(
    val sortOption: LocalSortOption = LocalSortOption.TITLE_AZ,
    val selectedArtists: Set<String> = emptySet(),
    val selectedAlbums: Set<String> = emptySet(),
    val selectedDurations: Set<DurationRange> = emptySet(),
    val favoritesOnly: Boolean = false,
    val selectedFolders: Set<String> = emptySet(),
    val reverseOrder: Boolean = false,
) {
    val hasActiveFilters: Boolean get() =
        selectedArtists.isNotEmpty() ||
            selectedAlbums.isNotEmpty() ||
            selectedDurations.isNotEmpty() ||
            favoritesOnly ||
            selectedFolders.isNotEmpty()
}

@HiltViewModel
class LocalViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val favoriteDao: FavoriteDao,
    private val recentSearchDao: RecentSearchDao,
    private val settingsDataStore: SettingsDataStore,
    private val localMusicRepository: LocalMusicRepository,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalUiState())
    val uiState: StateFlow<LocalUiState> = _uiState.asStateFlow()

    val recentSearches: StateFlow<List<String>> = recentSearchDao.getRecent("local", 8)
        .map { entities -> entities.map { it.query } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistoryEnabled: StateFlow<Boolean> = settingsDataStore.searchHistoryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val localMusicEnabled: StateFlow<Boolean> = settingsDataStore.localMusicEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val allLocalTracks: StateFlow<List<Track>> = combine(
        trackDao.getLocalTracks(),
        favoriteDao.getAllByType("track"),
    ) { entities, favorites ->
        val favoriteIds = favorites.map { it.id }.toSet()
        entities.map { it.toDomain(isFavorite = it.id in favoriteIds) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filter state

    private val _filterState = MutableStateFlow(LocalFilterState())
    val filterState: StateFlow<LocalFilterState> = _filterState.asStateFlow()

    val filteredTracks: StateFlow<List<Track>> = combine(
        allLocalTracks,
        _filterState,
    ) { tracks, filters ->
        val comparator = getSortComparator(filters.sortOption).let {
            if (filters.reverseOrder) it.reversed() else it
        }
        tracks
            .filter { applyFilters(it, filters) }
            .sortedWith(comparator)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableArtists: StateFlow<List<String>> = allLocalTracks
        .map { tracks -> tracks.map { it.artist }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableAlbums: StateFlow<List<String>> = allLocalTracks
        .map { tracks -> tracks.map { it.albumTitle }.filter { it.isNotBlank() }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableFolders: StateFlow<List<String>> = allLocalTracks
        .map { tracks -> tracks.map { it.folderUri }.filter { it.isNotBlank() }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filter mutation functions

    fun setSortOption(option: LocalSortOption) {
        _filterState.update { it.copy(sortOption = option) }
    }

    fun toggleArtist(artist: String) {
        _filterState.update { state ->
            val newSet = state.selectedArtists.toMutableSet()
            if (artist in newSet) newSet.remove(artist) else newSet.add(artist)
            state.copy(selectedArtists = newSet)
        }
    }

    fun toggleAlbum(album: String) {
        _filterState.update { state ->
            val newSet = state.selectedAlbums.toMutableSet()
            if (album in newSet) newSet.remove(album) else newSet.add(album)
            state.copy(selectedAlbums = newSet)
        }
    }

    fun toggleDuration(range: DurationRange) {
        _filterState.update { state ->
            val newSet = state.selectedDurations.toMutableSet()
            if (range in newSet) newSet.remove(range) else newSet.add(range)
            state.copy(selectedDurations = newSet)
        }
    }

    fun toggleFavoritesFilter() {
        _filterState.update { it.copy(favoritesOnly = !it.favoritesOnly) }
    }

    fun toggleFolder(folder: String) {
        _filterState.update { state ->
            val newSet = state.selectedFolders.toMutableSet()
            if (folder in newSet) newSet.remove(folder) else newSet.add(folder)
            state.copy(selectedFolders = newSet)
        }
    }

    fun toggleReverseOrder() {
        _filterState.update { it.copy(reverseOrder = !it.reverseOrder) }
    }

    fun setArtistFilter(artist: String) {
        _filterState.update { LocalFilterState(selectedArtists = setOf(artist)) }
    }

    fun clearFilters() {
        _filterState.update { LocalFilterState() }
    }

    // Local music enable + scan

    fun enableLocalMusic() {
        viewModelScope.launch {
            try {
                settingsDataStore.setLocalMusicEnabled(true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun onAudioPermissionGranted() {
        viewModelScope.launch {
            try {
                localMusicRepository.clearAll()
                settingsDataStore.setLocalMusicUseMediaStore(true)
                _isScanning.value = true
                localMusicRepository.scan()
                _isScanning.value = false
                localMusicRepository.scheduleSyncWork()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _isScanning.value = false
            }
        }
    }

    private fun applyFilters(track: Track, filters: LocalFilterState): Boolean {
        if (filters.selectedArtists.isNotEmpty() && track.artist !in filters.selectedArtists) return false
        if (filters.selectedAlbums.isNotEmpty() && track.albumTitle !in filters.selectedAlbums) return false
        if (filters.selectedDurations.isNotEmpty()) {
            val minutes = track.duration / 60f
            val matches = filters.selectedDurations.any { range ->
                when (range) {
                    DurationRange.UNDER_3 -> minutes < 3f
                    DurationRange.THREE_TO_FIVE -> minutes in 3f..5f
                    DurationRange.OVER_5 -> minutes > 5f
                }
            }
            if (!matches) return false
        }
        if (filters.favoritesOnly && !track.isFavorite) return false
        if (filters.selectedFolders.isNotEmpty() && track.folderUri !in filters.selectedFolders) return false
        return true
    }

    private fun getSortComparator(option: LocalSortOption): Comparator<Track> = when (option) {
        LocalSortOption.TITLE_AZ -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        LocalSortOption.ARTIST_AZ -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        LocalSortOption.ALBUM_AZ -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.albumTitle }
            .thenBy { it.trackNumber }
        LocalSortOption.SHORTEST -> compareBy { it.duration }
        LocalSortOption.LONGEST -> compareByDescending { it.duration }
        LocalSortOption.DATE_ADDED -> compareByDescending { it.dateAdded }
        LocalSortOption.RELEASE_YEAR -> compareByDescending<Track> { it.year }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    }

    // Search

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

    fun onSearchFilterSelected(filter: LocalSearchFilter?) {
        _uiState.update { it.copy(searchFilter = filter, searchResults = emptyList()) }
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            searchJob?.cancel()
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
            val filter = _uiState.value.searchFilter
            val results = withContext(Dispatchers.IO) {
                val entities = trackDao.searchLocalTracks(query)
                val ids = entities.map { it.id }
                val favoriteIds = if (ids.isNotEmpty()) favoriteDao.getFavoriteIdsChunk(ids).toSet() else emptySet()
                val all = entities.map { it.toDomain(isFavorite = it.id in favoriteIds) }
                val lowerQuery = query.lowercase()
                when (filter) {
                    null -> all
                    LocalSearchFilter.TRACKS -> all.filter { it.title.lowercase().contains(lowerQuery) }
                    LocalSearchFilter.ARTISTS -> all.filter { it.artist.lowercase().contains(lowerQuery) }
                    LocalSearchFilter.ALBUMS -> all.filter { it.albumTitle.lowercase().contains(lowerQuery) }
                }
            }
            _uiState.update { it.copy(searchResults = results, isSearching = false) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update { it.copy(isSearching = false) }
        }
    }

    fun deleteLocalTrack(track: Track) {
        viewModelScope.launch {
            try {
                track.streamUrl?.let { uri ->
                    try {
                        appContext.contentResolver.delete(uri.toUri(), null, null)
                    } catch (_: Exception) { }
                }
                trackDao.deleteByIdsChunk(listOf(track.id))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }
}
