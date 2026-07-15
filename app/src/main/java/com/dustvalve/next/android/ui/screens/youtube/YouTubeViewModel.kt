package com.dustvalve.next.android.ui.screens.youtube

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.dustvalve.next.android.R
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
import com.dustvalve.next.android.domain.model.YouTubeMusicHomeFeed
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.repository.YouTubeMusicRepository
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import com.dustvalve.next.android.util.UiText
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
    val title: UiText? = null,
    val items: List<SearchResult> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

data class MoodChip(@StringRes val labelRes: Int, val query: String)

data class GenreQuery(@StringRes val displayNameRes: Int, val searchQuery: String)

enum class YouTubeSource { YouTube, YouTubeMusic }

data class YouTubeUiState(
    // Active source sub-tab
    val activeSource: YouTubeSource = YouTubeSource.YouTube,

    // Search state
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val error: UiText? = null,
    val selectedFilter: String? = null,
    val searchGeneration: Int = 0,

    // Mood chip state
    val selectedMood: MoodChip? = null,
    val moodResults: List<SearchResult> = emptyList(),
    val isMoodLoading: Boolean = false,
    val moodError: UiText? = null,

    // Discovery feed sections (YouTube sub-tab)
    val recommendationsSection: DiscoverSection = DiscoverSection(),
    val lastPlayedTrackTitle: String? = null,
    val trendingSection: DiscoverSection = DiscoverSection(title = UiText.StringResource(R.string.ytm_section_trending)),
    val genreSections: List<DiscoverSection> = emptyList(),
    val genresExhausted: Boolean = false,

    // YouTube Music sub-tab home
    val ytmHome: YouTubeMusicHomeFeed? = null,
    val ytmHomeLoading: Boolean = false,
    val ytmHomeError: UiText? = null,
    val ytmSelectedChipParams: String? = null,
)

val moodChips = listOf(
    MoodChip(R.string.mood_chill, "chill vibes music"),
    MoodChip(R.string.mood_energize, "energize pump up music"),
    MoodChip(R.string.mood_focus, "focus concentration music"),
    MoodChip(R.string.mood_feel_good, "feel good happy music"),
    MoodChip(R.string.mood_party, "party dance music"),
    MoodChip(R.string.mood_sad, "sad emotional music"),
    MoodChip(R.string.mood_workout, "workout gym music"),
    MoodChip(R.string.mood_sleep, "sleep relaxing music"),
    MoodChip(R.string.mood_romance, "romantic love songs"),
    MoodChip(R.string.mood_commute, "driving road trip music"),
    MoodChip(R.string.mood_gaming, "gaming music mix"),
)

