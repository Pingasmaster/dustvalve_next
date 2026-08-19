package com.dustvalve.next.android.ui.screens.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.FavoriteType
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.FavoriteRepository
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.download.DownloadNotificationCenter
import com.dustvalve.next.android.player.PlaybackManager
import com.dustvalve.next.android.player.QueueManager
import com.dustvalve.next.android.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Collects download/playlist/favorite flows and auto-recovers stale streams.
 */
internal class PlayerStateCollectors(
    private val scope: CoroutineScope,
    private val extraState: PlayerExtraStateFlow,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    private val downloadRepository: DownloadRepository,
    private val downloadNotificationCenter: DownloadNotificationCenter,
    private val playlistRepository: PlaylistRepository,
    private val favoriteRepository: FavoriteRepository,
    private val playbackStreamResolver: PlaybackStreamResolver,
) {
    fun start() {
        collectDownloadedTrackIds()
        collectBlockingDownloadProgress()
        collectPlaylists()
        collectUserPlaylistTrackIds()
        collectFavoriteTrackIds()
        collectPlaybackErrors()
        collectPlaybackReadyForRetryReset()
    }

    private fun collectPlaybackErrors() {
        scope.launch {
            playbackManager.playbackError.collect { error ->
                if (error == null) return@collect
                playbackManager.clearPlaybackError()
                if (tryAutoRecoverStream(error)) return@collect
                extraState.update {
                    it.copy(
                        snackbarMessage = UiText.StringResource(R.string.snackbar_audio_stream_failed),
                        isSnackbarError = true,
                    )
                }
            }
        }
    }

    private fun collectPlaybackReadyForRetryReset() {
        scope.launch {
            playbackManager.playbackState.collect { state ->
                if (state == Player.STATE_READY) {
                    queueManager.currentTrack.value?.id?.let { playbackStreamResolver.clearAutoRetry(it) }
                }
            }
        }
    }

    private fun isRecoverableStreamError(error: PlaybackException): Boolean = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        -> true

        else -> false
    }

    private suspend fun tryAutoRecoverStream(error: PlaybackException): Boolean {
        val track = queueManager.currentTrack.value ?: return false
        if (track.isLocal ||
            !isRecoverableStreamError(error) ||
            !playbackStreamResolver.tryClaimAutoRetry(track.id)
        ) {
            return false
        }
        val resumeAt = playbackManager.currentPosition.value
        extraState.update { it.copy(isLoadingTrack = true) }
        val fresh = try {
            runPlayerUiActionOrNull { playbackStreamResolver.reResolve(track) }
        } finally {
            extraState.update { it.copy(isLoadingTrack = false) }
        }
        if (fresh?.streamUrl == null) return false
        queueManager.applyResolvedTracks(mapOf(fresh.id to fresh))
        playbackManager.playTrack(fresh)
        if (resumeAt > 0L) playbackManager.seekTo(resumeAt)
        return true
    }

    private fun collectBlockingDownloadProgress() {
        scope.launch {
            downloadNotificationCenter.progressState.collect { snapshot ->
                val id = extraState.value.blockingDownloadTrackId ?: return@collect
                val track = snapshot.activeTracks[id]
                val fraction = track?.let { progress ->
                    val total = progress.expectedTotal
                    if (total == null || total <= 0L) {
                        0f
                    } else {
                        (progress.bytesWritten.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    }
                }
                extraState.update { it.copy(downloadProgressFraction = fraction ?: it.downloadProgressFraction) }
            }
        }
    }

    private fun collectFavoriteTrackIds() {
        scope.launch {
            favoriteRepository.favoriteIds(FavoriteType.TRACK)
                .catch { /* ignore */ }
                .collect { ids ->
                    queueManager.applyFavoriteIds(ids)
                }
        }
    }

    private fun collectDownloadedTrackIds() {
        scope.launch {
            downloadRepository.getDownloadedTrackIds()
                .catch { /* ignore */ }
                .collect { ids ->
                    extraState.update { it.copy(downloadedTrackIds = ids.toSet()) }
                }
        }
    }

    private fun collectPlaylists() {
        scope.launch {
            playlistRepository.getAllPlaylists()
                .catch { /* ignore */ }
                .collect { playlists ->
                    extraState.update { it.copy(playlists = playlists) }
                }
        }
    }

    private fun collectUserPlaylistTrackIds() {
        scope.launch {
            playlistRepository.getTrackIdsInUserPlaylists()
                .catch { /* ignore */ }
                .collect { ids ->
                    extraState.update { it.copy(userPlaylistTrackIds = ids) }
                }
        }
    }
}
