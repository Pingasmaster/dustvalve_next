package com.dustvalve.next.android.ui.screens.spotify

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dustvalve.next.android.R
import com.dustvalve.next.android.ui.components.TrackRow
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.ui.theme.segmentedItemShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpotifyAlbumDetailScreen(
    albumUri: String,
    albumName: String,
    albumImageUrl: String?,
    onArtistClick: (uri: String, name: String, imageUrl: String?) -> Unit,
    onBack: () -> Unit,
    onExpandPlayer: () -> Unit = {},
    playerViewModel: PlayerViewModel,
    viewModel: SpotifyAlbumDetailViewModel = hiltViewModel(key = albumUri),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(albumUri) {
        viewModel.loadAlbum(albumUri, albumName, albumImageUrl)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = state.albumName,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = state.artistName.takeIf { it.isNotBlank() }?.let { artist ->
                    {
                        Text(
                            text = artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.common_cd_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets(0),
            )
        },
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    ContainedLoadingIndicator()
                }
            }
            state.error != null && state.tracks.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error ?: stringResource(R.string.detail_error_load_album),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.loadAlbum(albumUri, albumName, albumImageUrl)
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.common_action_retry))
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(bottom = 10.dp),
                ) {
                    // Hero art
                    item(key = "hero") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .animateItem(),
                        ) {
                            val heroArtUrl = state.imageUrl
                            if (!heroArtUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = heroArtUrl,
                                    contentDescription = state.albumName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                )
                            }
                            // Top scrim
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f),
                                                Color.Transparent,
                                            ),
                                        ),
                                    ),
                            )
                            // Bottom gradient
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                                MaterialTheme.colorScheme.surface,
                                            ),
                                        ),
                                    ),
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                            ) {
                                Text(
                                    text = state.albumName,
                                    style = MaterialTheme.typography.headlineLargeEmphasized,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (state.artistName.isNotBlank()) {
                                    TextButton(
                                        onClick = {
                                            if (state.artistUri.isNotBlank()) {
                                                onArtistClick(state.artistUri, state.artistName, null)
                                            }
                                        },
                                        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                                        shapes = ButtonDefaults.shapes(),
                                    ) {
                                        Text(
                                            text = state.artistName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Meta info
                    item(key = "meta") {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                                .animateItem(),
                        ) {
                            Text(
                                text = pluralStringResource(R.plurals.track_count, state.tracks.size, state.tracks.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            state.releaseDate?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Action bar
                    item(key = "actions") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .animateItem(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = {
                                        if (state.tracks.isNotEmpty()) {
                                            playerViewModel.playAlbum(state.tracks, 0)
                                            onExpandPlayer()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = state.tracks.isNotEmpty(),
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_play_arrow),
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.common_play_all))
                                }

                                FilledTonalIconButton(
                                    onClick = {
                                        if (state.tracks.isNotEmpty()) {
                                            playerViewModel.playAlbum(state.tracks.shuffled(), 0)
                                            onExpandPlayer()
                                        }
                                    },
                                    enabled = state.tracks.isNotEmpty(),
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_shuffle),
                                        contentDescription = stringResource(R.string.common_cd_shuffle_play),
                                    )
                                }

                                FilledTonalIconToggleButton(
                                    checked = state.isFavorite,
                                    onCheckedChange = { viewModel.toggleFavorite() },
                                    colors = IconToggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        checkedContentColor = MaterialTheme.colorScheme.primary,
                                    ),
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (state.isFavorite) R.drawable.ic_favorite
                                            else R.drawable.ic_favorite_border,
                                        ),
                                        contentDescription = if (state.isFavorite) stringResource(R.string.detail_cd_remove_favorites)
                                            else stringResource(R.string.detail_cd_add_favorites),
                                    )
                                }

                                val allDownloaded = state.tracks.isNotEmpty() &&
                                    state.tracks.all { it.id in state.downloadedTrackIds }
                                FilledTonalIconButton(
                                    onClick = {
                                        if (allDownloaded) {
                                            showDeleteDialog = true
                                        } else {
                                            viewModel.downloadAll()
                                        }
                                    },
                                    enabled = !state.isDownloading,
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    if (state.isDownloading) {
                                        CircularWavyProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(
                                                if (allDownloaded) R.drawable.ic_download_done
                                                else R.drawable.ic_download,
                                            ),
                                            contentDescription = if (allDownloaded) stringResource(R.string.detail_cd_delete_downloads)
                                                else stringResource(R.string.detail_cd_download_all),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Track list
                    if (state.tracks.isNotEmpty()) {
                        item(key = "tracks_header") {
                            Text(
                                text = com.dustvalve.next.android.ui.util.tracksHeaderLabel(
                                    trackCount = state.tracks.size,
                                    totalDurationSec = state.tracks.sumOf { it.duration.toDouble() }.toLong(),
                                ),
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                modifier = Modifier
                                    .padding(
                                        start = 20.dp, end = 20.dp,
                                        top = 16.dp, bottom = 4.dp,
                                    )
                                    .animateItem(),
                            )
                        }

                        items(
                            count = state.tracks.size,
                            key = { state.tracks[it].id },
                        ) { index ->
                            val track = state.tracks[index]
                            val isCurrentTrack = playerState.currentTrack?.id == track.id

                            Surface(
                                modifier = Modifier
                                    .padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = if (index == 0) 8.dp else 1.dp,
                                        bottom = if (index == state.tracks.lastIndex) 0.dp else 1.dp,
                                    )
                                    .animateItem(
                                        fadeInSpec = null,
                                        fadeOutSpec = null,
                                        placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                    ),
                                shape = segmentedItemShape(index, state.tracks.size),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                TrackRow(
                                    track = track,
                                    isPlaying = isCurrentTrack && playerState.isPlaying,
                                    onClick = {
                                        playerViewModel.playAlbum(state.tracks, index)
                                        onExpandPlayer()
                                    },
                                )
                            }
                        }
                    }

                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_downloads_title)) },
            text = { Text(stringResource(R.string.detail_delete_album_source_downloads_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllDownloads()
                        showDeleteDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.common_action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            },
        )
    }
}