val allGenres = listOf(
    // Core genres
    GenreQuery(R.string.genre_pop, "pop music hits"),
    GenreQuery(R.string.genre_rock, "rock music"),
    GenreQuery(R.string.genre_hiphop, "hip hop rap music"),
    GenreQuery(R.string.genre_rnb_soul, "r&b soul music"),
    GenreQuery(R.string.genre_jazz, "jazz music"),
    GenreQuery(R.string.genre_electronic, "electronic dance music"),
    GenreQuery(R.string.genre_classical, "classical music"),
    GenreQuery(R.string.genre_country, "country music"),
    GenreQuery(R.string.genre_metal, "metal music"),
    GenreQuery(R.string.genre_blues, "blues music"),
    GenreQuery(R.string.genre_indie, "indie alternative music"),
    GenreQuery(R.string.genre_folk, "folk acoustic music"),
    GenreQuery(R.string.genre_reggae, "reggae dancehall music"),
    GenreQuery(R.string.genre_funk, "funk music"),
    GenreQuery(R.string.genre_punk, "punk rock music"),
    GenreQuery(R.string.genre_lofi, "lo-fi hip hop beats"),
    // Regional & cultural
    GenreQuery(R.string.genre_latin, "latin reggaeton music"),
    GenreQuery(R.string.genre_kpop, "k-pop music"),
    GenreQuery(R.string.genre_jpop, "j-pop japanese music"),
    GenreQuery(R.string.genre_afrobeats, "afrobeats african music"),
    GenreQuery(R.string.genre_bollywood, "bollywood hindi songs"),
    GenreQuery(R.string.genre_arabic, "arabic music"),
    GenreQuery(R.string.genre_french_pop, "french pop musique"),
    // Subgenres & styles
    GenreQuery(R.string.genre_ambient, "ambient music"),
    GenreQuery(R.string.genre_phonk, "phonk music"),
    GenreQuery(R.string.genre_drill, "drill music"),
    GenreQuery(R.string.genre_trap, "trap music"),
    GenreQuery(R.string.genre_dnb, "drum and bass music"),
    GenreQuery(R.string.genre_house, "house music"),
    GenreQuery(R.string.genre_techno, "techno music"),
    GenreQuery(R.string.genre_trance, "trance music"),
    GenreQuery(R.string.genre_shoegaze, "shoegaze dream pop"),
    GenreQuery(R.string.genre_post_punk, "post punk dark wave"),
    GenreQuery(R.string.genre_grunge, "grunge music"),
    GenreQuery(R.string.genre_ska, "ska music"),
    GenreQuery(R.string.genre_gospel, "gospel music"),
    GenreQuery(R.string.genre_amapiano, "amapiano music"),
    GenreQuery(R.string.genre_bossa_nova, "bossa nova music"),
    GenreQuery(R.string.genre_cumbia, "cumbia music"),
    GenreQuery(R.string.genre_salsa, "salsa music"),
    // Decades
    GenreQuery(R.string.genre_80s, "80s music hits"),
    GenreQuery(R.string.genre_90s, "90s music hits"),
    GenreQuery(R.string.genre_2000s, "2000s music hits"),
    // Misc
    GenreQuery(R.string.genre_soundtracks, "film soundtrack music"),
    GenreQuery(R.string.genre_musical_theater, "broadway musical songs"),
    GenreQuery(R.string.genre_vgm, "video game soundtrack music"),
    GenreQuery(R.string.genre_disney, "disney songs music"),
)

private const val INITIAL_GENRE_COUNT = 6
private const val GENRE_LOAD_BATCH = 4

