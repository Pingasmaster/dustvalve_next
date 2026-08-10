package com.dustvalve.next.android.ui.screens.player

import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.util.UiText
import com.dustvalve.next.android.util.onFailure
import com.dustvalve.next.android.util.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Queue edits, favorites, downloads, and playlist adds. */
internal class PlayerLibraryCoordinator(
    private val scope: CoroutineScope,
    private val extraState: PlayerExtraStateFlow,
    core: PlayerCoreDeps,
    libraryDeps: PlayerLibraryDeps,
    private val currentTrack: () -> Track?,
) {
    private val queueManager = core.queueManager
    private val libraryRepository = core.libraryRepository
    private val downloadAlbumUseCase = libraryDeps.downloadAlbumUseCase
    private val downloadController = libraryDeps.downloadController
    private val playlistRepository = libraryDeps.playlistRepository
    private var favoriteJob: Job? = null
    private var downloadJob: Job? = null

    fun onToggleFavorite() {
        if (favoriteJob?.isActive == true) return
        val track = currentTrack() ?: return
        favoriteJob = scope.launch {
            runPlayerUiAction {
                libraryRepository.toggleTrackFavorite(track)
            }
        }
    }

    fun onDownloadTrack() {
        val track = currentTrack() ?: return
        if (track.isLocal || track.isStreamOnlyOrBlocked) return
        if (extraState.value.downloadingTrackId != null) return
        extraState.update { it.copy(downloadingTrackId = track.id) }
        downloadJob = scope.launch {
            runPlayerUiActionResult(R.string.snackbar_download_failed) {
                downloadController.downloadTrackBlocking(track)
            }.onSuccess { _ ->
                extraState.update { state ->
                    state.copy(
                        downloadingTrackId = null,
                        snackbarMessage = UiText.StringResource(R.string.snackbar_downloaded, listOf(track.title)),
                        isSnackbarError = false,
                    )
                }
            }.onFailure { error, _ ->
                extraState.update { state ->
                    state.copy(
                        downloadingTrackId = null,
                        snackbarMessage = error,
                        isSnackbarError = true,
                    )
                }
            }
        }
    }

    fun onDeleteTrackDownload() {
        val track = currentTrack() ?: return
        scope.launch {
            runPlayerUiActionResult(R.string.snackbar_delete_failed) {
                downloadAlbumUseCase.deleteTrackDownload(track.id)
            }.onSuccess { _ ->
                extraState.update { state ->
                    state.copy(
                        snackbarMessage = UiText.StringResource(R.string.snackbar_deleted, listOf(track.title)),
                        isSnackbarError = false,
                    )
                }
            }.onFailure { error, _ ->
                extraState.update { state ->
                    state.copy(
                        snackbarMessage = error,
                        isSnackbarError = true,
                    )
                }
            }
        }
    }

    fun addToPlaylist(playlistId: String) {
        val track = currentTrack() ?: return
        addTrackToPlaylist(playlistId, track)
    }

    fun createPlaylistAndAddTrack(name: String, shapeKey: String?, iconUrl: String?) {
        val track = currentTrack() ?: return
        createPlaylistAndAddArbitraryTrack(name, shapeKey, iconUrl, track)
    }

    fun playQueueEntry(uid: Long, skipToIndex: (Int) -> Unit) {
        val index = queueManager.entries.value.indexOfFirst { it.uid == uid }
        if (index >= 0) skipToIndex(index)
    }

    fun removeQueueEntry(uid: Long) = queueManager.removeEntry(uid)

    fun moveQueueEntry(fromUid: Long, toUid: Long) = queueManager.moveEntry(fromUid, toUid)

    fun toggleFavoriteById(trackId: String) {
        scope.launch {
            runPlayerUiAction {
                libraryRepository.toggleTrackFavorite(trackId)
            }
        }
    }

    fun toggleFavorite(track: Track) {
        scope.launch {
            runPlayerUiAction {
                libraryRepository.toggleTrackFavorite(track)
            }
        }
    }

    fun playNext(track: Track) {
        queueManager.playNext(track)
        extraState.update {
            it.copy(
                snackbarMessage = UiText.StringResource(R.string.snackbar_playing_next, listOf(track.title)),
                isSnackbarError = false,
            )
        }
    }

    fun addToQueue(track: Track) {
        queueManager.addToQueue(track)
        extraState.update {
            it.copy(
                snackbarMessage = UiText.PluralsResource(R.plurals.snackbar_added_n_to_queue, 1),
                isSnackbarError = false,
            )
        }
    }

    fun addAllToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        for (t in tracks) queueManager.addToQueue(t)
        extraState.update {
            it.copy(
                snackbarMessage = UiText.PluralsResource(R.plurals.snackbar_added_n_to_queue, tracks.size),
                isSnackbarError = false,
            )
        }
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        scope.launch {
            runPlayerUiActionResult(R.string.snackbar_add_to_playlist_failed) {
                playlistRepository.addTrackToPlaylist(playlistId, track)
            }.onSuccess { added ->
                if (!added) return@onSuccess
                showAddedToPlaylistSnackbar(playlistId)
            }.onFailure { error, _ ->
                extraState.update {
                    it.copy(
                        snackbarMessage = error,
                        isSnackbarError = true,
                    )
                }
            }
        }
    }

    /** Id-only path for callers that already cached the track; no success toast on miss. */
    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        scope.launch {
            runPlayerUiActionResult(R.string.snackbar_add_to_playlist_failed) {
                playlistRepository.addTrackToPlaylist(playlistId, trackId)
            }.onSuccess { added ->
                if (!added) return@onSuccess
                showAddedToPlaylistSnackbar(playlistId)
            }.onFailure { error, _ ->
                extraState.update {
                    it.copy(
                        snackbarMessage = error,
                        isSnackbarError = true,
                    )
                }
            }
        }
    }

    private fun showAddedToPlaylistSnackbar(playlistId: String) {
        val name = extraState.value.playlists.find { it.id == playlistId }?.name
            ?: UiText.StringResource(R.string.playlist_fallback_name)
        extraState.update {
            it.copy(
                snackbarMessage = UiText.StringResource(R.string.snackbar_added_to_playlist, listOf(name)),
                isSnackbarError = false,
            )
        }
    }

    fun createPlaylistAndAddArbitraryTrack(name: String, shapeKey: String?, iconUrl: String?, track: Track) {
        scope.launch {
            runPlayerUiActionResult(R.string.snackbar_create_playlist_failed) {
                val playlist = playlistRepository.createPlaylist(name, shapeKey, iconUrl)
                val added = playlistRepository.addTrackToPlaylist(playlist.id, track)
                if (!added) {
                    playlistRepository.deletePlaylist(playlist.id)
                    error("Failed to add track to new playlist")
                }
                playlist
            }.onSuccess { playlist ->
                extraState.update {
                    it.copy(
                        snackbarMessage = UiText.StringResource(R.string.snackbar_added_to_playlist, listOf(playlist.name)),
                        isSnackbarError = false,
                    )
                }
            }.onFailure { error, _ ->
                extraState.update {
                    it.copy(
                        snackbarMessage = error,
                        isSnackbarError = true,
                    )
                }
            }
        }
    }

    fun createPlaylistAndAddArbitraryTrack(name: String, shapeKey: String?, iconUrl: String?, trackId: String) {
        scope.launch {
            runPlayerUiActionResult(R.string.snackbar_create_playlist_failed) {
                val playlist = playlistRepository.createPlaylist(name, shapeKey, iconUrl)
                val added = playlistRepository.addTrackToPlaylist(playlist.id, trackId)
                if (!added) {
                    playlistRepository.deletePlaylist(playlist.id)
                    error("Failed to add track to new playlist")
                }
                playlist
            }.onSuccess { playlist ->
                extraState.update {
                    it.copy(
                        snackbarMessage = UiText.StringResource(R.string.snackbar_added_to_playlist, listOf(playlist.name)),
                        isSnackbarError = false,
                    )
                }
            }.onFailure { error, _ ->
                extraState.update {
                    it.copy(
                        snackbarMessage = error,
                        isSnackbarError = true,
                    )
                }
            }
        }
    }

    fun clearSnackbar() {
        extraState.update { it.copy(snackbarMessage = null) }
    }

    fun showNoAlbumSnackbar() {
        extraState.update {
            it.copy(
                snackbarMessage = UiText.StringResource(R.string.snackbar_no_album_for_track),
                isSnackbarError = false,
            )
        }
    }
}
