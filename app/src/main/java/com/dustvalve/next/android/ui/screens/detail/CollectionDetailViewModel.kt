package com.dustvalve.next.android.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.PlaylistDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.domain.model.MusicCollection
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.MusicSourceRegistry
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.repository.SourceConcept
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
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
    val error: String? = null,
    val isFavorite: Boolean = false,
    val isImported: Boolean = false,
    val isImporting: Boolean = false,
    val importedPlaylistId: String? = null,
    val isDownloading: Boolean = false,
    val downloadedTrackIds: Set<String> = emptySet(),
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
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val favoriteDao: FavoriteDao,
    private val database: DustvalveNextDatabase,
    private val downloadRepository: DownloadRepository,
    private val downloadAlbumUseCase: DownloadAlbumUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionDetailUiState())
    val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()

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

    fun load(sourceId: String, url: String, nameHint: String) {
        val key = "$sourceId|$url"
        if (loadedKey == key && _uiState.value.tracks.isNotEmpty()) return
        loadedKey = key
        _uiState.update {
            it.copy(
                sourceId = sourceId,
                collectionUrl = url,
                name = nameHint,
                isLoading = true,
                error = null,
            )
        }

        viewModelScope.launch {
            val source = sources[sourceId]
            if (source == null) {
                _uiState.update { it.copy(isLoading = false, error = "Unknown source: $sourceId") }
                return@launch
            }
            if (SourceConcept.COLLECTION !in source.capabilities) {
                _uiState.update { it.copy(isLoading = false, error = "Source '$sourceId' does not expose collections") }
                return@launch
            }
            try {
                val collection: MusicCollection = source.getCollection(url)
                paginationCursor = collection.continuation
                val isFav = favoriteDao.isFavorite(url)
                val displayName = collection.name.ifBlank { nameHint }
                val existing = playlistDao.getPlaylistByName(displayName)
                _uiState.update {
                    it.copy(
                        name = displayName,
                        coverUrl = collection.coverUrl,
                        tracks = collection.tracks,
                        isLoading = false,
                        hasMore = collection.hasMore,
                        isFavorite = isFav,
                        isImported = existing != null,
                        importedPlaylistId = existing?.id,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load collection")
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
                var playlistId: String? = null
                database.withTransaction {
                    trackDao.insertAll(state.tracks.map { it.toEntity() })
                    val pl = playlistRepository.createPlaylist(state.name)
                    playlistId = pl.id
                    playlistRepository.addTracksToPlaylist(pl.id, state.tracks.map { it.id })
                }
                _uiState.update {
                    it.copy(isImported = true, isImporting = false, importedPlaylistId = playlistId)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isImporting = false, error = "Failed to import: ${e.message}") }
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
                    favoriteDao.delete(url)
                    val playlistId = state.importedPlaylistId
                        ?: playlistDao.getPlaylistByName(state.name)?.id
                    if (playlistId != null) {
                        playlistRepository.deletePlaylist(playlistId)
                        _uiState.update { it.copy(isImported = false, importedPlaylistId = null) }
                    }
                } else {
                    val favType = when (state.sourceId) {
                        "youtube" -> "youtube_playlist"
                        else -> "collection"
                    }
                    favoriteDao.insert(FavoriteEntity(id = url, type = favType))
                    importToLibrary()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isFavorite = prev) }
            }
        }
    }

    fun downloadAll() {
        val tracks = _uiState.value.tracks
        if (tracks.isEmpty() || _uiState.value.isDownloading) return
        _uiState.update { it.copy(isDownloading = true) }
        viewModelScope.launch {
            try {
                for (track in tracks) {
                    if (track.id !in _uiState.value.downloadedTrackIds) {
                        downloadAlbumUseCase.downloadTrack(track)
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
            for (track in _uiState.value.tracks) {
                try {
                    downloadAlbumUseCase.deleteTrackDownload(track.id)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }
    }
}
