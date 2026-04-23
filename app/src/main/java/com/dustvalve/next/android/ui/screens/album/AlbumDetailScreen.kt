package com.dustvalve.next.android.ui.screens.album

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.dustvalve.next.android.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dustvalve.next.android.ui.components.TrackRow
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.ui.theme.segmentedItemShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumDetailScreen(
    albumUrl: String,
    onArtistClick: (String) -> Unit,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteAlbumDialog by rememberSaveable { mutableStateOf(false) }
    var trackToDelete by remember { mutableStateOf<com.dustvalve.next.android.domain.model.Track?>(null) }

    LaunchedEffect(albumUrl) {
        viewModel.loadAlbum(albumUrl)
    }

    val snackbarText = state.snackbarMessage?.asString()
    LaunchedEffect(snackbarText) {
        snackbarText?.let { message ->
            try {
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = if (state.isSnackbarError) "Retry" else null,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.retryAction?.invoke()
                }
            } finally {
                viewModel.clearSnackbar()
            }
        }
    }

    if (showDeleteAlbumDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAlbumDialog = false },
            title = { Text(stringResource(R.string.detail_delete_downloads_title)) },
            text = { Text(stringResource(R.string.detail_delete_album_downloads_text, state.album?.title ?: "")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAlbumDownloads()
                        showDeleteAlbumDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.common_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAlbumDialog = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            },
        )
    }

    trackToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            title = { Text(stringResource(R.string.detail_delete_download_title)) },
            text = { Text(stringResource(R.string.detail_delete_track_download_text, track.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTrackDownload(track)
                        trackToDelete = null
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.common_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { trackToDelete = null }, shapes = ButtonDefaults.shapes()) {
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
                        text = state.album?.title ?: "",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = state.album?.artist?.takeIf { it.isNotBlank() }?.let { artist ->
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            state.error != null -> {
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
                        Button(onClick = { viewModel.loadAlbum(albumUrl) }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.common_action_retry))
                        }
                    }
                }
            }
            state.album != null -> {
                val album = state.album!!

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    // Hero album art with gradient overlay
                    item(key = "album_art") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .animateItem(),
                        ) {
                            AsyncImage(
                                model = album.artUrl,
                                contentDescription = album.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            // Top scrim so status bar icons remain readable
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
                            // Bottom gradient into surface
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
                                    text = album.title,
                                    style = MaterialTheme.typography.headlineLargeEmphasized,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // Artist + release date
                    item(key = "album_meta") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .animateItem(),
                        ) {
                            Text(
                                text = album.artist,
                                style = MaterialTheme.typography.titleLarge,
                                color = if (album.artistUrl.isNotBlank()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .clickable(enabled = album.artistUrl.isNotBlank()) { onArtistClick(album.artistUrl) }
                                    .padding(vertical = 4.dp),
                            )
                            album.releaseDate?.let { date ->
                                Text(
                                    text = date,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Action bar — connected M3E button group
                    item(key = "actions") {
                        val allTracksDownloaded = album.tracks.isNotEmpty() &&
                            album.tracks.all { it.id in state.downloadedTrackIds }
                        AlbumActionBar(
                            isFavorite = album.isFavorite,
                            isDownloading = state.isDownloading,
                            allTracksDownloaded = allTracksDownloaded,
                            onPlayAll = {
                                if (album.tracks.isNotEmpty()) {
                                    playerViewModel.playAlbum(album.tracks, 0)
                                }
                            },
                            onShuffle = {
                                if (album.tracks.isNotEmpty()) {
                                    playerViewModel.playAlbum(album.tracks.shuffled(), 0)
                                }
                            },
                            onToggleFavorite = { viewModel.toggleFavorite() },
                            onDownload = {
                                if (allTracksDownloaded) {
                                    showDeleteAlbumDialog = true
                                } else {
                                    viewModel.downloadAlbum()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .animateItem(),
                        )
                    }

                    // About section (expandable)
                    album.about?.let { about ->
                        if (about.isNotBlank()) {
                            item(key = "about") {
                                ExpandableAbout(about = about)
                            }
                        }
                    }

                    // Track list header + segmented items
                    if (album.tracks.isNotEmpty()) {
                        item(key = "tracks_header") {
                            Text(
                                text = stringResource(R.string.detail_tracks_label),
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
                            count = album.tracks.size,
                            key = { album.tracks[it].id },
                        ) { index ->
                            val track = album.tracks[index]
                            val isCurrentTrack = playerState.currentTrack?.id == track.id
                            val isTrackDownloaded = track.id in state.downloadedTrackIds

                            Surface(
                                modifier = Modifier
                                    .padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = if (index == 0) 8.dp else 1.dp,
                                        bottom = if (index == album.tracks.lastIndex) 0.dp else 1.dp,
                                    )
                                    .animateItem(
                                        fadeInSpec = null,
                                        fadeOutSpec = null,
                                        placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                    ),
                                shape = segmentedItemShape(index, album.tracks.size),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                TrackRow(
                                    track = track,
                                    isPlaying = isCurrentTrack && playerState.isPlaying,
                                    onClick = {
                                        playerViewModel.playAlbum(album.tracks, index)
                                    },
                                    onFavoriteClick = {
                                        viewModel.toggleTrackFavorite(track.id)
                                    },
                                    onDownloadClick = {
                                        if (isTrackDownloaded) {
                                            trackToDelete = track
                                        } else {
                                            viewModel.downloadTrack(track)
                                        }
                                    },
                                    isDownloading = track.id in state.downloadingTrackIds,
                                    isDownloaded = isTrackDownloaded,
                                )
                            }
                        }
                    }

                    // Bandcamp-only "Buy" CTA — opens the album's page in the default
                    // browser (no in-app webview), per the user's spec.
                    if (album.url.contains("bandcamp.com", ignoreCase = true)) {
                        item(key = "buy_on_bandcamp") {
                            val uriHandler = LocalUriHandler.current
                            FilledTonalButton(
                                onClick = { uriHandler.openUri(album.url) },
                                shapes = ButtonDefaults.shapes(),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                                    .heightIn(min = 64.dp)
                                    .animateItem(),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_open_in_new),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.detail_buy_on_bandcamp),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }

                    // Tags in contained surface
                    if (album.tags.isNotEmpty()) {
                        item(key = "tags") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .animateItem(),
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.detail_tags),
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        modifier = Modifier.padding(bottom = 12.dp),
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        album.tags.forEach { tag ->
                                            Surface(
                                                shape = AppShapes.Tag,
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                            ) {
                                                Text(
                                                    text = tag,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom spacer for mini player clearance
                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpandableAbout(about: String) {
    ExpandableDescription(text = about, collapsedMaxLines = 4)
}

/**
 * Connected M3E button-group action bar for album detail. Layout:
 * [Play all (weighted, primary filled)] · [Shuffle] · [Favorite (toggle)] · [Download].
 * Spacing follows ButtonGroupDefaults.ConnectedSpaceBetween (2dp); the first +
 * last button get connected leading/trailing shapes, the two middles get the
 * connected middle shape so the row reads as one piece on screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlbumActionBar(
    isFavorite: Boolean,
    isDownloading: Boolean,
    allTracksDownloaded: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Leading: weighted Play all (primary filled).
        Button(
            onClick = onPlayAll,
            shape = ButtonGroupDefaults.connectedLeadingButtonShapes().shape,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_play_arrow),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.common_play_all))
        }

        // Middle 1: Shuffle (one-shot, tonal).
        FilledTonalButton(
            onClick = onShuffle,
            shape = ButtonGroupDefaults.connectedMiddleButtonShapes().shape,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.heightIn(min = 56.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shuffle),
                contentDescription = stringResource(R.string.common_cd_shuffle_play),
            )
        }

        // Middle 2: Favorite toggle. ToggleButton's checked-shape morph + the
        // connected middle shapes give the M3E "expand on press / pill on
        // checked" feel automatically.
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

        // Trailing: Download / delete-when-fully-downloaded. Tonal so the row
        // hierarchy reads "primary action · supporting · supporting · supporting".
        FilledTonalButton(
            onClick = onDownload,
            enabled = !isDownloading,
            shape = ButtonGroupDefaults.connectedTrailingButtonShapes().shape,
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
                        stringResource(R.string.detail_cd_delete_album_downloads)
                    } else {
                        stringResource(R.string.detail_cd_download_album)
                    },
                )
            }
        }
    }
}

/**
 * M3E-idiomatic "expand to show more text" pattern (no first-party component
 * exists). Multi-line `Text` with overflow detection driving a chevron+label
 * row; rotation animates with `MotionScheme.fastEffectsSpec`, height reflow
 * animates with `MotionScheme.defaultSpatialSpec`. Trigger is hidden when
 * the text fits without ellipsis.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpandableDescription(
    text: String,
    collapsedMaxLines: Int,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var hasOverflow by remember(text) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "show_more_chevron",
    )

    Column(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .animateContentSize(
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded) hasOverflow = result.hasVisualOverflow
            },
        )
        if (hasOverflow || expanded) {
            val expandLabel = stringResource(R.string.detail_show_more)
            val collapseLabel = stringResource(R.string.detail_show_less)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(
                        onClickLabel = if (expanded) collapseLabel else expandLabel,
                    ) { expanded = !expanded }
                    .semantics(mergeDescendants = true) {
                        if (expanded) collapse { expanded = false; true }
                        else expand { expanded = true; true }
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (expanded) collapseLabel else expandLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation),
                )
            }
        }
    }
}
