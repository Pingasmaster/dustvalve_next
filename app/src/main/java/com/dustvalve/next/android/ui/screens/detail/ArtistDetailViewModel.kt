package com.dustvalve.next.android.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.FavoriteType
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.ArtistRepository
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.FavoriteRepository
import com.dustvalve.next.android.domain.repository.MusicSourceRegistry
import com.dustvalve.next.android.domain.repository.SourceConcept
import com.dustvalve.next.android.domain.repository.TrackCacheRepository
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import com.dustvalve.next.android.domain.usecase.ExpandSourceTracksUseCase
import com.dustvalve.next.android.download.BatchDownloadResult
import com.dustvalve.next.android.download.DownloadController
import com.dustvalve.next.android.download.downloadEachDeferringFailures
import com.dustvalve.next.android.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class ArtistDetailUiState(
    val sourceId: String = "bandcamp",
    val artistUrl: String = "",
    /** Present as soon as headers / cache resolve. */
    val artist: Artist? = null,
    /** Paginated flat track feed (YouTube only - Bandcamp populates `artist.albums`). */
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val isFavorite: Boolean = false,
    val isDownloading: Boolean = false,
    val isLoadingMix: Boolean = false,
    val downloadedTrackIds: Set<String> = emptySet(),
    val downloadedAlbumIds: Set<String> = emptySet(),
    val error: UiText? = null,
    val snackbarMessage: UiText? = null,
    val isSnackbarError: Boolean = false,
)

/**
 * Source-agnostic artist detail VM. Picks the right [MusicSource] out of the
 * registry by [ArtistDetailUiState.sourceId] and delegates:
 *
 * - Bandcamp: `getArtist` returns an Artist with populated `albums`; the
 *   screen renders an album grid. Favorites + auto-download are managed via
 *   [ArtistRepository] (cache-first, persisted).
 * - YouTube: `getArtist` returns metadata only; `getArtistTracks` feeds the
 *   paginated flat track list. Favorites + downloads roll up to the artist
 *   URL via the generic DAOs.
 *
 * Replaces `ArtistDetailViewModel` (Bandcamp) + `YouTubeArtistDetailViewModel`.
 */
