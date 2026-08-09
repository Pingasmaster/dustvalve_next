package com.dustvalve.next.android.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dustvalve.next.android.ui.adaptive.AdaptiveLayoutInfo

@Composable
internal fun FullPlayerSheetHost(
    adaptiveInfo: AdaptiveLayoutInfo,
    state: PlayerUiState,
    sheets: FullPlayerSheetState,
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    FullPlayerVolumeSheet(
        adaptiveInfo = adaptiveInfo,
        visible = sheets.showVolumeSheet,
        volumeLevel = state.volumeLevel,
        audioOutputDevices = state.audioOutputDevices,
        activeAudioDevice = state.activeAudioDevice,
        onDismiss = { sheets.showVolumeSheet = false },
        onVolumeChange = playerViewModel::setVolume,
        onSelectDevice = playerViewModel::setAudioOutputDevice,
    )

    FullPlayerDeleteDownloadDialog(
        trackTitle = state.currentTrack?.title?.takeIf { sheets.showDeleteDownloadDialog },
        onConfirm = {
            playerViewModel.onDeleteTrackDownload()
            sheets.showDeleteDownloadDialog = false
        },
        onDismiss = { sheets.showDeleteDownloadDialog = false },
    )

    FullPlayerDebugSheet(
        adaptiveInfo = adaptiveInfo,
        track = state.currentTrack?.takeIf { sheets.showDebugSheet },
        currentSourcePath = state.currentSourcePath,
        currentPlaybackFormat = state.currentPlaybackFormat,
        downloadedTrackIds = state.downloadedTrackIds,
        downloadingTrackId = state.downloadingTrackId,
        onDismiss = { sheets.showDebugSheet = false },
    )

    FullPlayerAddToPlaylistSheet(
        visible = sheets.showPlaylistSheet,
        playlists = state.playlists,
        onDismiss = { sheets.showPlaylistSheet = false },
        onPlaylistSelected = { playlistId ->
            sheets.showPlaylistSheet = false
            playerViewModel.addToPlaylist(playlistId)
        },
        onCreatePlaylist = { name, shapeKey, iconUrl ->
            sheets.showPlaylistSheet = false
            playerViewModel.createPlaylistAndAddTrack(name, shapeKey, iconUrl)
        },
    )

    FullPlayerUpNextContextSheet(
        adaptiveInfo = adaptiveInfo,
        contextEntry = sheets.upNextContextEntry,
        onDismiss = { sheets.upNextContextEntry = null },
        onToggleFavorite = playerViewModel::toggleFavoriteById,
        onAddToPlaylist = { sheets.showUpNextPlaylistSheet = true },
        onRemoveFromQueue = playerViewModel::removeQueueEntry,
    )

    FullPlayerAddToPlaylistSheet(
        visible = sheets.showUpNextPlaylistSheet,
        playlists = state.playlists,
        onDismiss = { sheets.showUpNextPlaylistSheet = false },
        onPlaylistSelected = { playlistId ->
            sheets.showUpNextPlaylistSheet = false
            sheets.upNextContextEntry?.let { entry ->
                playerViewModel.addTrackToPlaylist(playlistId, entry.track.id)
            }
            sheets.upNextContextEntry = null
        },
        onCreatePlaylist = { name, shapeKey, iconUrl ->
            sheets.showUpNextPlaylistSheet = false
            sheets.upNextContextEntry?.let { entry ->
                playerViewModel.createPlaylistAndAddArbitraryTrack(name, shapeKey, iconUrl, entry.track.id)
            }
            sheets.upNextContextEntry = null
        },
    )

    FullPlayerQueueSheet(
        adaptiveInfo = adaptiveInfo,
        visible = sheets.showQueueSheet,
        currentQueueIndex = state.currentQueueIndex,
        currentTrackId = state.currentTrack?.id,
        downloadedTrackIds = state.downloadedTrackIds,
        onDismiss = { sheets.showQueueSheet = false },
        onEntryLongClick = { sheets.upNextContextEntry = it },
    )
}
