package com.dustvalve.next.android.ui.screens.player

import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.RepeatMode
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.LibraryRepository
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import com.dustvalve.next.android.download.DownloadController
import com.dustvalve.next.android.player.PlaybackManager
import com.dustvalve.next.android.player.QueueManager
import com.dustvalve.next.android.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Transport, queue edits, favorites, downloads, and playlist adds. */
internal class PlayerLibraryCoordinator(
    private val scope: CoroutineScope,
    private val extraState: PlayerExtraStateFlow,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    private val libraryRepository: LibraryRepository,
    private val downloadAlbumUseCase: DownloadAlbumUseCase,
    private val downloadController: DownloadController,
    private val playlistRepository: PlaylistRepository,
    private val currentTrack: () -> Track?,
) {
    private var favoriteJob: Job? = null
    private var downloadJob: Job? = null

    fun onPlayPause() = playbackManager.togglePlayPause()
    fun onNext() = playbackManager.skipNext()
    fun onPrevious() = playbackManager.skipPrevious()
    fun onSeek(ms: Long) = playbackManager.seekTo(ms)

    fun onStop() {
        playbackManager.stop()
        queueManager.clear()
    }

    fun onToggleShuffle() {
        playbackManager.setShuffleEnabled(!playbackManager.shuffleEnabled.value)
    }

    fun onToggleRepeat() {
        val nextMode = when (playbackManager.repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        playbackManager.setRepeatMode(nextMode)
    }

    fun onToggleFavorite() {
        if (favoriteJob?.isActive == true) return
        val track = currentTrack() ?: return
        favoriteJob = scope.launch {
            runPlayerUiAction {
                libraryRepository.toggleTrackFavorite(track.id)
            }
        }
    }

    fun onDownloadTrack() {
        val track = currentTrack() ?: return
        if (track.isLocal) return
        if (extraState.value.downloadingTrackId != null) return
        extraState.update { it.copy(downloadingTrackId = track.id) }
        downloadJob = scope.launch {
            val result = runPlayerUiActionResult {
                downloadController.downloadTrackBlocking(track)
            }
            result.fold(
                onSuccess = {
                    extraState.update {
                        it.copy(
                            downloadingTrackId = null,
                            snackbarMessage = UiText.StringResource(R.string.snackbar_downloaded, listOf(track.title)),
                            isSnackbarError = false,
                        )
                    }
                },
                onFailure = { e ->
                    extraState.update {
                        it.copy(
                            downloadingTrackId = null,
                            snackbarMessage =
                            e.message?.let { UiText.DynamicString(it) } ?: UiText.StringResource(R.string.snackbar_download_failed),
                            isSnackbarError = true,
                        )
                    }
                },
            )
        }
    }

    fun onDeleteTrackDownload() {
        val track = currentTrack() ?: return
        scope.launch {
            val result = runPlayerUiActionResult {
                downloadAlbumUseCase.deleteTrackDownload(track.id)
            }
            result.fold(
                onSuccess = {
                    extraState.update {
                        it.copy(
                            snackbarMessage = UiText.StringResource(R.string.snackbar_deleted, listOf(track.title)),
                            isSnackbarError = false,
                        )
                    }
                },
                onFailure = { e ->
                    extraState.update {
                        it.copy(
                            snackbarMessage =
                            e.message?.let { UiText.DynamicString(it) } ?: UiText.StringResource(R.string.snackbar_delete_failed),
                            isSnackbarError = true,
                        )
                    }
                },
            )
        }
    }

    fun addToPlaylist(playlistId: String) {
        val track = currentTrack() ?: return
        addTrackToPlaylist(playlistId, track.id)
    }

    fun createPlaylistAndAddTrack(name: String, shapeKey: String?, iconUrl: String?) {
        val track = currentTrack() ?: return
        createPlaylistAndAddArbitraryTrack(name, shapeKey, iconUrl, track.id)
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

    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        scope.launch {
            val result = runPlayerUiActionResult {
                playlistRepository.addTrackToPlaylist(playlistId, trackId)
                extraState.value.playlists.find { it.id == playlistId }
            }
            result.fold(
                onSuccess = { playlist ->
                    extraState.update {
                        it.copy(
                            snackbarMessage = UiText.StringResource(
                                R.string.snackbar_added_to_playlist,
                                listOf(playlist?.name ?: UiText.StringResource(R.string.playlist_fallback_name)),
                            ),
                            isSnackbarError = false,
                        )
                    }
                },
                onFailure = { e ->
                    extraState.update {
                        it.copy(
                            snackbarMessage =
                            e.message?.let { UiText.DynamicString(it) } ?: UiText.StringResource(R.string.snackbar_add_to_playlist_failed),
                            isSnackbarError = true,
                        )
                    }
                },
            )
        }
    }

    fun createPlaylistAndAddArbitraryTrack(name: String, shapeKey: String?, iconUrl: String?, trackId: String) {
        scope.launch {
            val result = runPlayerUiActionResult {
                val playlist = playlistRepository.createPlaylist(name, shapeKey, iconUrl)
                playlistRepository.addTrackToPlaylist(playlist.id, trackId)
                playlist
            }
            result.fold(
                onSuccess = { playlist ->
                    extraState.update {
                        it.copy(
                            snackbarMessage = UiText.StringResource(R.string.snackbar_added_to_playlist, listOf(playlist.name)),
                            isSnackbarError = false,
                        )
                    }
                },
                onFailure = { e ->
                    extraState.update {
                        it.copy(
                            snackbarMessage =
                            e.message?.let { UiText.DynamicString(it) } ?: UiText.StringResource(R.string.snackbar_create_playlist_failed),
                            isSnackbarError = true,
                        )
                    }
                },
            )
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
