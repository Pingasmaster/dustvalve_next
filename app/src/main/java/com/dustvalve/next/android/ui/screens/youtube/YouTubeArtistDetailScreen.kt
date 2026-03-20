package com.dustvalve.next.android.ui.screens.youtube

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
fun YouTubeArtistDetailScreen(
    artistUrl: String,
    artistName: String,
    artistImageUrl: String?,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: YouTubeArtistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(artistUrl) {
        viewModel.loadArtist(artistUrl, artistName, artistImageUrl)
    }

    // Pagination: load more when near bottom
    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalCount = listState.layoutInfo.totalItemsCount
            last != null && totalCount > 0 && last.index >= totalCount - 3
        }.collect { nearEnd ->
            if (nearEnd && state.hasMore && !state.isLoadingMore && state.tracks.isNotEmpty()) {
                viewModel.loadMore()
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.0f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
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
                            text = state.error ?: "Failed to load artist",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            viewModel.loadArtist(artistUrl, artistName, artistImageUrl)
                        }) {
                            Text("Retry")
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    // Hero image with gradient and artist name overlay
                    item(key = "hero") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        ) {
                            if (!state.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = state.imageUrl,
                                    contentDescription = state.artistName,
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
                            // Bottom gradient scrim
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                MaterialTheme.colorScheme.surface,
                                            ),
                                        ),
                                    ),
                            )
                            // Artist name overlaid
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                            ) {
                                Text(
                                    text = state.artistName,
                                    style = MaterialTheme.typography.displaySmallEmphasized,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // Action row
                    item(key = "actions") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        if (state.tracks.isNotEmpty()) {
                                            playerViewModel.playAlbum(state.tracks.shuffled(), 0)
                                        }
                                    },
                                    enabled = state.tracks.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_shuffle),
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Play Mix")
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
                                        contentDescription = if (state.isFavorite) "Remove from favorites"
                                            else "Add to favorites",
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
                                            contentDescription = if (allDownloaded) "Delete downloads"
                                                else "Download all",
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
                                text = "Videos",
                                style = MaterialTheme.typography.titleLargeEmphasized,
                                modifier = Modifier.padding(
                                    start = 20.dp, end = 20.dp,
                                    top = 20.dp, bottom = 4.dp,
                                ),
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
                                        bottom = if (index == state.tracks.lastIndex && !state.hasMore) 0.dp else 1.dp,
                                    )
                                    .animateItem(
                                        fadeInSpec = null,
                                        fadeOutSpec = null,
                                        placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                    ),
                                shape = segmentedItemShape(
                                    index,
                                    if (state.hasMore) state.tracks.size + 1 else state.tracks.size,
                                ),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                TrackRow(
                                    track = track,
                                    isPlaying = isCurrentTrack && playerState.isPlaying,
                                    onClick = {
                                        playerViewModel.playAlbum(state.tracks, index)
                                    },
                                )
                            }
                        }
                    }

                    // Loading indicator at bottom for pagination
                    if (state.isLoadingMore) {
                        item(key = "loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    // Bottom spacer
                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete downloads") },
            text = { Text("Delete all downloaded tracks from this artist?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllDownloads()
                        showDeleteDialog = false
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
