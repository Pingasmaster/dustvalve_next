package com.dustvalve.next.android.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.FavoriteType
import com.dustvalve.next.android.domain.model.MusicCollection
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.FavoriteRepository
import com.dustvalve.next.android.domain.repository.MusicSourceRegistry
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.repository.SourceConcept
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import com.dustvalve.next.android.domain.usecase.ExpandSourceTracksUseCase
import com.dustvalve.next.android.download.DownloadController
import com.dustvalve.next.android.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class CollectionDetailUiState(
    val sourceId: String = "youtube",
    val collectionUrl: String = "",
    val name: String = "",
    val coverUrl: String? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: UiText? = null,
    val isFavorite: Boolean = false,
    val isImported: Boolean = false,
    val isImporting: Boolean = false,
    val importedPlaylistId: String? = null,
    val isDownloading: Boolean = false,
    val downloadedTrackIds: Set<String> = emptySet(),
    val snackbarMessage: UiText? = null,
    val isSnackbarError: Boolean = false,
)

/**
 * Source-agnostic "playlist / collection" detail VM. Loads tracks via
 * [com.dustvalve.next.android.domain.repository.MusicSource.getCollection].
 * Favorite / import-to-library / download flows mirror the prior
 * YouTube-playlist VM.
 *
 * Replaces `YouTubePlaylistDetailViewModel`.
 */
