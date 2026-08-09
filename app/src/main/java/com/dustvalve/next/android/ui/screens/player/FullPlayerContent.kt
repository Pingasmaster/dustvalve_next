package com.dustvalve.next.android.ui.screens.player

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.ui.adaptive.AdaptiveLayoutInfo
import com.dustvalve.next.android.ui.util.tick
import com.dustvalve.next.android.ui.util.toggle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FullPlayerScaffold(
    adaptiveInfo: AdaptiveLayoutInfo,
    model: FullPlayerScaffoldModel,
    modifier: Modifier = Modifier,
) {
    val adaptive = adaptiveInfo
    val state = model.playback.state
    val track = state.currentTrack
    val chrome = model.chrome
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxSize()
            .then(model.shared.surfaceShared),
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentWindowInsets = WindowInsets.systemBars,
            snackbarHost = { SnackbarHost(model.snackbarHostState) },
            floatingActionButton = {
                FullPlayerQueueFab(
                    useDualPane = adaptive.useDualPane,
                    hasTrack = state.currentTrack != null,
                    queueSize = state.queue.size,
                    currentQueueIndex = state.currentQueueIndex,
                    onClick = chrome.onShowQueueSheet,
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
                        text = stringResource(R.string.player_no_track),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Scaffold
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                Box(
                    modifier = if (adaptive.useDualPane) {
                        Modifier.weight(0.62f).fillMaxHeight()
                    } else {
                        Modifier.fillMaxSize()
                    },
                ) {
                    FullPlayerMainColumn(
                        adaptiveInfo = adaptive,
                        track = track,
                        model = model,
                    )
                }
                if (adaptive.useDualPane) {
                    Surface(
                        modifier = Modifier
                            .weight(0.38f)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        UpNextQueuePane(
                            currentQueueIndex = state.currentQueueIndex,
                            currentTrackId = track.id,
                            downloadedTrackIds = state.downloadedTrackIds,
                            onEntryLongClick = chrome.onEntryLongClick,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayerQueueFab(
    useDualPane: Boolean,
    hasTrack: Boolean,
    queueSize: Int,
    currentQueueIndex: Int,
    onClick: () -> Unit,
) {
    val upNextCount = if (currentQueueIndex >= 0) {
        (queueSize - currentQueueIndex - 1).coerceAtLeast(0)
    } else {
        0
    }
    if (!useDualPane && hasTrack && upNextCount > 0) {
        ExtendedFloatingActionButton(
            onClick = onClick,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_queue_music),
                    contentDescription = null,
                )
            },
            text = { Text(stringResource(R.string.player_queue_count, upNextCount)) },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayerMainColumn(
    adaptiveInfo: AdaptiveLayoutInfo,
    track: Track,
    model: FullPlayerScaffoldModel,
) {
    val state = model.playback.state
    val positionState = model.playback.positionState
    val transport = model.actions.transport
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(modifier = Modifier.height(86.dp))
            FullPlayerAlbumArtRow(
                adaptiveInfo = adaptiveInfo,
                track = track,
                state = state,
                isCarouselMode = model.layout.isCarouselMode,
                modifier = model.shared.artShared,
                motion = model.motion,
                chrome = model.chrome,
                artActions = model.actions.art,
            )
            FullPlayerTrackTitleSection(
                track = track,
                state = state,
                actions = model.actions.track,
            )
            FullPlayerSeekBar(
                trackId = track.id,
                currentPosition = positionState.positionMs,
                duration = positionState.durationMs,
                isLoadingTrack = state.isLoadingTrack,
                progressBarStyle = state.progressBarStyle,
                progressBarSizeDp = state.progressBarSizeDp,
                onSeek = transport.onSeek,
            )
            FullPlayerTransportControls(
                isPlaying = state.isPlaying,
                onPrevious = transport.onPrevious,
                onPlayPause = transport.onPlayPause,
                onNext = transport.onNext,
            )
            FullPlayerShuffleRepeatRow(
                shuffleEnabled = state.shuffleEnabled,
                repeatMode = state.repeatMode,
                onToggleShuffle = transport.onToggleShuffle,
                onToggleRepeat = transport.onToggleRepeat,
            )
            Spacer(modifier = Modifier.height(80.dp))
        }
        FullPlayerCollapseBar(
            expandDistancePx = model.layout.expandDistancePx,
            showVolumeButton = state.showVolumeButton,
            chrome = model.chrome,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayerTrackTitleSection(
    track: Track,
    state: PlayerUiState,
    actions: FullPlayerTrackActions,
) {
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
        FullPlayerTrackActionButtons(
            track = track,
            isTrackDownloaded = track.id in state.downloadedTrackIds,
            isDownloading = state.downloadingTrackId == track.id,
            isInUserPlaylist = track.id in state.userPlaylistTrackIds,
            actions = actions,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FullPlayerTransportControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedButton(
            onClick = {
                hapticFeedback.tick()
                onPrevious()
            },
            modifier = Modifier
                .size(56.dp)
                .testTag(com.dustvalve.next.android.ui.TestTags.PLAYER_PREVIOUS),
            shapes = ButtonDefaults.shapes(),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_skip_previous),
                contentDescription = stringResource(R.string.player_cd_previous),
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(modifier = Modifier.width(24.dp))
        ToggleButton(
            checked = isPlaying,
            onCheckedChange = {
                hapticFeedback.toggle(!isPlaying)
                onPlayPause()
            },
            modifier = Modifier
                .size(80.dp)
                .testTag(com.dustvalve.next.android.ui.TestTags.PLAYER_PLAY_PAUSE),
            colors = ToggleButtonDefaults.toggleButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                contentDescription = stringResource(
                    if (isPlaying) R.string.player_cd_pause else R.string.player_cd_play,
                ),
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(modifier = Modifier.width(24.dp))
        OutlinedButton(
            onClick = {
                hapticFeedback.tick()
                onNext()
            },
            modifier = Modifier
                .size(56.dp)
                .testTag(com.dustvalve.next.android.ui.TestTags.PLAYER_NEXT),
            shapes = ButtonDefaults.shapes(),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_skip_next),
                contentDescription = stringResource(R.string.player_cd_next),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayerCollapseBar(
    expandDistancePx: Float,
    showVolumeButton: Boolean,
    chrome: FullPlayerChrome,
) {
    var collapseDy by remember { mutableFloatStateOf(0f) }
    val collapseVelocityTracker = remember { VelocityTracker() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .pointerInput(expandDistancePx) {
                detectVerticalDragGestures(
                    onDragStart = {
                        collapseDy = 0f
                        collapseVelocityTracker.resetTracking()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        collapseDy = (collapseDy + dragAmount).coerceAtLeast(0f)
                        collapseVelocityTracker.addPosition(change.uptimeMillis, change.position)
                        chrome.onCollapseSeek((1f - collapseDy / expandDistancePx).coerceIn(0f, 1f))
                    },
                    onDragCancel = {
                        chrome.onCollapseSeek(1f)
                        collapseDy = 0f
                    },
                    onDragEnd = {
                        chrome.onCollapseSettle(collapseVelocityTracker.calculateVelocity().y)
                        collapseDy = 0f
                    },
                )
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = chrome.onCollapse, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                contentDescription = stringResource(R.string.player_cd_collapse),
            )
        }
        if (showVolumeButton) {
            IconButton(onClick = chrome.onShowVolumeSheet, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(R.drawable.ic_volume_up),
                    contentDescription = stringResource(R.string.player_cd_volume),
                )
            }
        }
    }
}
