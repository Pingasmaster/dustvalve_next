package com.dustvalve.next.android.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.data.local.db.dao.ArtistDao
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.dao.getFavoriteIds
import com.dustvalve.next.android.data.local.db.entity.ArtistEntity
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.mapper.toDomain
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.ArtistRepository
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.MusicSourceRegistry
import com.dustvalve.next.android.domain.repository.SourceConcept
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class ArtistDetailUiState(
    val sourceId: String = "bandcamp",
    val artistUrl: String = "",
    /** Present as soon as headers / cache resolve. */
    val artist: Artist? = null,
    /** Paginated flat track feed (YouTube only — Bandcamp populates `artist.albums`). */
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val isFavorite: Boolean = false,
    val isDownloading: Boolean = false,
    val isLoadingMix: Boolean = false,
    val downloadedTrackIds: Set<String> = emptySet(),
    val downloadedAlbumIds: Set<String> = emptySet(),
    val error: String? = null,
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
    private val favoriteDao: FavoriteDao,
    private val artistDao: ArtistDao,
    private val trackDao: TrackDao,
    private val downloadRepository: DownloadRepository,
    private val downloadAlbumUseCase: DownloadAlbumUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    private var loadedKey: String? = null
    private var nextPage: Any? = null

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
     * shown immediately while the real load is in flight — useful for
     * YouTube channels whose browse endpoint doesn't return the channel
     * image, so the caller passes the thumbnail it already has from the
     * SearchResult.
     */
    fun load(
        sourceId: String,
        url: String,
        name: String? = null,
        imageUrl: String? = null,
    ) {
        val key = "$sourceId|$url"
        if (loadedKey == key && _uiState.value.artist != null) return
        loadedKey = key
        nextPage = null

        // Seed with the caller-provided hint so the top bar isn't blank.
        val seed = if (name != null || imageUrl != null) {
            Artist(
                id = url, name = name.orEmpty(), url = url,
                imageUrl = imageUrl, bio = null, location = null,
                albums = emptyList(),
            )
        } else {
            null
        }
        _uiState.update {
            it.copy(
                sourceId = sourceId,
                artistUrl = url,
                artist = seed,
                tracks = emptyList(),
                isLoading = true,
                hasMore = false,
                error = null,
            )
        }

        viewModelScope.launch {
            val source = sources[sourceId]
            if (source == null) {
                _uiState.update { it.copy(isLoading = false, error = "Unknown source: $sourceId") }
                return@launch
            }

            try {
                val artist = source.getArtist(url)
                val isFav = favoriteDao.isFavorite(url)
                _uiState.update {
                    it.copy(
                        artist = artist.copy(
                            // Keep a hint image if the source didn't populate one.
                            imageUrl = artist.imageUrl ?: imageUrl,
                        ),
                        isFavorite = isFav,
                    )
                }

                // YouTube-style flat track feed.
                if (SourceConcept.ARTIST_TRACKS in source.capabilities) {
                    persistYouTubeArtist(artist.copy(imageUrl = artist.imageUrl ?: imageUrl))
                    val page = source.getArtistTracks(url, continuation = null)
                    nextPage = page.continuation
                    if (page.tracks.isNotEmpty()) {
                        trackDao.insertAll(page.tracks.map { it.toEntity() })
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
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load artist")
                }
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
                    trackDao.insertAll(page.tracks.map { it.toEntity() })
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
                    // YT path — the Artist id IS the URL; persist the entity
                    // so library INNER JOINs on artist_id resolve.
                    if (prev) {
                        favoriteDao.delete(url)
                    } else {
                        val art = state.artist
                        if (art != null) persistYouTubeArtist(art)
                        favoriteDao.insert(FavoriteEntity(id = url, type = "artist"))
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isFavorite = prev) }
            }
        }
    }

    fun downloadAll() {
        val state = _uiState.value
        if (state.isDownloading) return
        _uiState.update { it.copy(isDownloading = true) }
        viewModelScope.launch {
            try {
                if (state.sourceId == "bandcamp") {
                    val albums = state.artist?.albums ?: emptyList()
                    for (album in albums) {
                        for (track in album.tracks) {
                            if (track.id !in state.downloadedTrackIds) {
                                downloadAlbumUseCase.downloadTrack(track)
                            }
                        }
                    }
                } else {
                    for (track in state.tracks) {
                        if (track.id !in state.downloadedTrackIds) {
                            downloadAlbumUseCase.downloadTrack(track)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            _uiState.update { it.copy(isDownloading = false) }
        }
    }

    fun deleteAllDownloads() {
        viewModelScope.launch {
            val state = _uiState.value
            val ids: List<String> = if (state.sourceId == "bandcamp") {
                state.artist?.albums?.flatMap { it.tracks }?.map { it.id }.orEmpty()
            } else {
                state.tracks.map { it.id }
            }
            for (id in ids) {
                try {
                    downloadAlbumUseCase.deleteTrackDownload(id)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }
    }

    /** Bandcamp-only: load the artist mix (one track per album, interleaved). */
    fun loadMixTracks(onLoaded: (List<Track>) -> Unit) {
        val state = _uiState.value
        if (state.isLoadingMix) return
        val albums = state.artist?.albums ?: return
        if (albums.isEmpty()) return
        _uiState.update { it.copy(isLoadingMix = true) }
        viewModelScope.launch {
            try {
                val tracks = artistRepository.getArtistMixTracks(albums.map { it.id })
                onLoaded(tracks)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                _uiState.update { it.copy(isLoadingMix = false) }
            }
        }
    }

    private suspend fun persistYouTubeArtist(artist: Artist) {
        try {
            artistDao.insert(
                ArtistEntity(
                    id = artist.url,
                    name = artist.name,
                    url = artist.url,
                    imageUrl = artist.imageUrl,
                    bio = artist.bio,
                    location = artist.location,
                    source = "youtube",
                ),
            )
        } catch (_: Throwable) { /* best-effort */ }
    }

    @Suppress("unused")
    private fun md5Hash(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
