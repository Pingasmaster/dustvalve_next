package com.dustvalve.next.android.ui.screens.youtube

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
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class DiscoverSection(
    val title: String,
    val items: List<SearchResult> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

data class MoodChip(val label: String, val query: String)

data class GenreQuery(val displayName: String, val searchQuery: String)

data class YouTubeUiState(
    // Search state
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val selectedFilter: String? = null,
    val searchGeneration: Int = 0,

    // Mood chip state
    val selectedMood: String? = null,
    val moodResults: List<SearchResult> = emptyList(),
    val isMoodLoading: Boolean = false,
    val moodError: String? = null,

    // Discovery feed sections
    val recommendationsSection: DiscoverSection = DiscoverSection(title = ""),
    val lastPlayedTrackTitle: String? = null,
    val trendingSection: DiscoverSection = DiscoverSection(title = "Trending now"),
    val genreSections: List<DiscoverSection> = emptyList(),
    val genresExhausted: Boolean = false,
)

val moodChips = listOf(
    MoodChip("Chill", "chill vibes music"),
    MoodChip("Energize", "energize pump up music"),
    MoodChip("Focus", "focus concentration music"),
    MoodChip("Feel Good", "feel good happy music"),
    MoodChip("Party", "party dance music"),
    MoodChip("Sad", "sad emotional music"),
    MoodChip("Workout", "workout gym music"),
    MoodChip("Sleep", "sleep relaxing music"),
    MoodChip("Romance", "romantic love songs"),
    MoodChip("Commute", "driving road trip music"),
    MoodChip("Gaming", "gaming music mix"),
)

val allGenres = listOf(
    // Core genres
    GenreQuery("Pop", "pop music hits"),
    GenreQuery("Rock", "rock music"),
    GenreQuery("Hip-Hop", "hip hop rap music"),
    GenreQuery("R&B & Soul", "r&b soul music"),
    GenreQuery("Jazz", "jazz music"),
    GenreQuery("Electronic", "electronic dance music"),
    GenreQuery("Classical", "classical music"),
    GenreQuery("Country", "country music"),
    GenreQuery("Metal", "metal music"),
    GenreQuery("Blues", "blues music"),
    GenreQuery("Indie", "indie alternative music"),
    GenreQuery("Folk", "folk acoustic music"),
    GenreQuery("Reggae", "reggae dancehall music"),
    GenreQuery("Funk", "funk music"),
    GenreQuery("Punk", "punk rock music"),
    GenreQuery("Lo-fi", "lo-fi hip hop beats"),
    // Regional & cultural
    GenreQuery("Latin", "latin reggaeton music"),
    GenreQuery("K-Pop", "k-pop music"),
    GenreQuery("J-Pop", "j-pop japanese music"),
    GenreQuery("Afrobeats", "afrobeats african music"),
    GenreQuery("Bollywood", "bollywood hindi songs"),
    GenreQuery("Arabic", "arabic music"),
    GenreQuery("French Pop", "french pop musique"),
    // Subgenres & styles
    GenreQuery("Ambient", "ambient music"),
    GenreQuery("Phonk", "phonk music"),
    GenreQuery("Drill", "drill music"),
    GenreQuery("Trap", "trap music"),
    GenreQuery("Drum & Bass", "drum and bass music"),
    GenreQuery("House", "house music"),
    GenreQuery("Techno", "techno music"),
    GenreQuery("Trance", "trance music"),
    GenreQuery("Shoegaze", "shoegaze dream pop"),
    GenreQuery("Post-Punk", "post punk dark wave"),
    GenreQuery("Grunge", "grunge music"),
    GenreQuery("Ska", "ska music"),
    GenreQuery("Gospel", "gospel music"),
    GenreQuery("Amapiano", "amapiano music"),
    GenreQuery("Bossa Nova", "bossa nova music"),
    GenreQuery("Cumbia", "cumbia music"),
    GenreQuery("Salsa", "salsa music"),
    // Decades
    GenreQuery("80s Hits", "80s music hits"),
    GenreQuery("90s Hits", "90s music hits"),
    GenreQuery("2000s Hits", "2000s music hits"),
    // Misc
    GenreQuery("Soundtracks", "film soundtrack music"),
    GenreQuery("Musical Theater", "broadway musical songs"),
    GenreQuery("Video Game OST", "video game soundtrack music"),
    GenreQuery("Disney", "disney songs music"),
)

private const val INITIAL_GENRE_COUNT = 6
private const val GENRE_LOAD_BATCH = 4

@HiltViewModel
class YouTubeViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val youtubeRepository: YouTubeRepository,
    private val playlistRepository: PlaylistRepository,
    private val trackDao: TrackDao,
    private val database: DustvalveNextDatabase,
    private val recentSearchDao: RecentSearchDao,
    private val favoriteDao: FavoriteDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(YouTubeUiState())
    val uiState: StateFlow<YouTubeUiState> = _uiState.asStateFlow()

    val recentSearches: StateFlow<List<String>> = recentSearchDao.getRecent("youtube", 8)
        .map { entities -> entities.map { it.query } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistoryEnabled: StateFlow<Boolean> = settingsDataStore.searchHistoryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val lastVideoId: StateFlow<String?> = settingsDataStore.lastYoutubeVideoId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var searchJob: Job? = null
    private var nextPage: Any? = null
    private var moodJob: Job? = null
    private val discoveryJobs = mutableListOf<Job>()

    private val shuffledGenres = allGenres.shuffled()
    private var loadedGenreCount = 0

    init {
        loadDiscoveryFeed()
    }

    // ── Discovery feed ──────────────────────────────────────────────────

    private fun loadDiscoveryFeed() {
        discoveryJobs.forEach { it.cancel() }
        discoveryJobs.clear()

        // Initialize genre sections with loading placeholders
        val initialGenres = shuffledGenres.take(INITIAL_GENRE_COUNT)
        loadedGenreCount = initialGenres.size
        _uiState.update {
            it.copy(
                genreSections = initialGenres.map { genre ->
                    DiscoverSection(title = "Discover: ${genre.displayName}")
                },
                genresExhausted = loadedGenreCount >= shuffledGenres.size,
            )
        }

        // Launch all discovery loads in parallel with staggered genre starts
        discoveryJobs += viewModelScope.launch { loadRecommendationsSection() }
        discoveryJobs += viewModelScope.launch { loadTrendingSection() }
        initialGenres.forEachIndexed { index, genre ->
            discoveryJobs += viewModelScope.launch {
                delay(index * 150L) // Stagger to avoid rate limiting
                loadGenreSection(index, genre)
            }
        }
    }

    private suspend fun loadRecommendationsSection() {
        try {
            val videoId = settingsDataStore.lastYoutubeVideoId.firstOrNull()
            if (videoId == null) {
                _uiState.update {
                    it.copy(recommendationsSection = DiscoverSection(title = "", isLoading = false))
                }
                return
            }

            val trackTitle = trackDao.getById("yt_$videoId")?.title
            _uiState.update {
                it.copy(
                    lastPlayedTrackTitle = trackTitle,
                    recommendationsSection = DiscoverSection(
                        title = if (trackTitle != null) "Because you listened to $trackTitle"
                        else "Recommended for you",
                        isLoading = true,
                    )
                )
            }

            val recommendations = youtubeRepository.getRecommendations(
                "https://www.youtube.com/watch?v=$videoId"
            )
            _uiState.update {
                it.copy(
                    recommendationsSection = it.recommendationsSection.copy(
                        items = recommendations,
                        isLoading = false,
                    )
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update {
                it.copy(
                    recommendationsSection = it.recommendationsSection.copy(
                        isLoading = false,
                        error = e.message,
                    )
                )
            }
        }
    }

    private suspend fun loadTrendingSection() {
        try {
            val (results, _) = youtubeRepository.search(
                query = "trending music",
                filter = "songs",
                page = null,
            )
            _uiState.update {
                it.copy(trendingSection = it.trendingSection.copy(items = results, isLoading = false))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update {
                it.copy(trendingSection = it.trendingSection.copy(isLoading = false, error = e.message))
            }
        }
    }

    private suspend fun loadGenreSection(index: Int, genre: GenreQuery) {
        try {
            val (results, _) = youtubeRepository.search(
                query = genre.searchQuery,
                filter = "songs",
                page = null,
            )
            _uiState.update {
                val updated = it.genreSections.toMutableList()
                if (index < updated.size) {
                    updated[index] = updated[index].copy(items = results, isLoading = false)
                }
                it.copy(genreSections = updated)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update {
                val updated = it.genreSections.toMutableList()
                if (index < updated.size) {
                    updated[index] = updated[index].copy(isLoading = false, error = e.message)
                }
                it.copy(genreSections = updated)
            }
        }
    }

    fun loadMoreGenres() {
        if (_uiState.value.genresExhausted) return
        val startIndex = loadedGenreCount
        val batch = shuffledGenres.drop(startIndex).take(GENRE_LOAD_BATCH)
        if (batch.isEmpty()) {
            _uiState.update { it.copy(genresExhausted = true) }
            return
        }

        loadedGenreCount += batch.size
        _uiState.update {
            it.copy(
                genreSections = it.genreSections + batch.map { genre ->
                    DiscoverSection(title = "Discover: ${genre.displayName}")
                },
                genresExhausted = loadedGenreCount >= shuffledGenres.size,
            )
        }

        batch.forEachIndexed { batchIndex, genre ->
            val globalIndex = startIndex + batchIndex
            discoveryJobs += viewModelScope.launch {
                delay(batchIndex * 150L)
                loadGenreSection(globalIndex, genre)
            }
        }
    }

    // ── Mood selection ──────────────────────────────────────────────────

    fun onMoodSelected(mood: String?) {
        if (mood == _uiState.value.selectedMood) {
            // Deselect
            moodJob?.cancel()
            _uiState.update {
                it.copy(selectedMood = null, moodResults = emptyList(), isMoodLoading = false, moodError = null)
            }
            return
        }

        _uiState.update {
            it.copy(selectedMood = mood, moodResults = emptyList(), isMoodLoading = true, moodError = null)
        }

        moodJob?.cancel()
        moodJob = viewModelScope.launch {
            try {
                val chip = moodChips.first { it.label == mood }
                val (results, _) = youtubeRepository.search(
                    query = chip.query,
                    filter = "songs",
                    page = null,
                )
                _uiState.update { it.copy(moodResults = results, isMoodLoading = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(isMoodLoading = false, moodError = e.message ?: "Failed to load")
                }
            }
        }
    }

    fun retrySection(key: String) {
        when (key) {
            "recommendations" -> viewModelScope.launch { loadRecommendationsSection() }
            "trending" -> {
                _uiState.update { it.copy(trendingSection = it.trendingSection.copy(isLoading = true, error = null)) }
                viewModelScope.launch { loadTrendingSection() }
            }
            else -> {
                val index = key.removePrefix("genre_").toIntOrNull() ?: return
                val genre = shuffledGenres.getOrNull(index) ?: return
                _uiState.update {
                    val updated = it.genreSections.toMutableList()
                    if (index < updated.size) {
                        updated[index] = updated[index].copy(isLoading = true, error = null)
                    }
                    it.copy(genreSections = updated)
                }
                viewModelScope.launch { loadGenreSection(index, genre) }
            }
        }
    }

    // ── Search (unchanged) ──────────────────────────────────────────────

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
            saveRecentSearch(query)
            searchJob = viewModelScope.launch { performSearch(query, resetResults = true) }
        }
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch { recentSearchDao.delete(query, "youtube") }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { recentSearchDao.clearAll("youtube") }
    }

    private fun saveRecentSearch(query: String) {
        if (!searchHistoryEnabled.value) return
        viewModelScope.launch {
            recentSearchDao.insert(RecentSearchEntity(query = query.trim(), source = "youtube"))
            recentSearchDao.deleteOld(source = "youtube", keepCount = 20)
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

    // ── Other ───────────────────────────────────────────────────────────

    fun importPlaylist(playlistUrl: String, name: String) {
        viewModelScope.launch {
            try {
                val (tracks, _) = youtubeRepository.getPlaylistTracks(playlistUrl)
                database.withTransaction {
                    trackDao.insertAll(tracks.map { it.toEntity() })
                    val playlist = playlistRepository.createPlaylist(name)
                    playlistRepository.addTracksToPlaylist(playlist.id, tracks.map { it.id })
                }
                favoriteDao.insert(FavoriteEntity(id = playlistUrl, type = "youtube_playlist"))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(error = "Failed to import playlist: ${e.message}") }
            }
        }
    }

    suspend fun getTrackInfo(videoUrl: String): Track {
        return youtubeRepository.getTrackInfo(videoUrl)
    }

    suspend fun resolvePlaylistTracks(playlistUrl: String): List<Track> {
        return youtubeRepository.getPlaylistTracks(playlistUrl).first
    }
}
