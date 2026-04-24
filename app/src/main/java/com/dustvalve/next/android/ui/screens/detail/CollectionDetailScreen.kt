package com.dustvalve.next.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
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
import com.dustvalve.next.android.ui.components.lists.MusicRow
import com.dustvalve.next.android.ui.components.lists.SegmentedListItem
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel

/**
 * Source-agnostic playlist / collection detail screen. Loads via
 * [com.dustvalve.next.android.domain.repository.MusicSource.getCollection].
 *
 * Visually mirrors [com.dustvalve.next.android.ui.screens.album.AlbumDetailScreen]:
 * name lives in the top-bar (no hero overlay), actions are a connected M3E
 * ButtonGroup offset -28dp to sit on the cover's bottom edge. Differences
 * versus album detail are scope-appropriate: no Buy CTA, no tags, no bio, no
 * Artist-nav button (a playlist has no single artist), plus a mix-style
 * infinite-scroll footer that album detail doesn't need.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollectionDetailScreen(
    sourceId: String,
    collectionUrl: String,
    collectionName: String,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: CollectionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sourceId, collectionUrl, collectionName) {
        viewModel.load(sourceId, collectionUrl, collectionName)
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_downloads_title)) },
            text = { Text(stringResource(R.string.detail_delete_playlist_downloads_text, state.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllDownloads()
                        showDeleteDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.common_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            },
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = state.name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = if (state.tracks.isNotEmpty()) {
                    {
                        Text(
                            text = pluralStringResource(
                                R.plurals.track_count,
                                state.tracks.size,
                                state.tracks.size,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else null,
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
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) { ContainedLoadingIndicator() }
            }
            state.error != null && state.tracks.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error ?: stringResource(R.string.detail_error_load_playlist),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.load(sourceId, collectionUrl, collectionName) },
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(stringResource(R.string.common_action_retry)) }
                    }
                }
            }
            else -> {
                val heroUrl = state.coverUrl ?: state.tracks.firstOrNull()?.artUrl
                val listState = rememberLazyListState()

                // Infinite scroll: when within 3 items of the bottom and the
                // collection reports hasMore (e.g. a YouTube Mix), fetch the
                // next page. Mirrors the pattern in ArtistDetailScreen and the
                // queue sheet in FullPlayer.
                LaunchedEffect(listState, state.tracks.size, state.hasMore) {
                    snapshotFlow {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        val total = listState.layoutInfo.totalItemsCount
                        total > 0 && last >= total - 3
                    }.collect { nearEnd ->
                        if (nearEnd && state.hasMore && !state.isLoadingMore) {
                            viewModel.loadMore()
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(bottom = 10.dp),
                ) {
                    // Hero cover. Name + track count live in the top-bar, so
                    // the hero is the bare artwork — matches AlbumDetailScreen.
                    item(key = "hero") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .animateItem(),
                        ) {
                            if (!heroUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = heroUrl,
                                    contentDescription = state.name,
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
                        }
                    }

                    item(key = "actions") {
                        val allDownloaded = state.tracks.isNotEmpty() &&
                            state.tracks.all { it.id in state.downloadedTrackIds }
                        CollectionActionBar(
                            isFavorite = state.isFavorite,
                            isDownloading = state.isDownloading,
                            allTracksDownloaded = allDownloaded,
                            hasTracks = state.tracks.isNotEmpty(),
                            onPlayAll = {
                                if (state.tracks.isNotEmpty()) {
                                    playerViewModel.playAlbum(state.tracks, 0)
                                }
                            },
                            onShuffle = {
                                if (state.tracks.isNotEmpty()) {
                                    playerViewModel.playAlbum(state.tracks.shuffled(), 0)
                                }
                            },
                            onToggleFavorite = { viewModel.toggleFavorite() },
                            onDownload = {
                                if (allDownloaded) showDeleteDialog = true
                                else viewModel.downloadAll()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .offset(y = (-28).dp)
                                .animateItem(),
                        )
                    }

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
                            val isCurrent = playerState.currentTrack?.id == track.id
                            SegmentedListItem(
                                index = index,
                                count = state.tracks.size,
                                modifier = Modifier.animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = null,
                                    placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                ),
                            ) {
                                MusicRow(
                                    track = track,
                                    onClick = { playerViewModel.playAlbum(state.tracks, index) },
                                    isPlaying = isCurrent && playerState.isPlaying,
                                    isCurrentTrack = isCurrent,
                                )
                            }
                        }
                        if (state.hasMore || state.isLoadingMore) {
                            item(key = "loading_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) { ContainedLoadingIndicator() }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Connected M3E button-group action bar for collection detail. Layout:
 * [Play all (weighted, primary filled)] · [Shuffle] · [Favorite (toggle)]
 *  · [Download (toggle)].
 *
 * Mirrors `AlbumActionBar` minus the Artist-nav button — a playlist doesn't
 * have a single artist. Spacing + shape morphing + heights match album
 * detail so the two screens read as one design system.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CollectionActionBar(
    isFavorite: Boolean,
    isDownloading: Boolean,
    allTracksDownloaded: Boolean,
    hasTracks: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(ActionBarSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onPlayAll,
            enabled = hasTracks,
            shape = ButtonGroupDefaults.connectedLeadingButtonShapes().shape,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_play_arrow),
                contentDescription = stringResource(R.string.common_play_all),
            )
        }

        FilledTonalButton(
            onClick = onShuffle,
            enabled = hasTracks,
            shape = ButtonGroupDefaults.connectedMiddleButtonShapes().shape,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.heightIn(min = 56.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shuffle),
                contentDescription = stringResource(R.string.common_cd_shuffle_play),
            )
        }

        ToggleButton(
            checked = isFavorite,
            onCheckedChange = { onToggleFavorite() },
            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
            colors = ToggleButtonDefaults.tonalToggleButtonColors(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.heightIn(min = 56.dp),
        ) {
            Icon(
                painter = painterResource(
                    if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
                ),
                contentDescription = if (isFavorite) {
                    stringResource(R.string.detail_cd_remove_favorites)
                } else {
                    stringResource(R.string.detail_cd_add_favorites)
                },
            )
        }

        ToggleButton(
            checked = allTracksDownloaded,
            onCheckedChange = { onDownload() },
            enabled = !isDownloading && hasTracks,
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            colors = ToggleButtonDefaults.tonalToggleButtonColors(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.heightIn(min = 56.dp),
        ) {
            if (isDownloading) {
                CircularWavyProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    painter = painterResource(
                        if (allTracksDownloaded) R.drawable.ic_download_done
                        else R.drawable.ic_download
                    ),
                    contentDescription = if (allTracksDownloaded) {
                        stringResource(R.string.detail_cd_delete_downloads)
                    } else {
                        stringResource(R.string.detail_cd_download_all)
                    },
                )
            }
        }
    }
}

private val ActionBarSpacing = 8.dp
