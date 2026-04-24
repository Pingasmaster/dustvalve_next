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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.dustvalve.next.android.ui.components.lists.MusicRow
import com.dustvalve.next.android.ui.components.lists.SegmentedListItem
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.ui.theme.AppShapes

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
                    contentPadding = PaddingValues(bottom = 10.dp),
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
                        }
                    }

                    // Optional release date — name + artist live in the
                    // LargeFlexibleTopAppBar above, so we drop the overlay
                    // text and the artist link here. The "Artist" icon button
                    // in the action group below handles navigation to the
                    // artist page.
                    album.releaseDate?.takeIf { it.isNotBlank() }?.let { date ->
                        item(key = "album_release_date") {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp)
                                    .animateItem(),
                            )
                        }
                    }

                    // Action bar — connected M3E button group, offset so its
                    // vertical centre lands on the cover's bottom edge.
                    item(key = "actions") {
                        val allTracksDownloaded = album.tracks.isNotEmpty() &&
                            album.tracks.all { it.id in state.downloadedTrackIds }
                        AlbumActionBar(
                            isFavorite = album.isFavorite,
                            isDownloading = state.isDownloading,
                            allTracksDownloaded = allTracksDownloaded,
                            artistEnabled = album.artistUrl.isNotBlank(),
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
                            onOpenArtist = { onArtistClick(album.artistUrl) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .offset(y = (-28).dp)
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
                                text = com.dustvalve.next.android.ui.util.tracksHeaderLabel(
                                    trackCount = album.tracks.size,
                                    totalDurationSec = album.tracks.sumOf { it.duration.toDouble() }.toLong(),
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
                            count = album.tracks.size,
                            key = { album.tracks[it].id },
                        ) { index ->
                            val track = album.tracks[index]
                            val isCurrentTrack = playerState.currentTrack?.id == track.id
                            val isTrackDownloaded = track.id in state.downloadedTrackIds

                            SegmentedListItem(
                                index = index,
                                count = album.tracks.size,
                                modifier = Modifier.animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = null,
                                    placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                ),
                            ) {
                                MusicRow(
                                    track = track,
                                    onClick = {
                                        playerViewModel.playAlbum(album.tracks, index)
                                    },
                                    isPlaying = isCurrentTrack && playerState.isPlaying,
                                    isCurrentTrack = isCurrentTrack,
                                    showFavorite = true,
                                    onFavoriteClick = {
                                        viewModel.toggleTrackFavorite(track.id)
                                    },
                                    showDownload = true,
                                    onDownloadClick = {
                                        if (isTrackDownloaded) {
                                            trackToDelete = track
                                        } else {
                                            viewModel.downloadTrack(track)
                                        }
                                    },
                                    isDownloading = track.id in state.downloadingTrackIds,
                                    isDownloaded = isTrackDownloaded,
                                    priceSuffix = album.singleTrackPrice?.let { formatPrice(it) },
                                )
                            }
                        }
                    }

                    // Bandcamp-only "Buy" split CTA — opens the album page in
                    // the default browser. Trailing chevron exposes "Send as
                    // a gift" and, when the artist offers a buy-full-
                    // discography bundle, an "Buy full discography (price)"
                    // option that switches the leading button to the bundle
                    // price + URL.
                    if (album.url.contains("bandcamp.com", ignoreCase = true)) {
                        item(key = "buy_on_bandcamp") {
                            val uriHandler = LocalUriHandler.current
                            BuyOnBandcampSplitButton(
                                albumPrice = album.price,
                                singleTrackPrice = album.singleTrackPrice,
                                albumUrl = album.url,
                                discographyOffer = album.discographyOffer,
                                onOpen = { uriHandler.openUri(it) },
                                modifier = Modifier.animateItem(),
                            )
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
 * [Play all (weighted, primary filled)] · [Shuffle] · [Favorite (toggle)]
 *  · [Artist] · [Download].
 * Items are spaced by 8dp (wider than the default 2dp) so each button's
 * connected shape morphs are clearly distinct visually; first + last carry
 * the connected leading/trailing shapes, middles share the middle shape.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlbumActionBar(
    isFavorite: Boolean,
    isDownloading: Boolean,
    allTracksDownloaded: Boolean,
    artistEnabled: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onOpenArtist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Order: Play all | Shuffle | Artist | Favorite (toggle) | Download (toggle).
    Row(
        modifier = modifier.heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(ActionBarSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onPlayAll,
            shape = ButtonGroupDefaults.connectedLeadingButtonShapes().shape,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp),
        ) {
            // 24 dp icon (default) — the legacy `ButtonDefaults.IconSize` (18 dp)
            // sized this smaller than every other action-bar icon, breaking
            // visual parity with the icon-only siblings.
            Icon(
                painter = painterResource(R.drawable.ic_play_arrow),
                contentDescription = stringResource(R.string.common_play_all),
            )
        }

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

        // Artist navigation — between Shuffle and Favorite, icon-only.
        FilledTonalButton(
            onClick = onOpenArtist,
            enabled = artistEnabled,
            shape = ButtonGroupDefaults.connectedMiddleButtonShapes().shape,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.heightIn(min = 56.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_person),
                contentDescription = stringResource(R.string.detail_cd_open_artist),
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

        // Download is a toggle (not a one-shot button) so it picks up the
        // M3E shape morph + tonal-on-checked container styling — matches the
        // Favorite button's interaction language. While the download is in
        // flight, swap the icon for a circular wavy progress indicator.
        ToggleButton(
            checked = allTracksDownloaded,
            onCheckedChange = { onDownload() },
            enabled = !isDownloading,
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
 * Inter-button spacing for both album + artist action bars. Wider than the
 * 2 dp ButtonGroupDefaults.ConnectedSpaceBetween so the connected-shape
 * morphs read clearly on press; still narrow enough for the row to be
 * understood as one logical group.
 */
private val ActionBarSpacing = 8.dp

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

/**
 * M3E SplitButtonLayout for Bandcamp's "Buy" CTA.
 *
 * Leading button shows the active offer's formatted price + shopping-bag
 * icon; tapping opens that offer's URL in the user's browser. Trailing
 * chevron opens a DropdownMenu with:
 *   - "Send as a gift" — opens the same URL (Bandcamp's gift flow lives
 *     inside the page itself).
 *   - "Buy full discography (price)" — present only when the album's
 *     [discographyOffer] is non-null. Selecting it switches the leading
 *     button's price + URL to the bundle.
 *   - "Buy this album (price)" — only visible when discography is
 *     currently selected, lets the user revert to the per-album offer.
 *
 * The DropdownMenu lives inside the trailing-button slot so the popup
 * anchors to the chevron, not the wrapping centred Box.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3ComponentOverrideApi::class)
@Composable
private fun BuyOnBandcampSplitButton(
    albumPrice: com.dustvalve.next.android.domain.model.AlbumPrice?,
    singleTrackPrice: com.dustvalve.next.android.domain.model.AlbumPrice?,
    albumUrl: String,
    discographyOffer: com.dustvalve.next.android.domain.model.DiscographyOffer?,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    /** Which offer the leading button currently shows. */
    var activeMode by rememberSaveable(albumUrl) { mutableStateOf("album") }
    if (activeMode == "discography" && discographyOffer == null) activeMode = "album"
    if (activeMode == "track" && singleTrackPrice == null) activeMode = "album"

    val activePrice = when (activeMode) {
        "discography" -> discographyOffer?.price
        "track" -> singleTrackPrice
        else -> albumPrice
    }
    val activeUrl = when (activeMode) {
        "discography" -> discographyOffer?.url ?: albumUrl
        else -> albumUrl  // "track" stays on the album page; bandcamp's UI lets the user pick a single track there.
    }
    val activeLabel = activePrice?.let { formatPrice(it) }
        ?: stringResource(R.string.detail_buy_on_bandcamp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.SplitButtonLayout(
            leadingButton = {
                androidx.compose.material3.SplitButtonDefaults.LeadingButton(
                    onClick = { onOpen(activeUrl) },
                    modifier = Modifier.heightIn(min = 80.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 18.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shopping_bag),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(text = activeLabel, style = MaterialTheme.typography.titleLarge)
                }
            },
            trailingButton = {
                Box {
                    androidx.compose.material3.SplitButtonDefaults.TrailingButton(
                        checked = menuOpen,
                        onCheckedChange = { menuOpen = it },
                        modifier = Modifier.heightIn(min = 80.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                    ) {
                        val rotation by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (menuOpen) 180f else 0f,
                            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                            label = "buy_chevron",
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_expand_more),
                            contentDescription = stringResource(R.string.detail_buy_more_options),
                            modifier = Modifier
                                .size(28.dp)
                                .rotate(rotation),
                        )
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(stringResource(R.string.detail_send_as_gift)) },
                            onClick = {
                                menuOpen = false
                                onOpen(activeUrl)
                            },
                        )
                        // Switch-to options — only show the modes we're not
                        // already on and that are actually available.
                        if (activeMode != "album" && albumPrice != null) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Text("${stringResource(R.string.detail_buy_this_album)} (${formatPrice(albumPrice)})")
                                },
                                onClick = { menuOpen = false; activeMode = "album" },
                            )
                        }
                        if (activeMode != "track" && singleTrackPrice != null) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Text("${stringResource(R.string.detail_buy_single_track)} (${formatPrice(singleTrackPrice)})")
                                },
                                onClick = { menuOpen = false; activeMode = "track" },
                            )
                        }
                        if (activeMode != "discography" && discographyOffer != null) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            R.string.detail_buy_full_discography_priced,
                                            formatPrice(discographyOffer.price),
                                        )
                                    )
                                },
                                onClick = { menuOpen = false; activeMode = "discography" },
                            )
                        }
                    }
                }
            },
        )
    }
}

/**
 * Formats an [com.dustvalve.next.android.domain.model.AlbumPrice] using the
 * platform's `NumberFormat.getCurrencyInstance(Locale.ENGLISH)` with the
 * supplied ISO currency code, yielding "$8.00", "£9.99", "CA$11.11", etc.
 *
 * Falls back to "<symbol> <amount>" if the currency code isn't recognized
 * by the JVM's `java.util.Currency` (defensive — bandcamp ships ISO codes).
 */
private fun formatPrice(price: com.dustvalve.next.android.domain.model.AlbumPrice): String {
    return try {
        val nf = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.ENGLISH)
        nf.currency = java.util.Currency.getInstance(price.currency)
        nf.format(price.amount)
    } catch (_: Throwable) {
        "${price.currency} ${"%.2f".format(java.util.Locale.ENGLISH, price.amount)}"
    }
}
