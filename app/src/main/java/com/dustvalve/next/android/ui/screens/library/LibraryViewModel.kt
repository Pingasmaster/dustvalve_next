package com.dustvalve.next.android.ui.screens.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.R
import com.dustvalve.next.android.data.transfer.PlaylistTransferRepository
import com.dustvalve.next.android.domain.model.FavoriteType
import com.dustvalve.next.android.domain.model.LibraryItem
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.FavoriteRepository
import com.dustvalve.next.android.domain.repository.LibraryRepository
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.util.UiText
import com.dustvalve.next.android.util.onFailure
import com.dustvalve.next.android.util.runCatchingUi
import com.dustvalve.next.android.util.runCatchingUiIgnore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val libraryItems: List<LibraryItem> = emptyList(),
    val fullyDownloadedPlaylistIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: UiText? = null,
    val showCreateDialog: Boolean = false,
    val renameTarget: Playlist? = null,
    val renameTargetTracks: List<Track> = emptyList(),
    val deleteTarget: LibraryItem? = null,
    val shapeTarget: LibraryItem? = null,
    /** Non-null while the export offline/lightweight choice dialog is shown. */
    val exportTarget: Playlist? = null,
    /** Non-null while an export/import is running (drives the progress overlay). */
    val transfer: TransferProgress? = null,
    /** One-shot success message for the snackbar. */
    val message: UiText? = null,
)

