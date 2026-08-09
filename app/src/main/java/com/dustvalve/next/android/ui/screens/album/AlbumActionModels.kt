package com.dustvalve.next.android.ui.screens.album

import androidx.compose.runtime.Immutable

@Immutable
data class AlbumActionBarState(
    val isFavorite: Boolean,
    val isDownloading: Boolean,
    val allTracksDownloaded: Boolean,
    val artistEnabled: Boolean,
    val hasTracks: Boolean,
)

@Immutable
data class AlbumActionBarActions(
    val onPlayAll: () -> Unit,
    val onShuffle: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onDownload: () -> Unit,
    val onOpenArtist: () -> Unit,
)
