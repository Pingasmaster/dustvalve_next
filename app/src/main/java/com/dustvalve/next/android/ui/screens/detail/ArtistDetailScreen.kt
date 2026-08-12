package com.dustvalve.next.android.ui.screens.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SplitButton
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dustvalve.next.android.R
import com.dustvalve.next.android.ui.adaptive.AdaptiveLayoutInfo
import com.dustvalve.next.android.ui.adaptive.adaptiveHeroSize
import com.dustvalve.next.android.ui.components.AlbumCard
import com.dustvalve.next.android.ui.components.detail.ExpandableText
import com.dustvalve.next.android.ui.components.heartMorphClip
import com.dustvalve.next.android.ui.components.lists.MusicRow
import com.dustvalve.next.android.ui.components.lists.SegmentedListItem
import com.dustvalve.next.android.ui.components.rememberHeartMorphState
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.ui.screens.player.clearSnackbar
import com.dustvalve.next.android.ui.screens.player.playAlbum
import com.dustvalve.next.android.ui.util.toggle
import kotlinx.coroutines.launch

/**
 * Source-agnostic artist detail screen.
 *
 * Renders two shapes based on what the source returns:
 *  - Bandcamp-style: `artist.albums` populated -> album grid with "Buy full
 *    discography" split-button when the artist has that offer.
 *  - YouTube-style: `albums` empty, `tracks` populated -> flat segmented track
 *    list with "Load more" pagination. No buy button.
 *
 * Replaces `ui/screens/artist/ArtistDetailScreen.kt` (Bandcamp) and
 * `ui/screens/youtube/YouTubeArtistDetailScreen.kt`.
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistDetailScreen(
    adaptiveInfo: AdaptiveLayoutInfo,
    args: ArtistDetailArgs,
    onAlbumClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    viewModel: ArtistDetailViewModel = hiltViewModel(),
) {
    val sourceId = args.sourceId
    val artistUrl = args.artistUrl
    val artistNameHint = args.artistNameHint
    val artistImageHint = args.artistImageHint
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(sourceId, artistUrl) {
        viewModel.load(
            sourceId = sourceId,
            url = artistUrl,
            name = artistNameHint,
            imageUrl = artistImageHint,
        )
    }

    val snackbarText = state.snackbarMessage?.asString()
    val retryLabel = stringResource(R.string.common_action_retry)
    LaunchedEffect(snackbarText) {
        snackbarText?.let { message ->
            try {
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = if (state.isSnackbarError) retryLabel else null,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.retryAction?.invoke()
                }
            } finally {
                viewModel.clearSnackbar()
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_downloads_title)) },
            text = { Text(stringResource(R.string.detail_delete_artist_downloads_text, state.artist?.name.orEmpty())) },
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
                        text = (state.artist?.name ?: artistNameHint).orEmpty(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = state.artist?.location?.takeIf { it.isNotBlank() }?.let { location ->
                    { Text(text = location, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        // Local val so `artist != null` smart-casts inside the matching branch
        // (Kotlin can't smart-cast through a property in a `when` clause).
        val artist = state.artist
        // Show the error+retry branch even when a seed artist (name/image
        // hint) exists but the real fetch failed - otherwise a failed load
        // with a hint rendered a misleading empty "No releases" page.
        val fetchFailedWithNothingToShow = state.error != null && state.tracks.isEmpty() &&
            (artist == null || artist.albums.isEmpty())
        when {
            state.isLoading && artist == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) { ContainedLoadingIndicator() }
            }

            fetchFailedWithNothingToShow -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error?.asString() ?: stringResource(R.string.detail_error_load_artist),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.load(sourceId, artistUrl, artistNameHint, artistImageHint) },
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(stringResource(R.string.common_action_retry)) }
                    }
                }
            }

            artist != null -> {
                // YouTube Topic / YTM fallback may populate both Top songs and
                // album tiles. Prefer the flat track feed when songs exist so
                // Play All works; album chips still render above the list.
                val renderFlatTracks = (sourceId == "youtube" && state.tracks.isNotEmpty()) ||
                    (
                        artist.albums.isEmpty() &&
                            (state.tracks.isNotEmpty() || state.hasMore || sourceId != "bandcamp")
                        )
                if (renderFlatTracks) {
                    FlatTracksLayout(
                        adaptiveInfo = adaptiveInfo,
                        state = state,
                        innerPadding = innerPadding,
                        actions = ArtistTracksActions(
                            playback = DetailPlaybackActions(
                                onPlayAll = {
                                    viewModel.playExpanded(0) { tracks, index ->
                                        playerViewModel.playAlbum(tracks, index)
                                    }
                                },
                                onShuffle = {
                                    viewModel.playMixShuffled { tracks, index ->
                                        playerViewModel.playAlbum(tracks, index)
                                    }
                                },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onDownload = {
                                    val allDownloaded = state.tracks.isNotEmpty() &&
                                        !state.hasMore &&
                                        state.tracks.all { it.id in state.downloadedTrackIds }
                                    if (allDownloaded) {
                                        showDeleteDialog = true
                                    } else {
                                        viewModel.downloadAll()
                                    }
                                },
                            ),
                            onLoadMore = viewModel::loadMore,
                            onTrackClick = { idx ->
                                viewModel.playExpanded(idx) { tracks, index ->
                                    playerViewModel.playAlbum(tracks, index)
                                }
                            },
                            onAlbumClick = onAlbumClick,
                        ),
                    )
                } else {
                    AlbumGridLayout(
                        adaptiveInfo = adaptiveInfo,
                        state = state,
                        innerPadding = innerPadding,
                        onAlbumClick = onAlbumClick,
                        onPlayMix = {
                            viewModel.loadMixTracks { tracks ->
                                playerViewModel.playAlbum(tracks, 0)
                            }
                        },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onDownload = {
                            val allDownloaded = artist.albums.isNotEmpty() &&
                                artist.albums.all { it.id in state.downloadedAlbumIds }
                            if (allDownloaded) {
                                showDeleteDialog = true
                            } else {
                                viewModel.downloadAll()
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlbumGridLayout(
    adaptiveInfo: AdaptiveLayoutInfo,
    state: ArtistDetailUiState,
    innerPadding: PaddingValues,
    onAlbumClick: (String) -> Unit,
    onPlayMix: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
) {
    val artist = state.artist ?: return
    val allAlbumsDownloaded = artist.albums.isNotEmpty() &&
        artist.albums.all { it.id in state.downloadedAlbumIds }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = adaptiveInfo.gridMinSize),
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(bottom = 10.dp),
    ) {
        item(key = "artist_hero", span = { GridItemSpan(maxLineSpan) }) {
            ArtistHero(
                heroMaxSize = adaptiveInfo.heroMaxSize,
                imageUrl = artist.imageUrl,
                name = artist.name,
                isFavorite = artist.isFavorite,
                onDoubleTap = onToggleFavorite,
            )
        }
        item(key = "actions", span = { GridItemSpan(maxLineSpan) }) {
            ActionBar(
                primary = DetailActionBarPrimary(
                    label = stringResource(R.string.common_play_mix),
                    iconRes = R.drawable.ic_shuffle,
                    enabled = !state.isLoadingMix && artist.albums.isNotEmpty(),
                    loading = state.isLoadingMix,
                    onClick = onPlayMix,
                ),
                state = DetailActionBarState(
                    isFavorite = state.isFavorite,
                    isDownloading = state.isDownloading,
                    allDownloaded = allAlbumsDownloaded,
                    downloadEnabled = !state.isDownloading && artist.albums.isNotEmpty(),
                ),
                extras = DetailActionBarExtras(
                    onToggleFavorite = onToggleFavorite,
                    onDownload = onDownload,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-28).dp),
            )
        }
        if (!artist.bio.isNullOrBlank()) {
            item(key = "artist_bio", span = { GridItemSpan(maxLineSpan) }) {
                ExpandableText(text = artist.bio.orEmpty(), collapsedMaxLines = 4)
            }
        }
        if (artist.hasDiscographyOffer) {
            item(key = "buy_discography", span = { GridItemSpan(maxLineSpan) }) {
                val uriHandler = LocalUriHandler.current
                BuyDiscographySplitButton(
                    artistUrl = artist.url,
                    onOpen = { uriHandler.openUri(it) },
                )
            }
        }
        if (artist.albums.isNotEmpty()) {
            item(key = "discography_header", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.detail_discography),
                    // Matches the tracks header on album/collection detail.
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(items = artist.albums, key = { "album_${it.id}" }, contentType = { "album" }) { album ->
                Surface(
                    modifier = Modifier.padding(6.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    AlbumCard(
                        album = album,
                        onClick = { onAlbumClick(album.url) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            item(key = "no_albums", span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(message = stringResource(R.string.detail_no_releases))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FlatTracksLayout(
    adaptiveInfo: AdaptiveLayoutInfo,
    state: ArtistDetailUiState,
    innerPadding: PaddingValues,
    actions: ArtistTracksActions,
) {
    val onPlayAll = actions.playback.onPlayAll
    val onShuffle = actions.playback.onShuffle
    val onToggleFavorite = actions.playback.onToggleFavorite
    val onDownload = actions.playback.onDownload
    val onLoadMore = actions.onLoadMore
    val onTrackClick = actions.onTrackClick
    val onAlbumClick = actions.onAlbumClick
    val artist = state.artist ?: return
    val listState = rememberLazyListState()
    val allDownloaded = state.tracks.isNotEmpty() &&
        state.tracks.all { it.id in state.downloadedTrackIds }
    val loadMore by rememberUpdatedState(onLoadMore)

    // Trigger pagination when we're near the bottom.
    LaunchedEffect(listState, state.tracks.size, state.hasMore) {
        androidx.compose.runtime.snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 3
        }.collect { nearEnd ->
            if (nearEnd && state.hasMore && !state.isLoadingMore) {
                loadMore()
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(bottom = 10.dp),
    ) {
        item(key = "artist_hero") {
            ArtistHero(
                heroMaxSize = adaptiveInfo.heroMaxSize,
                imageUrl = artist.imageUrl,
                name = artist.name,
                isFavorite = artist.isFavorite,
                onDoubleTap = onToggleFavorite,
            )
        }
        item(key = "actions") {
            ActionBar(
                primary = DetailActionBarPrimary(
                    label = stringResource(R.string.common_play_all),
                    iconRes = R.drawable.ic_play_arrow,
                    enabled = state.tracks.isNotEmpty(),
                    loading = false,
                    onClick = onPlayAll,
                ),
                state = DetailActionBarState(
                    isFavorite = state.isFavorite,
                    isDownloading = state.isDownloading,
                    allDownloaded = allDownloaded,
                    downloadEnabled = !state.isDownloading && state.tracks.isNotEmpty(),
                ),
                extras = DetailActionBarExtras(
                    onToggleFavorite = onToggleFavorite,
                    onDownload = onDownload,
                    onShuffle = onShuffle,
                    shuffleEnabled = !state.isLoadingMix && state.tracks.isNotEmpty(),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-28).dp),
            )
        }
        if (!artist.bio.isNullOrBlank()) {
            item(key = "artist_bio") {
                ExpandableText(text = artist.bio.orEmpty(), collapsedMaxLines = 4)
            }
        }
        youtubeDiscographySection(artist = artist, onAlbumClick = onAlbumClick)
        artistTracksSection(
            state = state,
            onTrackClick = onTrackClick,
        )
    }
}

private fun LazyListScope.youtubeDiscographySection(
    artist: com.dustvalve.next.android.domain.model.Artist,
    onAlbumClick: (String) -> Unit,
) {
    if (artist.albums.isEmpty()) return
    item(key = "discography_header") {
        Text(
            text = stringResource(R.string.detail_discography),
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        )
    }
    item(key = "discography_row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = artist.albums, key = { "album_${it.id}" }) { album ->
                Surface(
                    modifier = Modifier.width(140.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    AlbumCard(
                        album = album,
                        onClick = { onAlbumClick(album.url) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    item(key = "tracks_header") {
        Text(
            text = stringResource(R.string.detail_tracks_label),
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.artistTracksSection(state: ArtistDetailUiState, onTrackClick: (Int) -> Unit) {
    if (state.tracks.isEmpty() && !state.hasMore && !state.isLoading) {
        item(key = "empty") { EmptyState(message = stringResource(R.string.detail_no_releases)) }
        return
    }
    val count = state.tracks.size + if (state.hasMore) 1 else 0
    items(
        count = state.tracks.size,
        key = { i -> state.tracks[i].id },
        contentType = { "artist_track" },
    ) { index ->
        val track = state.tracks[index]
        SegmentedListItem(
            index = index,
            count = count,
            modifier = Modifier.animateItem(
                fadeInSpec = null,
                fadeOutSpec = null,
                placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            ),
        ) {
            MusicRow(
                track = track,
                onClick = { onTrackClick(index) },
            )
        }
    }
    if (state.hasMore || state.isLoadingMore) {
        item(key = "loading_more") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) { ContainedLoadingIndicator() }
        }
    }
}

@Composable
private fun ArtistHero(heroMaxSize: Dp, imageUrl: String?, name: String, isFavorite: Boolean, onDoubleTap: (() -> Unit)? = null) {
    val hapticFeedback = LocalHapticFeedback.current
    val heartMorph = rememberHeartMorphState()
    val heartScope = rememberCoroutineScope()
    // Double-tap the hero to toggle the artist favorite; single tap stays a
    // no-op, so the gesture detector adds no latency to anything else. The
    // artwork morphs into a heart - same animation as the player's album art.
    val doubleTapModifier = if (onDoubleTap != null) {
        Modifier.pointerInput(imageUrl) {
            detectTapGestures(
                onDoubleTap = {
                    hapticFeedback.toggle(!isFavorite)
                    onDoubleTap()
                    heartScope.launch { heartMorph.play() }
                },
            )
        }
    } else {
        Modifier
    }
    // Clips only while the morph is animating (resting hero stays full-bleed);
    // progress is read in the layer block, so animation frames don't recompose
    // this scope.
    val artModifier = Modifier
        .fillMaxSize()
        .heartMorphClip(heartMorph)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(doubleTapModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .adaptiveHeroSize(heroMaxSize)
                .aspectRatio(1f),
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = name,
                    modifier = artModifier,
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = artModifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    com.dustvalve.next.android.ui.components.EmptyState(
        icon = R.drawable.ic_album,
        title = message,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionBar(
    primary: DetailActionBarPrimary,
    state: DetailActionBarState,
    extras: DetailActionBarExtras,
    modifier: Modifier = Modifier,
) {
    val playPrimaryLabel = primary.label
    val playPrimaryIconRes = primary.iconRes
    val playPrimaryEnabled = primary.enabled
    val playPrimaryLoading = primary.loading
    val onPlayPrimary = primary.onClick
    val isFavorite = state.isFavorite
    val isDownloading = state.isDownloading
    val allDownloaded = state.allDownloaded
    val downloadEnabled = state.downloadEnabled
    val onToggleFavorite = extras.onToggleFavorite
    val onDownload = extras.onDownload
    val onShuffle = extras.onShuffle
    val shuffleEnabled = extras.shuffleEnabled
    Row(
        modifier = modifier.heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(ActionBarSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onPlayPrimary,
            enabled = playPrimaryEnabled,
            shape = ButtonGroupDefaults.connectedLeadingButtonShapes().shape,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        ) {
            if (playPrimaryLoading) {
                CircularWavyProgressIndicator(modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.common_loading))
            } else {
                // 24 dp icon (default) to match the action-bar icons on the
                // album/collection/playlist sibling screens.
                Icon(
                    painter = painterResource(playPrimaryIconRes),
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(playPrimaryLabel)
            }
        }

        if (onShuffle != null) {
            FilledTonalButton(
                onClick = onShuffle,
                enabled = shuffleEnabled,
                shape = ButtonGroupDefaults.connectedMiddleButtonShapes().shape,
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.heightIn(min = 56.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shuffle),
                    contentDescription = stringResource(R.string.common_cd_shuffle_play),
                )
            }
        }

        FilledTonalToggleButton(
            checked = isFavorite,
            onCheckedChange = { onToggleFavorite() },
            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.heightIn(min = 56.dp),
        ) {
            Icon(
                painter = painterResource(
                    if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                ),
                contentDescription = if (isFavorite) {
                    stringResource(R.string.detail_cd_remove_favorites)
                } else {
                    stringResource(R.string.detail_cd_add_favorites)
                },
            )
        }

        FilledTonalButton(
            onClick = onDownload,
            enabled = downloadEnabled,
            shape = ButtonGroupDefaults.connectedTrailingButtonShapes().shape,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.heightIn(min = 56.dp),
        ) {
            if (isDownloading) {
                CircularWavyProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    painter = painterResource(
                        if (allDownloaded) R.drawable.ic_download_done else R.drawable.ic_download,
                    ),
                    contentDescription = if (allDownloaded) {
                        stringResource(R.string.detail_cd_delete_all_downloads)
                    } else {
                        stringResource(R.string.detail_cd_download_all)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BuyDiscographySplitButton(artistUrl: String, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    // Sizing mirrors the album-page Buy split-button (min 80.dp height,
    // 32x18 content padding, 28 dp icon, titleLarge) so the artist CTA
    // matches in visual weight.
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        SplitButton(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = { onOpen(artistUrl) },
                    modifier = Modifier.heightIn(min = 80.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 18.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shopping_bag),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = stringResource(R.string.detail_buy_full_discography),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            },
            trailingButton = {
                Box {
                    SplitButtonDefaults.TrailingButton(
                        checked = menuOpen,
                        onCheckedChange = { menuOpen = it },
                        modifier = Modifier.heightIn(min = 80.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                    ) {
                        val rotation by animateFloatAsState(
                            targetValue = if (menuOpen) 180f else 0f,
                            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                            label = "buy_discog_chevron",
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_expand_more),
                            contentDescription = stringResource(R.string.detail_buy_more_options),
                            modifier = Modifier.size(28.dp).rotate(rotation),
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.detail_send_as_gift)) },
                            onClick = {
                                menuOpen = false
                                onOpen(artistUrl)
                            },
                        )
                    }
                }
            },
        )
    }
}

private val ActionBarSpacing = 8.dp