data class TransferProgress(val importing: Boolean, val done: Int, val total: Int)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val downloadRepository: DownloadRepository,
    private val favoriteRepository: FavoriteRepository,
    private val libraryRepository: LibraryRepository,
    private val playlistTransferRepository: PlaylistTransferRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

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
            runCatchingUiIgnore {
                val tracks = playlistRepository.getTracksInPlaylistSync(playlist.id)
                _uiState.update { it.copy(renameTargetTracks = tracks) }
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

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** Show the offline/lightweight choice dialog for [playlist]. */
    fun requestExport(playlist: Playlist) {
        _uiState.update { it.copy(exportTarget = playlist) }
    }

    fun dismissExport() {
        _uiState.update { it.copy(exportTarget = null) }
    }

    /** Export [playlist] to the SAF [uri]. [offline] downloads everything; otherwise metadata-only. */
    fun exportPlaylist(playlist: Playlist, offline: Boolean, uri: Uri) {
        _uiState.update { it.copy(exportTarget = null) }
        viewModelScope.launch {
            _uiState.update { it.copy(transfer = TransferProgress(importing = false, done = 0, total = playlist.trackCount)) }
            runCatchingUi(R.string.library_error_export) {
                val out = context.contentResolver.openOutputStream(uri)
                if (out == null) {
                    _uiState.update {
                        it.copy(transfer = null, error = UiText.StringResource(R.string.library_error_open_export_destination))
                    }
                    return@launch
                }
                out.use { stream ->
                    playlistTransferRepository.export(playlist.id, offline, stream) { done, total ->
                        _uiState.update { it.copy(transfer = TransferProgress(false, done, total)) }
                    }
                }
                _uiState.update {
                    it.copy(transfer = null, message = UiText.StringResource(R.string.library_exported, listOf(playlist.name)))
                }
            }.onFailure { error, cause ->
                _uiState.update { it.copy(transfer = null, error = error) }
            }
        }
    }

    /** Import a `.dvplaylist` bundle from the SAF [uri]. */
    fun importPlaylist(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(transfer = TransferProgress(importing = true, done = 0, total = 0)) }
            runCatchingUi(R.string.library_error_import) {
                val inp = context.contentResolver.openInputStream(uri)
                if (inp == null) {
                    _uiState.update { it.copy(transfer = null, error = UiText.StringResource(R.string.library_error_open_import_file)) }
                    return@launch
                }
                val playlist = inp.use { stream ->
                    playlistTransferRepository.import(stream) { done, total ->
                        _uiState.update { it.copy(transfer = TransferProgress(true, done, total)) }
                    }
                }
                _uiState.update {
                    it.copy(transfer = null, message = UiText.StringResource(R.string.library_imported, listOf(playlist.name)))
                }
            }.onFailure { error, cause ->
                _uiState.update { it.copy(transfer = null, error = error) }
            }
        }
    }

    fun createPlaylist(name: String, shapeKey: String? = null, iconUrl: String? = null) {
        viewModelScope.launch {
            runCatchingUi(R.string.snackbar_create_playlist_failed) {
                playlistRepository.createPlaylist(name, shapeKey, iconUrl)
                _uiState.update { it.copy(showCreateDialog = false) }
            }.onFailure { error, cause ->
                _uiState.update { it.copy(error = error) }
            }
        }
    }

    fun updatePlaylistAppearance(playlistId: String, name: String, shapeKey: String?, iconUrl: String?) {
        viewModelScope.launch {
            runCatchingUi(R.string.library_error_update_playlist) {
                playlistRepository.updatePlaylistAppearance(playlistId, name, shapeKey, iconUrl)
                _uiState.update { it.copy(renameTarget = null, renameTargetTracks = emptyList()) }
            }.onFailure { error, cause ->
                _uiState.update { it.copy(error = error) }
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            runCatchingUi(R.string.library_error_delete_playlist) {
                playlistRepository.deletePlaylist(playlistId)
                _uiState.update { it.copy(deleteTarget = null) }
            }.onFailure { error, cause ->
                _uiState.update { it.copy(error = error) }
            }
        }
    }

    fun pinPlaylist(playlistId: String, isPinned: Boolean) {
        viewModelScope.launch {
            runCatchingUi(R.string.library_error_update_playlist) {
                playlistRepository.pinPlaylist(playlistId, isPinned)
            }.onFailure { error, cause ->
                _uiState.update { it.copy(error = error) }
            }
        }
    }

    fun pinFavorite(favoriteId: String, type: FavoriteType, isPinned: Boolean) {
        viewModelScope.launch {
            runCatchingUiIgnore(
                onFailure = { _ ->
                    _uiState.update { it.copy(error = UiText.StringResource(R.string.library_error_update_pin)) }
                },
            ) {
                favoriteRepository.setPinned(favoriteId, type, isPinned)
            }
        }
    }

    fun deleteFavorite(favoriteId: String, type: FavoriteType) {
        viewModelScope.launch {
            runCatchingUiIgnore(
                onFailure = { _ ->
                    _uiState.update { it.copy(error = UiText.StringResource(R.string.library_error_remove)) }
                },
            ) {
                favoriteRepository.remove(favoriteId, type)
                _uiState.update { it.copy(deleteTarget = null) }
            }
        }
    }

    fun updateFavoriteShape(favoriteId: String, type: FavoriteType, shapeKey: String?) {
        viewModelScope.launch {
            runCatchingUiIgnore(
                onFailure = { _ ->
                    _uiState.update { it.copy(error = UiText.StringResource(R.string.library_error_update_shape)) }
                },
            ) {
                favoriteRepository.setShapeKey(favoriteId, type, shapeKey)
                _uiState.update { it.copy(shapeTarget = null) }
            }
        }
    }

    private fun collectFullyDownloadedPlaylists() {
        viewModelScope.launch {
            combine(
                playlistRepository.getPlaylistTrackMappings(),
                downloadRepository.getDownloadedTrackIds(),
            ) { mappings, downloadedIds ->
                val downloadedSet = downloadedIds.toSet()
                mappings.filter { (_, trackIds) ->
                    trackIds.isNotEmpty() && trackIds.all { it in downloadedSet }
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
                libraryRepository.getFavoriteAlbums(),
                libraryRepository.getFavoriteArtists(),
            ) { playlists, favAlbums, favArtists ->
                // ensureSystemPlaylistsExist sweeps the retired Bandcamp
                // purchases system playlist; filter defensively if a stale
                // row is still present mid-migration.
                val filteredPlaylists = playlists.filter { it.id != "system_collection" }
                val playlistItems = filteredPlaylists.map { LibraryItem.PlaylistItem(it) }
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
                        .thenByDescending { it.addedAt },
                )
            }
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            error = UiText.orResource(e.message, R.string.library_error_load),
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
            runCatchingUiIgnore {
                playlistRepository.syncRecentPlaylist()
            }
        }
    }
}