@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    private val sources: MusicSourceRegistry,
    private val playlistRepository: PlaylistRepository,
    private val favoriteRepository: FavoriteRepository,
    private val downloadRepository: DownloadRepository,
    private val downloadAlbumUseCase: DownloadAlbumUseCase,
    private val downloadController: DownloadController,
    private val expandSourceTracks: ExpandSourceTracksUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionDetailUiState())
    val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()

    /** Invoked when the user taps the snackbar's Retry action. */
    var retryAction: (() -> Unit)? = null
        private set

    private var loadedKey: String? = null
    private var paginationCursor: Any? = null
    private var loadMoreJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            downloadRepository.getDownloadedTrackIds()
                .catch { }
                .collect { ids ->
                    _uiState.update { it.copy(downloadedTrackIds = ids.toSet()) }
                }
        }
    }

    fun load(sourceId: String, url: String, nameHint: String, coverHint: String? = null) {
        val key = "$sourceId|$url"
        if (loadedKey == key && _uiState.value.tracks.isNotEmpty()) return
        loadedKey = key
        _uiState.update {
            it.copy(
                sourceId = sourceId,
                collectionUrl = url,
                name = nameHint,
                coverUrl = coverHint,
                isLoading = true,
                error = null,
            )
        }

        viewModelScope.launch {
            val source = sources[sourceId]
            if (source == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = UiText.StringResource(R.string.error_unknown_source, listOf(sourceId)))
                }
                return@launch
            }
            if (SourceConcept.COLLECTION !in source.capabilities) {
                _uiState.update { it.copy(isLoading = false, error = UiText.StringResource(R.string.error_source_no_collections)) }
                return@launch
            }
            try {
                val collection: MusicCollection = source.getCollection(url)
                paginationCursor = collection.continuation
                val isFav = favoriteRepository.isFavorite(url)
                val displayName = collection.name.ifBlank { nameHint }
                // Name matching is DISPLAY-ONLY (drives the "already imported"
                // affordance) - the repository deliberately returns a Boolean,
                // never a playlist id; see PlaylistRepository.playlistExistsByName.
                val alreadyImported = playlistRepository.playlistExistsByName(displayName)
                _uiState.update {
                    it.copy(
                        name = displayName,
                        coverUrl = collection.coverUrl
                            ?: coverHint
                            ?: collection.tracks.firstOrNull()?.artUrl?.takeIf { art -> art.isNotBlank() },
                        tracks = collection.tracks,
                        isLoading = false,
                        hasMore = collection.hasMore,
                        isFavorite = isFav,
                        isImported = alreadyImported || it.importedPlaylistId != null,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(isLoading = false, error = UiText.orResource(e.message, R.string.detail_error_load_collection))
                }
            }
        }
    }

    /**
     * Loads the next page of an infinite-scroll collection (e.g. a YouTube
     * Mix). No-op for sources/collections that returned `hasMore = false`.
     */
    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoadingMore || state.isLoading) return
        val cursor = paginationCursor ?: return
        val source = sources[state.sourceId] ?: return
        loadMoreJob?.cancel()
        _uiState.update { it.copy(isLoadingMore = true) }
        loadMoreJob = viewModelScope.launch {
            try {
                val page = source.getCollection(state.collectionUrl, cursor)
                val existingIds = state.tracks.mapTo(HashSet()) { it.id }
                val deduped = page.tracks.filter { it.id !in existingIds }
                paginationCursor = page.continuation
                _uiState.update {
                    it.copy(
                        tracks = it.tracks + deduped,
                        isLoadingMore = false,
                        hasMore = page.hasMore && deduped.isNotEmpty(),
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Surface failure as "no more" to stop further scroll-triggered
                // loads, but don't blow the screen away.
                _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
            }
        }
    }

    fun importToLibrary() {
        val state = _uiState.value
        if (state.isImported || state.isImporting || state.tracks.isEmpty()) return
        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            try {
                val tracks = expandLoadedTracks()
                if (tracks.isEmpty()) {
                    _uiState.update { it.copy(isImporting = false) }
                    return@launch
                }
                // No favorite parameters: this screen's favorite row is inserted
                // separately (in toggleFavorite, BEFORE the import) - the
                // historical outside-the-transaction ordering is preserved.
                val playlist = playlistRepository.importTracksAsPlaylist(_uiState.value.name, tracks)
                _uiState.update {
                    it.copy(isImported = true, isImporting = false, importedPlaylistId = playlist.id)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isImporting = false, error = failedImport(e)) }
            }
        }
    }

    fun toggleFavorite() {
        val state = _uiState.value
        val url = state.collectionUrl.ifBlank { return }
        val prev = state.isFavorite
        _uiState.update { it.copy(isFavorite = !prev) }
        viewModelScope.launch {
            try {
                if (prev) {
                    favoriteRepository.remove(url)
                    // Delete ONLY the playlist this session imported (id captured in
                    // importToLibrary). Never fall back to a name lookup: it could
                    // resolve to - and destroy - an unrelated user playlist that
                    // happens to share the collection's name. A leftover imported
                    // playlist is acceptable; deleting a user playlist is not.
                    val playlistId = state.importedPlaylistId
                    if (playlistId != null) {
                        playlistRepository.deletePlaylist(playlistId)
                        _uiState.update { it.copy(isImported = false, importedPlaylistId = null) }
                    }
                } else {
                    val favType = when (state.sourceId) {
                        "youtube" -> FavoriteType.YOUTUBE_PLAYLIST
                        "soundcloud" -> FavoriteType.SOUNDCLOUD_PLAYLIST
                        else -> FavoriteType.COLLECTION
                    }
                    favoriteRepository.add(url, favType)
                    importToLibrary()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isFavorite = prev) }
            }
        }
    }

    /**
     * Expand paginated collections up to [ExpandSourceTracksUseCase.MAX_TRACKS],
     * then play starting at [startIndex] (clamped). Used for Play all / row tap
     * so infinite feeds do not queue only the first scrolled page.
     */
    fun playExpanded(startIndex: Int, play: (List<Track>, Int) -> Unit) {
        viewModelScope.launch {
            val tracks = expandLoadedTracks()
            if (tracks.isEmpty()) return@launch
            val index = startIndex.coerceIn(0, tracks.lastIndex)
            play(tracks, index)
        }
    }

    /** Expand, then play a shuffled copy (still capped). */
    fun playExpandedShuffled(play: (List<Track>, Int) -> Unit) {
        viewModelScope.launch {
            val tracks = expandLoadedTracks()
            if (tracks.isEmpty()) return@launch
            play(tracks.shuffled(), 0)
        }
    }

    fun downloadAll() {
        if (_uiState.value.isDownloading) return
        _uiState.update { it.copy(isDownloading = true) }
        viewModelScope.launch {
            try {
                val tracks = expandLoadedTracks()
                val pending = tracks.filter { it.id !in _uiState.value.downloadedTrackIds }
                if (pending.isEmpty()) {
                    _uiState.update { it.copy(isDownloading = false) }
                    return@launch
                }
                downloadController.downloadPlaylistBlocking(
                    label = _uiState.value.name.ifEmpty { "playlist" },
                    tracks = pending,
                )
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        snackbarMessage = UiText.StringResource(R.string.snackbar_downloaded, listOf(it.name)),
                        isSnackbarError = false,
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
     * Drain remaining pages into [CollectionDetailUiState.tracks] (capped),
     * keep UI in sync, and return the expanded list for play/download.
     */
    private suspend fun expandLoadedTracks(): List<Track> {
        val state = _uiState.value
        val source = sources[state.sourceId] ?: return state.tracks
        if (state.tracks.isEmpty() && !state.hasMore) return emptyList()
        _uiState.update { it.copy(isLoadingMore = true) }
        return try {
            val expanded = expandSourceTracks.expandCollection(
                source = source,
                url = state.collectionUrl,
                seedTracks = state.tracks,
                seedContinuation = paginationCursor,
                seedHasMore = state.hasMore,
            )
            paginationCursor = null
            _uiState.update {
                it.copy(
                    tracks = expanded,
                    hasMore = false,
                    isLoadingMore = false,
                    coverUrl = it.coverUrl
                        ?: expanded.firstOrNull()?.artUrl?.takeIf { art -> art.isNotBlank() },
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
            for (track in _uiState.value.tracks) {
                try {
                    downloadAlbumUseCase.deleteTrackDownload(track.id)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
            _uiState.update {
                it.copy(
                    snackbarMessage = UiText.StringResource(R.string.snackbar_deleted_downloads_for, listOf(it.name)),
                    isSnackbarError = false,
                )
            }
        }
    }

    fun clearSnackbar() {
        retryAction = null
        _uiState.update { it.copy(snackbarMessage = null, isSnackbarError = false) }
    }

    private fun failedImport(e: Exception): UiText = UiText.StringResource(
        R.string.error_import_playlist,
        listOf(e.message ?: UiText.StringResource(R.string.error_unknown)),
    )
}
