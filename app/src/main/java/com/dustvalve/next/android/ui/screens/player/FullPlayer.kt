package com.dustvalve.next.android.ui.screens.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.drawscope.Stroke

import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import android.graphics.Matrix
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.RepeatMode
import androidx.compose.foundation.background
import com.dustvalve.next.android.ui.components.PlaylistEditSheet
import com.dustvalve.next.android.ui.components.PlaylistListItem
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.ui.theme.segmentedItemShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FullPlayer(
    playerViewModel: PlayerViewModel,
    onCollapse: () -> Unit,
    onArtistClick: (String) -> Unit = {},
) {
    val state by playerViewModel.uiState.collectAsStateWithLifecycle()
    val track = state.currentTrack
    val snackbarHostState = remember { SnackbarHostState() }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Snackbar handling
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            try {
                snackbarHostState.showSnackbar(message)
            } finally {
                playerViewModel.clearSnackbar()
            }
        }
    }

    // Double-tap heart animation: morph from Square to Heart
    val heartProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val squarePolygon = remember { MaterialShapes.Square }
    val heartPolygon = remember { MaterialShapes.Heart }
    val heartMorph = remember(squarePolygon, heartPolygon) { Morph(squarePolygon, heartPolygon) }
    val heartInSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val heartOutSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    // Play/pause tap feedback on album art
    var showPlayPauseFeedback by remember { mutableStateOf(false) }
    var feedbackIsPlaying by remember { mutableStateOf(false) }
    val feedbackScale = remember { Animatable(0f) }
    val feedbackSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    // Dialog and sheet state
    var showDeleteDownloadDialog by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistSheet by remember { mutableStateOf(false) }
    var showDebugSheet by remember { mutableStateOf(false) }
    var showVolumeSheet by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    // Full-screen volume control sheet
    if (showVolumeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showVolumeSheet = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Volume",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.height(32.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_volume_up),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                // XL vertical slider
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(64.dp)
                        .graphicsLayer { rotationZ = -90f },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Slider(
                        value = state.volumeLevel,
                        onValueChange = { playerViewModel.setVolume(it) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_volume_off),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TwoRowsTopAppBar(
                    title = { expanded ->
                        Text(
                            text = if (expanded) "Now Playing" else (track?.title ?: "Now Playing"),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    subtitle = { expanded ->
                        if (expanded && track != null) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onCollapse) {
                            Icon(
                                painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                                contentDescription = "Collapse",
                            )
                        }
                    },
                    actions = {
                        if (state.showVolumeButton) {
                            IconButton(onClick = { showVolumeSheet = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_volume_up),
                                    contentDescription = "Volume",
                                )
                            }
                        }
                    },
                    titleHorizontalAlignment = Alignment.CenterHorizontally,
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                )
            },
        ) { paddingValues ->
            if (track == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No track playing",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Album art with single-tap play/pause and double-tap heart
                val albumArtShape = if (heartProgress.value > 0f) {
                    PlaybackMorphShape(heartMorph, heartProgress.value)
                } else {
                    PlaybackMorphShape(heartMorph, 0f)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = track.artUrl,
                        contentDescription = track.albumTitle.ifEmpty { "Album art" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(albumArtShape)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        feedbackIsPlaying = state.isPlaying
                                        playerViewModel.onPlayPause()
                                        scope.launch {
                                            feedbackScale.snapTo(0f)
                                            showPlayPauseFeedback = true
                                            feedbackScale.animateTo(1f, feedbackSpec)
                                            delay(400L)
                                            feedbackScale.animateTo(0f, feedbackSpec)
                                            showPlayPauseFeedback = false
                                        }
                                    },
                                    onDoubleTap = {
                                        playerViewModel.onToggleFavorite()
                                        scope.launch {
                                            heartProgress.animateTo(
                                                targetValue = 1f,
                                                animationSpec = heartInSpec,
                                            )
                                            delay(1000L)
                                            heartProgress.animateTo(
                                                targetValue = 0f,
                                                animationSpec = heartOutSpec,
                                            )
                                        }
                                    },
                                    onLongPress = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showDebugSheet = true
                                    },
                                )
                            },
                    )

                    // Play/pause tap feedback overlay
                    if (showPlayPauseFeedback) {
                        Icon(
                            painter = painterResource(if (!feedbackIsPlaying) R.drawable.ic_play_arrow else R.drawable.ic_pause),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            modifier = Modifier
                                .size(72.dp)
                                .graphicsLayer {
                                    scaleX = feedbackScale.value
                                    scaleY = feedbackScale.value
                                    alpha = feedbackScale.value
                                },
                        )
                    }
                } // end album art Box

                // Inline volume slider (to the right of album art)
                if (state.showInlineVolumeSlider) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.height(240.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_volume_up),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Vertical slider via rotated horizontal slider
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .width(48.dp)
                                .graphicsLayer { rotationZ = -90f },
                        ) {
                            androidx.compose.material3.Slider(
                                value = state.volumeLevel,
                                onValueChange = { playerViewModel.setVolume(it) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_volume_down),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                } // end Row

                // Track title, artist, favorite, and download
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Artist name as TextButton
                        TextButton(
                            onClick = { onArtistClick(track.artistUrl) },
                            enabled = track.artistUrl.isNotEmpty(),
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Text(
                                text = track.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // Favorite toggle button
                        TonalToggleButton(
                            checked = track.isFavorite,
                            onCheckedChange = { playerViewModel.onToggleFavorite() },
                            colors = ToggleButtonDefaults.tonalToggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                checkedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                checkedContentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Icon(
                                painter = painterResource(if (track.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
                                contentDescription = if (track.isFavorite) "Remove from favorites" else "Add to favorites",
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // Download toggle button
                        val isTrackDownloaded = track.id in state.downloadedTrackIds
                        val isDownloading = state.downloadingTrackId == track.id
                        val isLocalTrack = track.isLocal
                        TonalToggleButton(
                            checked = isTrackDownloaded || isLocalTrack,
                            onCheckedChange = {
                                if (isLocalTrack) return@TonalToggleButton
                                if (isTrackDownloaded) {
                                    showDeleteDownloadDialog = true
                                } else if (!isDownloading) {
                                    playerViewModel.onDownloadTrack()
                                }
                            },
                            enabled = !isDownloading && !isLocalTrack,
                            colors = ToggleButtonDefaults.tonalToggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                checkedContentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            if (isDownloading) {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Icon(
                                    painter = painterResource(if (isTrackDownloaded || isLocalTrack) R.drawable.ic_download_done
                                        else R.drawable.ic_download),
                                    contentDescription = when {
                                        isLocalTrack -> "Local file"
                                        isTrackDownloaded -> "Delete download"
                                        else -> "Download track"
                                    },
                                )
                            }
                        }
                    }
                }

                // Wavy seek bar — keyed to track so state resets on track change
                val trackId = track.id
                var isSeeking by remember(trackId) { mutableStateOf(false) }
                var seekPosition by remember(trackId) { mutableFloatStateOf(0f) }

                // Clear isSeeking once the player position catches up to the seek target
                SideEffect {
                    if (isSeeking && state.duration > 0L) {
                        val playerFraction = state.currentPosition.toFloat() / state.duration.toFloat()
                        if ((playerFraction - seekPosition).let { it * it } < 0.001f) {
                            isSeeking = false
                        }
                    }
                }

                val sliderPosition = if (isSeeking) {
                    seekPosition
                } else {
                    if (state.duration > 0L) {
                        state.currentPosition.toFloat() / state.duration.toFloat()
                    } else {
                        0f
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .pointerInput(trackId) {
                            detectTapGestures { offset ->
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                seekPosition = fraction
                                val targetMs = (fraction * state.duration).toLong()
                                playerViewModel.onSeek(targetMs)
                                // Keep showing seekPosition until player catches up
                                isSeeking = true
                            }
                        }
                        .pointerInput(trackId) {
                            detectDragGestures(
                                onDragEnd = {
                                    val targetMs = (seekPosition * state.duration).toLong()
                                    playerViewModel.onSeek(targetMs)
                                    // Keep showing seekPosition until player catches up
                                },
                                onDragCancel = {
                                    isSeeking = false
                                },
                            ) { change, _ ->
                                change.consume()
                                val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                isSeeking = true
                                seekPosition = fraction
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.wavyProgressBar) {
                        LinearWavyProgressIndicator(
                            progress = { sliderPosition.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            stroke = Stroke(width = 24.dp.value),
                            trackStroke = Stroke(width = 24.dp.value),
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { sliderPosition.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }

                // Time labels — reflect seek position during drag
                val displayPosition = if (isSeeking) {
                    (seekPosition * state.duration).toLong()
                } else {
                    state.currentPosition
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime(displayPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val remaining = (state.duration - displayPosition).coerceAtLeast(0L)
                    Text(
                        text = "-${formatTime(remaining)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Top control row: Previous / Play / Next
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Previous button
                    OutlinedButton(
                        onClick = { playerViewModel.onPrevious() },
                        modifier = Modifier.size(56.dp),
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_skip_previous),
                            contentDescription = "Previous",
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    // Play/Pause toggle button
                    ToggleButton(
                        checked = state.isPlaying,
                        onCheckedChange = { playerViewModel.onPlayPause() },
                        modifier = Modifier.size(80.dp),
                        colors = ToggleButtonDefaults.toggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    // Next button
                    OutlinedButton(
                        onClick = { playerViewModel.onNext() },
                        modifier = Modifier.size(56.dp),
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_skip_next),
                            contentDescription = "Next",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                // Bottom control row: Shuffle / Add to Playlist / Repeat
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TonalToggleButton(
                        checked = state.shuffleEnabled,
                        onCheckedChange = { playerViewModel.onToggleShuffle() },
                        colors = ToggleButtonDefaults.tonalToggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            checkedContainerColor = MaterialTheme.colorScheme.tertiary,
                            checkedContentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_shuffle),
                            contentDescription = "Shuffle",
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    TonalToggleButton(
                        checked = track.id in state.userPlaylistTrackIds,
                        onCheckedChange = { showPlaylistSheet = true },
                        colors = ToggleButtonDefaults.tonalToggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            checkedContainerColor = MaterialTheme.colorScheme.tertiary,
                            checkedContentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_playlist_add),
                            contentDescription = "Add to playlist",
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    TonalToggleButton(
                        checked = state.repeatMode != RepeatMode.OFF,
                        onCheckedChange = { playerViewModel.onToggleRepeat() },
                        colors = ToggleButtonDefaults.tonalToggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            checkedContainerColor = MaterialTheme.colorScheme.tertiary,
                            checkedContentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(when (state.repeatMode) {
                                RepeatMode.ONE -> R.drawable.ic_repeat_one
                                else -> R.drawable.ic_repeat
                            }),
                            contentDescription = "Repeat",
                        )
                    }
                }

                // Up Next section — use actual queue index
                val currentIndex = state.currentQueueIndex
                val upNextTracks = if (currentIndex >= 0 && currentIndex < state.queue.lastIndex) {
                    state.queue.subList(currentIndex + 1, minOf(currentIndex + 6, state.queue.size))
                } else {
                    emptyList()
                }

                if (upNextTracks.isNotEmpty()) {
                    Column {
                        Text(
                            text = "Up Next",
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )

                        upNextTracks.forEachIndexed { upNextIndex, queueTrack ->
                            val queueIndex = currentIndex + 1 + upNextIndex
                            key("upnext_${queueIndex}_${queueTrack.id}") {
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val pressScale by animateFloatAsState(
                                    targetValue = if (isPressed) 0.97f else 1f,
                                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                                    label = "upNextPressScale",
                                )
                                val isDownloaded = queueTrack.id in state.downloadedTrackIds || queueTrack.isLocal

                                Surface(
                                    onClick = {
                                        playerViewModel.skipToQueueIndex(queueIndex)
                                    },
                                    interactionSource = interactionSource,
                                    shape = segmentedItemShape(upNextIndex, upNextTracks.size),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier
                                        .padding(
                                            top = if (upNextIndex == 0) 8.dp else 1.dp,
                                            bottom = if (upNextIndex == upNextTracks.lastIndex) 0.dp else 1.dp,
                                        )
                                        .graphicsLayer {
                                            scaleX = pressScale
                                            scaleY = pressScale
                                        },
                                ) {
                                    ListItem(
                                        headlineContent = {
                                            Text(
                                                text = queueTrack.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        supportingContent = {
                                            Text(
                                                text = queueTrack.artist,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        leadingContent = {
                                            AsyncImage(
                                                model = queueTrack.artUrl,
                                                contentDescription = queueTrack.albumTitle,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(AppShapes.SearchResultTrack),
                                                contentScale = ContentScale.Crop,
                                            )
                                        },
                                        trailingContent = if (isDownloaded) {
                                            {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_download_done),
                                                    contentDescription = "Downloaded",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete download confirmation dialog
    if (showDeleteDownloadDialog && track != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDownloadDialog = false },
            title = { Text("Delete Download") },
            text = { Text("Remove downloaded file for '${track.title}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        playerViewModel.onDeleteTrackDownload()
                        showDeleteDownloadDialog = false
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDownloadDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Playback debug info sheet
    if (showDebugSheet && track != null) {
        ModalBottomSheet(
            onDismissRequest = { showDebugSheet = false },
        ) {
            Text(
                text = "Playback Info",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val isLocalTrackDebug = track.isLocal
                val isLocal = state.currentSourcePath != null
                val isTrackDownloaded = track.id in state.downloadedTrackIds
                val isDownloading = state.downloadingTrackId == track.id

                val downloadStatus = when {
                    isLocalTrackDebug -> "Local file"
                    isDownloading -> "Downloading..."
                    isTrackDownloaded -> "Downloaded"
                    isLocal -> "Cached"
                    else -> "Not downloaded"
                }

                val formatDisplay = if (isLocalTrackDebug) "Local" else (state.currentPlaybackFormat?.displayName ?: "Unknown")
                val sourceDisplay = if (isLocalTrackDebug || isLocal) "Local file" else "Streaming"

                Surface(
                    shape = segmentedItemShape(0, 6),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    ListItem(
                        headlineContent = { Text(formatDisplay) },
                        supportingContent = { Text("Audio format") },
                        leadingContent = {
                            Icon(painterResource(R.drawable.ic_audio_file), contentDescription = null)
                        },
                    )
                }
                Surface(
                    shape = segmentedItemShape(1, 6),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    ListItem(
                        headlineContent = { Text(sourceDisplay) },
                        supportingContent = { Text("Source") },
                        leadingContent = {
                            Icon(painterResource(R.drawable.ic_cloud), contentDescription = null)
                        },
                    )
                }
                Surface(
                    shape = segmentedItemShape(2, 6),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    ListItem(
                        headlineContent = { Text(downloadStatus) },
                        supportingContent = { Text("Download status") },
                        leadingContent = {
                            Icon(painterResource(R.drawable.ic_download), contentDescription = null)
                        },
                    )
                }
                Surface(
                    shape = segmentedItemShape(3, 6),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = state.currentSourcePath?.let {
                                    it.substringAfterLast("/downloads/")
                                } ?: track.streamUrl?.take(60) ?: "None",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = { Text("File path") },
                        leadingContent = {
                            Icon(painterResource(R.drawable.ic_storage), contentDescription = null)
                        },
                    )
                }
                Surface(
                    shape = segmentedItemShape(4, 6),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = track.id,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = { Text("Track ID") },
                        leadingContent = {
                            Icon(painterResource(R.drawable.ic_info), contentDescription = null)
                        },
                    )
                }
                Surface(
                    shape = segmentedItemShape(5, 6),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = track.albumId,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = { Text("Album ID") },
                        leadingContent = {
                            Icon(painterResource(R.drawable.ic_info), contentDescription = null)
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Add to playlist bottom sheet
    if (showPlaylistSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistSheet = false },
        ) {
            val userPlaylists = state.playlists.filter { !it.isSystem }
            Box {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Add to playlist",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    if (userPlaylists.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 32.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(AppShapes.EmptyStateIcon)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_queue_music),
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No playlists yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Create one to get started",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            userPlaylists.forEachIndexed { index, playlist ->
                                Surface(
                                    shape = segmentedItemShape(index, userPlaylists.size),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    PlaylistListItem(
                                        playlist = playlist,
                                        onClick = {
                                            showPlaylistSheet = false
                                            playerViewModel.addToPlaylist(playlist.id)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(72.dp))
                }
                FloatingActionButton(
                    onClick = { showCreatePlaylistSheet = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = "Create playlist",
                    )
                }
            }
        }
    }

    // Create playlist sheet (from add-to-playlist context)
    if (showCreatePlaylistSheet) {
        PlaylistEditSheet(
            onDismiss = { showCreatePlaylistSheet = false },
            onConfirm = { name, shapeKey, iconUrl ->
                showCreatePlaylistSheet = false
                showPlaylistSheet = false
                playerViewModel.createPlaylistAndAddTrack(name, shapeKey, iconUrl)
            },
            isCreate = true,
        )
    }
}

/**
 * A shape that morphs between two RoundedPolygon states.
 */
private class PlaybackMorphShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = morph.toPath(progress = progress)

        // Use graphics-shapes native bounds calculation
        val bounds = morph.calculateBounds()
        val pathWidth = bounds[2] - bounds[0]  // right - left
        val pathHeight = bounds[3] - bounds[1] // bottom - top
        val centerX = (bounds[0] + bounds[2]) / 2f
        val centerY = (bounds[1] + bounds[3]) / 2f

        val matrix = Matrix()
        if (pathWidth > 0f && pathHeight > 0f) {
            matrix.postTranslate(-centerX, -centerY)
            val scale = minOf(size.width / pathWidth, size.height / pathHeight)
            matrix.postScale(scale, scale)
            matrix.postTranslate(size.width / 2f, size.height / 2f)
        } else {
            matrix.postScale(size.width / 2f, size.height / 2f)
            matrix.postTranslate(size.width / 2f, size.height / 2f)
        }
        path.transform(matrix)

        return Outline.Generic(path.asComposePath())
    }
}

private fun formatTime(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
