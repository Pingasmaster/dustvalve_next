package com.dustvalve.next.android.ui.screens.detail

import androidx.compose.runtime.Immutable

@Immutable
data class DetailPlaybackActions(
    val onPlayAll: () -> Unit,
    val onShuffle: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onDownload: () -> Unit,
)

@Immutable
data class ArtistTracksActions(
    val playback: DetailPlaybackActions,
    val onLoadMore: () -> Unit,
    val onTrackClick: (Int) -> Unit,
    val onAlbumClick: (String) -> Unit = {},
)

@Immutable
data class DetailActionBarPrimary(
    val label: String,
    val iconRes: Int,
    val enabled: Boolean,
    val loading: Boolean,
    val onClick: () -> Unit,
)

@Immutable
data class DetailActionBarState(
    val isFavorite: Boolean,
    val isDownloading: Boolean,
    val allDownloaded: Boolean,
    val downloadEnabled: Boolean,
)

@Immutable
data class DetailActionBarExtras(
    val onToggleFavorite: () -> Unit,
    val onDownload: () -> Unit,
    val onShuffle: (() -> Unit)? = null,
    val shuffleEnabled: Boolean = false,
)

@Immutable
data class CollectionActionBarState(
    val isFavorite: Boolean,
    val isDownloading: Boolean,
    val allTracksDownloaded: Boolean,
    val hasTracks: Boolean,
)


data class ArtistDetailArgs(
    val sourceId: String,
    val artistUrl: String,
    val artistNameHint: String? = null,
    val artistImageHint: String? = null,
)

data class CollectionDetailArgs(
    val sourceId: String,
    val collectionUrl: String,
    val collectionName: String,
    val collectionCoverHint: String? = null,
)