@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val sources: MusicSourceRegistry,
    private val artistRepository: ArtistRepository,
    private val favoriteRepository: FavoriteRepository,
    private val trackCacheRepository: TrackCacheRepository,
    private val downloadRepository: DownloadRepository,
    private val downloadAlbumUseCase: DownloadAlbumUseCase,
    private val downloadController: DownloadController,
    private val expandSourceTracks: ExpandSourceTracksUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    /** Invoked when the user taps the snackbar's Retry action. */
    var retryAction: (() -> Unit)? = null
        private set

    private var loadedKey: String? = null
    private var loadJob: Job? = null
    private var nextPage: Any? = null
    private var preloadedAlbumIds: Set<String> = emptySet()

    init {
        collectDownloaded()
    }

    private fun collectDownloaded() {
        viewModelScope.launch {
            downloadRepository.getDownloadedTrackIds()
                .catch { /* ignore */ }
                .collect { ids ->
                    _uiState.update { it.copy(downloadedTrackIds = ids.toSet()) }
                }
        }
        viewModelScope.launch {
            downloadRepository.getDownloadedAlbumIds()
                .catch { /* ignore */ }
                .collect { ids ->
                    _uiState.update { it.copy(downloadedAlbumIds = ids.toSet()) }
                }
        }
    }

    /**
     * Start (or re-start) loading an artist. [name] and [imageUrl] are
     * shown immediately while the real load is in flight - useful for
     * YouTube channels whose browse endpoint doesn't return the channel
     * image, so the caller passes the thumbnail it already has from the
     * SearchResult.
     *
     * Bandcamp uses [ArtistRepository.getArtistDetailFlow]: cache first,
     * then opportunistic revalidate on every open so new releases appear
     * as soon as the page is visited. Flat-feed sources (YouTube,
     * SoundCloud) keep the suspend + paginated track path.
     */
    fun load(sourceId: String, url: String, name: String? = null, imageUrl: String? = null) {
        val key = "$sourceId|$url"
        val sameLoaded = loadedKey == key && _uiState.value.artist != null
        // Flat-feed artists are expensive to re-page; Bandcamp must always
        // re-subscribe so the Flow revalidates discography on every visit.
        if (sameLoaded && sourceId != "bandcamp") return
        nextPage = null

        val seed = if (name != null || imageUrl != null) {
            Artist(
                id = url,
                name = name.orEmpty(),
                url = url,
                imageUrl = imageUrl,
                bio = null,
                location = null,
                albums = emptyList(),
            )
        } else {
            null
        }
        _uiState.update {
            it.copy(
                sourceId = sourceId,
                artistUrl = url,
                artist = when {
                    sameLoaded -> it.artist
                    seed != null -> seed
                    else -> null
                },
                tracks = if (sameLoaded) it.tracks else emptyList(),
                // Keep the current grid visible while Bandcamp revalidates.
                isLoading = !sameLoaded,
                hasMore = if (sameLoaded) it.hasMore else false,
                error = null,
            )
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val source = sources[sourceId]
            if (source == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = UiText.StringResource(R.string.error_unknown_source, listOf(sourceId)))
                }
                return@launch
            }

            if (sourceId == "bandcamp") {
                loadBandcampArtist(url, imageUrl, key)
                return@launch
            }

            try {
                val artist = source.getArtist(url)
                val isFav = favoriteRepository.isFavorite(url)
                _uiState.update {
                    it.copy(
                        artist = artist.copy(
                            imageUrl = artist.imageUrl ?: imageUrl,
                        ),
                        isFavorite = isFav,
                    )
                }

                if (SourceConcept.ARTIST_TRACKS in source.capabilities) {
                    artistRepository.cacheRemoteArtist(
                        artist.copy(imageUrl = artist.imageUrl ?: imageUrl),
                        source = sourceId,
                    )
                    val page = source.getArtistTracks(url, continuation = null)
                    nextPage = page.continuation
                    if (page.tracks.isNotEmpty()) {
                        trackCacheRepository.cacheTracks(page.tracks)
                    }
                    _uiState.update {
                        it.copy(
                            tracks = page.tracks,
                            hasMore = page.hasMore,
                            isLoading = false,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
                loadedKey = key
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(isLoading = false, error = UiText.orResource(e.message, R.string.detail_error_load_artist))
                }
            }
        }
    }

    private suspend fun loadBandcampArtist(url: String, imageUrl: String?, key: String) {
        try {
            artistRepository.getArtistDetailFlow(url)
                .catch { e ->
                    if (e is CancellationException) throw e
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = UiText.orResource(e.message, R.string.detail_error_load_artist),
                        )
                    }
                }
                .collect { artist ->
                    loadedKey = key
                    _uiState.update {
                        it.copy(
                            artist = artist.copy(
                                imageUrl = artist.imageUrl ?: imageUrl,
                            ),
                            // Favorite is keyed by the stable Bandcamp hash id,
                            // not the URL - use the repository's value.
                            isFavorite = artist.isFavorite,
                            isLoading = false,
                            error = null,
                        )
                    }
                    val albumIds = artist.albums.map { it.id }.toSet()
                    if (albumIds != preloadedAlbumIds) {
                        preloadedAlbumIds = albumIds
                        preloadMixPool()
                    }
                }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update {
                it.copy(isLoading = false, error = UiText.orResource(e.message, R.string.detail_error_load_artist))
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return
        val source = sources[state.sourceId] ?: return
        if (SourceConcept.ARTIST_TRACKS !in source.capabilities) return

        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                val page = source.getArtistTracks(state.artistUrl, continuation = nextPage)
                nextPage = page.continuation
                if (page.tracks.isNotEmpty()) {
                    trackCacheRepository.cacheTracks(page.tracks)
                }
                _uiState.update {
                    val existing = it.tracks.mapTo(HashSet()) { t -> t.id }
                    val newTracks = page.tracks.filter { t -> t.id !in existing }
                    it.copy(
                        tracks = it.tracks + newTracks,
                        isLoadingMore = false,
                        hasMore = page.hasMore,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun toggleFavorite() {
        val state = _uiState.value
        val url = state.artistUrl.ifBlank { return }
        val prev = state.isFavorite
        _uiState.update { it.copy(isFavorite = !prev) }
        viewModelScope.launch {
            try {
                if (state.sourceId == "bandcamp") {
                    val artistId = state.artist?.id ?: return@launch
                    artistRepository.toggleFavorite(artistId)
                } else {
                    // YT path - the Artist id IS the URL; the repository
                    // persists the entity so library INNER JOINs on artist_id
                    // resolve.
                    if (prev) {
                        artistRepository.unfavoriteArtist(url)
                    } else {
                        val art = state.artist
                        if (art != null) {
                            artistRepository.favoriteRemoteArtist(art, source = "youtube")
                        } else {
                            // No loaded artist metadata: historically the
                            // favorite row was still inserted (only the
                            // artist-row persist was skipped).
                            favoriteRepository.add(url, FavoriteType.ARTIST)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isFavorite = prev) }
            }
        }
    }

    /**
     * Expand paginated artist tracks up to [ExpandSourceTracksUseCase.MAX_TRACKS]
     * then play from [startIndex]. Bandcamp (album grid) callers should keep
     * using the album list directly.
     */
    fun playExpanded(startIndex: Int, play: (List<Track>, Int) -> Unit) {
        viewModelScope.launch {
            val tracks = expandLoadedArtistTracks()
            if (tracks.isEmpty()) return@launch
            play(tracks, startIndex.coerceIn(0, tracks.lastIndex))
        }
    }

    fun downloadAll() {
        val state = _uiState.value
        if (state.isDownloading) return
        _uiState.update { it.copy(isDownloading = true) }
        viewModelScope.launch {
            try {
                if (state.sourceId == "bandcamp") {
                    val artist = state.artist
                    if (artist == null || artist.albums.isEmpty()) {
                        _uiState.update { it.copy(isDownloading = false) }
                        return@launch
                    }
                    // Artist-grid albums are stubs (empty tracks). Resolve and
                    // download via the album path - flatMap { tracks } is a no-op.
                    downloadAlbumUseCase.downloadArtist(artist)
                    retryAction = null
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            snackbarMessage = UiText.StringResource(
                                R.string.snackbar_downloaded,
                                listOf(artist.name),
                            ),
                            isSnackbarError = false,
                        )
                    }
                    return@launch
                }

                val pending = expandLoadedArtistTracks()
                    .filter { it.id !in state.downloadedTrackIds }

                // One undownloadable track (typically an age-restricted
                // YouTube video) used to end the whole artist download where
                // it stood. Skip it, come back to it once at the very end,
                // and report what was lost instead of stopping.
                val result = downloadEachDeferringFailures(pending) { track ->
                    // Re-check per track: the auto-download coordinator or
                    // another screen may have landed it while we were working.
                    if (track.id !in _uiState.value.downloadedTrackIds) {
                        downloadController.downloadTrackBlocking(track)
                    }
                }

                // Cleared on a clean run so a Retry left over from an earlier
                // failure can't ride along with a success snackbar.
                retryAction = if (result.hasUnavailable) {
                    { downloadAll() }
                } else {
                    null
                }
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        snackbarMessage = downloadSummary(result, state.artist?.name.orEmpty()),
                        isSnackbarError = result.allFailed,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                retryAction = { downloadAll() }
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        snackbarMessage =
                        e.message?.let { m -> UiText.DynamicString(m) }
                            ?: UiText.StringResource(R.string.snackbar_download_failed),
                        isSnackbarError = true,
                    )
                }
            }
        }
    }

    /**
     * Success line for a finished batch: the plain "Downloaded <artist>" when
     * everything landed, the underlying error when nothing did, and a count of
     * what was given up on in between.
     */
    private fun downloadSummary(result: BatchDownloadResult<Track>, artistName: String): UiText = when {
        !result.hasUnavailable -> UiText.StringResource(R.string.snackbar_downloaded, listOf(artistName))

        result.allFailed -> UiText.orResource(result.error?.message, R.string.snackbar_download_failed)

        else -> UiText.PluralsResource(
            R.plurals.snackbar_downloaded_partial,
            result.unavailable.size,
            listOf(result.downloaded, result.attempted, result.unavailable.size),
        )
    }

    private suspend fun expandLoadedArtistTracks(): List<Track> {
        val state = _uiState.value
        val source = sources[state.sourceId] ?: return state.tracks
        if (SourceConcept.ARTIST_TRACKS !in source.capabilities) return state.tracks
        if (state.tracks.isEmpty() && !state.hasMore) return emptyList()
        _uiState.update { it.copy(isLoadingMore = true) }
        return try {
            val expanded = expandSourceTracks.expandArtistTracks(
                source = source,
                url = state.artistUrl,
                seedTracks = state.tracks,
                seedContinuation = nextPage,
                seedHasMore = state.hasMore,
            )
            nextPage = null
            if (expanded.isNotEmpty()) {
                trackCacheRepository.cacheTracks(expanded)
            }
            _uiState.update {
                it.copy(
                    tracks = expanded,
                    hasMore = false,
                    isLoadingMore = false,
                )
            }
            expanded
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update { it.copy(isLoadingMore = false) }
            state.tracks
        }
    }

    fun deleteAllDownloads() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.sourceId == "bandcamp") {
                val artist = state.artist ?: return@launch
                // Album stubs have empty tracks; delete by album id instead.
                try {
                    downloadAlbumUseCase.deleteArtistDownloads(artist)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            } else {
                val ids = state.tracks.map { it.id }
                for (id in ids) {
                    try {
                        downloadAlbumUseCase.deleteTrackDownload(id)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                    }
                }
            }
            _uiState.update {
                it.copy(
                    snackbarMessage = UiText.StringResource(
                        R.string.snackbar_deleted_downloads_for,
                        listOf(state.artist?.name.orEmpty()),
                    ),
                    isSnackbarError = false,
                )
            }
        }
    }

    fun clearSnackbar() {
        retryAction = null
        _uiState.update { it.copy(snackbarMessage = null, isSnackbarError = false) }
    }

    /**
     * Album-grid sources (Bandcamp): play every cached track of every album, in
     * random order.
     *
     * The pool is whatever [preloadMixPool] has stocked, so it spans the full
     * discography rather than only the albums that happen to be downloaded.
     * Falls back to stocking on demand when the preload has not finished.
     */
    fun loadMixTracks(onLoaded: (List<Track>) -> Unit) {
        val state = _uiState.value
        if (state.isLoadingMix) return
        val albums = state.artist?.albums ?: return
        if (albums.isEmpty()) return
        _uiState.update { it.copy(isLoadingMix = true) }
        viewModelScope.launch {
            try {
                val albumIds = albums.map { it.id }
                if (artistRepository.albumIdsMissingTracks(albumIds).isNotEmpty()) {
                    // Preload still running (or it failed): stock what is left
                    // before playing, so the first tap is never a thin mix.
                    stockMixPool()
                }
                val tracks = artistRepository.getArtistMixTracks(albumIds)
                if (tracks.isNotEmpty()) onLoaded(tracks)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                _uiState.update { it.copy(isLoadingMix = false) }
            }
        }
    }

    /**
     * Flat-feed sources (YouTube, SoundCloud): drain the paginated artist feed,
     * then play it shuffled.
     *
     * [expandLoadedArtistTracks] writes the drained list back into state and
     * into the track cache, so the network cost is paid once and every later
     * shuffle is a re-sort of what is already held.
     */
    fun playMixShuffled(play: (List<Track>, Int) -> Unit) {
        if (_uiState.value.isLoadingMix) return
        _uiState.update { it.copy(isLoadingMix = true) }
        viewModelScope.launch {
            try {
                val tracks = expandLoadedArtistTracks()
                if (tracks.isNotEmpty()) play(tracks.shuffled(), 0)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                _uiState.update { it.copy(isLoadingMix = false) }
            }
        }
    }

    /**
     * Fetch, in the background, the track lists the mix needs.
     *
     * An artist's albums arrive without their tracks (the cached path builds
     * them with an empty list), so nothing but previously-opened albums is in
     * the pool. Fetching each album's detail persists its tracks, and the
     * database is the indefinite store - this cost is paid once per album.
     */
    private fun preloadMixPool() {
        val state = _uiState.value
        if (state.artist?.albums.isNullOrEmpty()) return
        viewModelScope.launch {
            try {
                stockMixPool()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Best effort: a failed preload only means the mix falls back
                // to stocking on demand.
            }
        }
    }

    private suspend fun stockMixPool() {
        val state = _uiState.value
        val albums = state.artist?.albums.orEmpty()
        if (albums.isEmpty()) return
        val source = sources[state.sourceId] ?: return
        if (SourceConcept.ALBUM !in source.capabilities) return
        val missing = artistRepository.albumIdsMissingTracks(albums.map { it.id }).toSet()
        if (missing.isEmpty()) return
        for (album in albums.filter { it.id in missing }) {
            try {
                // Persists the album with its tracks; the mix reads them back
                // out of the database on the next getArtistMixTracks call.
                source.getAlbum(album.url)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // One unreachable album must not strand the rest of the mix.
            }
        }
    }
}
