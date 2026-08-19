package com.dustvalve.next.android.ui.screens.player

import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.util.UiText
import kotlinx.coroutines.flow.MutableStateFlow

/** Mutable slice of [PlayerUiState] owned by player coordinators. */
internal data class PlayerExtraState(
    val downloadedTrackIds: Set<String> = emptySet(),
    val downloadingTrackId: String? = null,
    val blockingDownloadTrackId: String? = null,
    val downloadProgressFraction: Float? = null,
    val playlists: List<Playlist> = emptyList(),
    val snackbarMessage: UiText? = null,
    val isSnackbarError: Boolean = false,
    val currentPlaybackFormat: AudioFormat? = null,
    val currentSourcePath: String? = null,
    val userPlaylistTrackIds: Set<String> = emptySet(),
    val isLoadingTrack: Boolean = false,
)

internal typealias PlayerExtraStateFlow = MutableStateFlow<PlayerExtraState>
