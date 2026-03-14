package com.dustvalve.next.android.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.remote.DustvalveCollectionScraper
import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.LibraryItem
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.AccountRepository
import com.dustvalve.next.android.data.local.db.dao.PlaylistDao
import com.dustvalve.next.android.domain.repository.AlbumRepository
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class LibraryUiState(
    val libraryItems: List<LibraryItem> = emptyList(),
    val fullyDownloadedPlaylistIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val renameTarget: Playlist? = null,
    val renameTargetTracks: List<Track> = emptyList(),
    val deleteTarget: LibraryItem? = null,
    val shapeTarget: LibraryItem? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val albumRepository: AlbumRepository,
    private val accountRepository: AccountRepository,
    private val collectionScraper: DustvalveCollectionScraper,
    private val settingsDataStore: SettingsDataStore,
    private val downloadAlbumUseCase: DownloadAlbumUseCase,
    private val downloadRepository: DownloadRepository,
    private val playlistDao: PlaylistDao,
    private val favoriteDao: FavoriteDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var collectionJob: Job? = null

    init {
        initLibraryItems()
        syncSystemPlaylists()
        collectFullyDownloadedPlaylists()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun showCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true) }
    }

    fun dismissCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    fun showRenameDialog(playlist: Playlist) {
        _uiState.update { it.copy(renameTarget = playlist) }
        viewModelScope.launch {
            try {
                val tracks = playlistRepository.getTracksInPlaylistSync(playlist.id)
                _uiState.update { it.copy(renameTargetTracks = tracks) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun dismissRenameDialog() {
        _uiState.update { it.copy(renameTarget = null, renameTargetTracks = emptyList()) }
    }

    fun showDeleteDialog(item: LibraryItem) {
        _uiState.update { it.copy(deleteTarget = item) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(deleteTarget = null) }
    }

    fun showShapeDialog(item: LibraryItem) {
        _uiState.update { it.copy(shapeTarget = item) }
    }

    fun dismissShapeDialog() {
        _uiState.update { it.copy(shapeTarget = null) }
    }

    fun createPlaylist(name: String, shapeKey: String? = null, iconUrl: String? = null) {
        viewModelScope.launch {
            try {
                playlistRepository.createPlaylist(name, shapeKey, iconUrl)
                _uiState.update { it.copy(showCreateDialog = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(error = e.message ?: "Failed to create playlist") }
            }
        }
    }

    fun updatePlaylistAppearance(playlistId: String, name: String, shapeKey: String?, iconUrl: String?) {
        viewModelScope.launch {
            try {
                playlistRepository.updatePlaylistAppearance(playlistId, name, shapeKey, iconUrl)
                _uiState.update { it.copy(renameTarget = null, renameTargetTracks = emptyList()) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(error = e.message ?: "Failed to update playlist") }
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            try {
                playlistRepository.deletePlaylist(playlistId)
                _uiState.update { it.copy(deleteTarget = null) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(error = e.message ?: "Failed to delete playlist") }
            }
        }
    }

    fun pinPlaylist(playlistId: String, isPinned: Boolean) {
        viewModelScope.launch {
            try {
                playlistRepository.pinPlaylist(playlistId, isPinned)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(error = e.message ?: "Failed to update playlist") }
            }
        }
    }

    fun pinFavorite(favoriteId: String, isPinned: Boolean) {
        viewModelScope.launch {
            try {
                favoriteDao.setPinned(favoriteId, isPinned)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(error = "Failed to update pin") }
            }
        }
    }

    fun deleteFavorite(favoriteId: String) {
        viewModelScope.launch {
            try {
                favoriteDao.delete(favoriteId)
                _uiState.update { it.copy(deleteTarget = null) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(error = "Failed to remove from library") }
            }
        }
    }

    fun updateFavoriteShape(favoriteId: String, shapeKey: String?) {
        viewModelScope.launch {
            try {
                favoriteDao.setShapeKey(favoriteId, shapeKey)
                _uiState.update { it.copy(shapeTarget = null) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(error = "Failed to update shape") }
            }
        }
    }

    private fun collectFullyDownloadedPlaylists() {
        viewModelScope.launch {
            combine(
                playlistDao.getAllPlaylistTrackMappings(),
                downloadRepository.getDownloadedTrackIds(),
            ) { mappings, downloadedIds ->
                val downloadedSet = downloadedIds.toSet()
                val byPlaylist = mappings.groupBy { it.playlistId }
                byPlaylist.filter { (_, tracks) ->
                    tracks.isNotEmpty() && tracks.all { it.trackId in downloadedSet }
                }.keys
            }
                .catch { /* ignore */ }
                .collect { ids ->
                    _uiState.update { it.copy(fullyDownloadedPlaylistIds = ids) }
                }
        }
    }

    private fun initLibraryItems() {
        viewModelScope.launch {
            playlistRepository.ensureSystemPlaylistsExist()

            combine(
                playlistRepository.getAllPlaylists(),
                favoriteDao.getFavoritedAlbumsWithInfo(),
                favoriteDao.getFavoritedArtistsWithInfo(),
            ) { playlists, favAlbums, favArtists ->
                val playlistItems = playlists.map { LibraryItem.PlaylistItem(it) }
                val albumItems = favAlbums.map { info ->
                    LibraryItem.AlbumItem(
                        favoriteId = info.id,
                        name = info.albumTitle,
                        artist = info.albumArtist,
                        artUrl = info.albumArtUrl,
                        albumUrl = info.albumUrl,
                        isPinned = info.isPinned,
                        addedAt = info.addedAt,
                        shapeKey = info.shapeKey,
                    )
                }
                val artistItems = favArtists.map { info ->
                    LibraryItem.ArtistItem(
                        favoriteId = info.id,
                        name = info.artistName,
                        imageUrl = info.artistImageUrl,
                        artistUrl = info.artistUrl,
                        isPinned = info.isPinned,
                        addedAt = info.addedAt,
                        shapeKey = info.shapeKey,
                    )
                }
                (playlistItems + albumItems + artistItems).sortedWith(
                    compareByDescending<LibraryItem> { it.isPinned }
                        .thenByDescending { it.addedAt }
                )
            }
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            error = e.message ?: "Failed to load library",
                            isLoading = false,
                        )
                    }
                }
                .collect { items ->
                    _uiState.update {
                        it.copy(
                            libraryItems = items,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    private fun syncSystemPlaylists() {
        viewModelScope.launch {
            try {
                playlistRepository.syncFavoritesPlaylist()
                playlistRepository.syncDownloadsPlaylist()
                playlistRepository.syncRecentPlaylist()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Best-effort sync
            }
        }
        loadCollection()
    }

    private fun loadCollection() {
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            val accountState = accountRepository.getAccountState().first()
            val fanId = accountState.fanId ?: return@launch

            try {
                val result = collectionScraper.getCollection(fanId)
                // Persist purchase info for HQ downloads
                for ((albumId, info) in result.purchaseInfo) {
                    try {
                        albumRepository.updatePurchaseInfo(albumId, info)
                    } catch (_: Exception) { /* best-effort */ }
                }
                playlistRepository.syncCollectionPlaylist()
                autoDownloadCollection(result.albums)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Best-effort
            }
        }
    }

    private fun autoDownloadCollection(albums: List<Album>) {
        viewModelScope.launch {
            try {
                val enabled = settingsDataStore.autoDownloadCollection.first()
                if (!enabled) return@launch
                for (albumStub in albums) {
                    try {
                        val album = albumRepository.getAlbumDetail(albumStub.url)
                        downloadAlbumUseCase(album)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Best-effort: skip failures
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort: never block UI
            }
        }
    }
}