@HiltViewModel
class YouTubeViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val youtubeRepository: YouTubeRepository,
    private val youtubeMusicRepository: YouTubeMusicRepository,
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

    val searchHistoryEnabled: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
        settingsDataStore.searchHistoryEnabled,
        settingsDataStore.searchHistoryYoutube,
    ) { global, perSource -> global && perSource }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val lastVideoId: StateFlow<String?> = settingsDataStore.lastYoutubeVideoId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var searchJob: Job? = null
    private var nextPage: Any? = null
    private var moodJob: Job? = null
    private var ytmHomeJob: Job? = null
    private val discoveryJobs = mutableListOf<Job>()

    private val shuffledGenres = allGenres.shuffled()
    private var loadedGenreCount = 0

    init {
        applyDefaultSource()
        loadDiscoveryFeed()
    }

    /**
     * Resets the active sub-tab to the source configured in Settings. Called from
     * [init] and on every YouTube-tab entry, so opening the tab always lands on the
     * user's default; a manual [setActiveSource] switch is a temporary, per-visit
     * override discarded the next time the tab is opened.
     */
    fun applyDefaultSource() {
        viewModelScope.launch {
            val default = settingsDataStore.youtubeDefaultSource.firstOrNull()
            val source = if (default == "youtube_music") YouTubeSource.YouTubeMusic else YouTubeSource.YouTube
            if (_uiState.value.activeSource != source) {
                _uiState.update { it.copy(activeSource = source) }
            }
            if (source == YouTubeSource.YouTubeMusic && _uiState.value.ytmHome == null) {
                loadYtmHome()
            }
        }
    }

    // ── Source sub-tab switching ────────────────────────────────────────

    fun setActiveSource(source: YouTubeSource) {
        if (_uiState.value.activeSource == source) return
        // Cancel in-flight search because the filter vocabulary changes between sources.
        searchJob?.cancel()
        nextPage = null
        _uiState.update {
            it.copy(
                activeSource = source,
                results = emptyList(),
                isLoading = false,
                hasMore = true,
                error = null,
                selectedFilter = null,
                searchGeneration = it.searchGeneration + 1,
            )
        }
        if (source == YouTubeSource.YouTubeMusic && _uiState.value.ytmHome == null) {
            loadYtmHome()
        }
    }

    private fun loadYtmHome(params: String? = null) {
        ytmHomeJob?.cancel()
        _uiState.update { it.copy(ytmHomeLoading = true, ytmHomeError = null) }
        ytmHomeJob = viewModelScope.launch {
            try {
                val feed = if (params == null) {
                    youtubeMusicRepository.getHome()
                } else {
                    youtubeMusicRepository.getMoodHome(params)
                }
                _uiState.update {
                    it.copy(
                        ytmHome = feed,
                        ytmHomeLoading = false,
                        ytmSelectedChipParams = params,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(ytmHomeLoading = false, ytmHomeError = UiText.orResource(e.message, R.string.snackbar_failed_load))
                }
            }
        }
    }

    fun onYtmChipSelected(params: String?) {
        if (params == _uiState.value.ytmSelectedChipParams) {
            loadYtmHome(params = null)
        } else {
            loadYtmHome(params = params)
        }
    }

    fun retryYtmHome() {
        loadYtmHome(_uiState.value.ytmSelectedChipParams)
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
                    DiscoverSection(
                        title = UiText.StringResource(
                            R.string.ytm_section_discover,
                            listOf(UiText.StringResource(genre.displayNameRes)),
                        ),
                    )
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
                    it.copy(recommendationsSection = DiscoverSection(isLoading = false))
                }
                return
            }

            val trackTitle = trackDao.getById("yt_$videoId")?.title
            _uiState.update {
                it.copy(
                    lastPlayedTrackTitle = trackTitle,
                    recommendationsSection = DiscoverSection(
                        title = if (trackTitle != null) {
                            UiText.StringResource(R.string.ytm_section_because_listened, listOf(trackTitle))
                        } else {
                            UiText.StringResource(R.string.ytm_section_recommended)
                        },
                        isLoading = true,
                    ),
                )
            }

            val recommendations = youtubeRepository.getRecommendations(
                "https://www.youtube.com/watch?v=$videoId",
            )
            _uiState.update {
                it.copy(
                    recommendationsSection = it.recommendationsSection.copy(
                        items = recommendations,
                        isLoading = false,
                    ),
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update {
                it.copy(
                    recommendationsSection = it.recommendationsSection.copy(
                        isLoading = false,
                        error = e.message,
                    ),
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
                    DiscoverSection(
                        title = UiText.StringResource(
                            R.string.ytm_section_discover,
                            listOf(UiText.StringResource(genre.displayNameRes)),
                        ),
                    )
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

    fun onMoodSelected(mood: MoodChip?) {
        if (mood == null || mood == _uiState.value.selectedMood) {
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
                val (results, _) = youtubeRepository.search(
                    query = mood.query,
                    filter = "songs",
                    page = null,
                )
                _uiState.update { it.copy(moodResults = results, isMoodLoading = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(isMoodLoading = false, moodError = UiText.orResource(e.message, R.string.snackbar_failed_load))
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
            val source = _uiState.value.activeSource
            val uiFilter = _uiState.value.selectedFilter
            val page = if (resetResults) null else nextPage

            val results: List<SearchResult>
            val newNextPage: Any?
            when (source) {
                YouTubeSource.YouTube -> {
                    val (r, np) = youtubeRepository.search(query = query, filter = uiFilter, page = page)
                    results = r
                    newNextPage = np
                }

                YouTubeSource.YouTubeMusic -> {
                    // YT Music Innertube search returns no opaque page token in this pass,
                    // so we always fetch the first page and disable pagination.
                    results = youtubeMusicRepository.search(query = query, filter = uiFilter)
                    newNextPage = null
                }
            }
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
                it.copy(isLoading = false, error = UiText.orResource(e.message, R.string.common_search_failed))
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
                _uiState.update {
                    it.copy(
                        error = UiText.StringResource(
                            R.string.error_import_playlist,
                            listOf(e.message ?: UiText.StringResource(R.string.error_unknown)),
                        ),
                    )
                }
            }
        }
    }

    suspend fun getTrackInfo(videoUrl: String): Track = youtubeRepository.getTrackInfo(videoUrl)

    suspend fun resolvePlaylistTracks(playlistUrl: String): List<Track> = youtubeRepository.getPlaylistTracks(playlistUrl).first
}
