package com.dustvalve.next.android.ui.screens.player

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.RepeatMode
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.ui.util.tick
import com.dustvalve.next.android.ui.util.toggle
import java.util.Locale

/** Number of haptic segments across the seek bar (~ticks per full-width scrub). */
private const val SEEK_TICK_SEGMENTS = 40

/**
 * Connected M3E ButtonGroup: Artist / Album / Favorite / Download / Add-to-playlist.
 * All icon-only (the artist name is already in the title above), all ~40 dp tall to
 * match the previous FilledTonalToggleButton sizing. Spaced 8 dp apart for visual
 * breathing room - wider than the 2 dp ButtonGroupDefaults.ConnectedSpaceBetween.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FullPlayerTrackActionButtons(
    track: Track,
    isTrackDownloaded: Boolean,
    isDownloading: Boolean,
    isInUserPlaylist: Boolean,
    actions: FullPlayerTrackActions,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val isLocalTrack = track.isLocal
    val albumNavEnabled = track.albumUrl.isNotEmpty()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        // Artist navigation (icon-only - name is in the title above).
        // Stateless action: FilledTonalButton, not FilledTonalToggleButton,
        // so TalkBack announces "button" rather than "not selected".
        FilledTonalButton(
            onClick = { actions.onArtistClick(track) },
            shape = ButtonGroupDefaults.connectedLeadingButtonShape,
            enabled = track.artistUrl.isNotEmpty() || track.isLocal,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_person),
                contentDescription = stringResource(R.string.player_cd_open_artist),
            )
        }
        // Album navigation (new). Disabled when the track has
        // no album page (streaming sources where it's not
        // canonical). Also stateless - same FilledTonalButton.
        FilledTonalButton(
            onClick = { actions.onAlbumClick(track) },
            shape = ButtonGroupDefaults.connectedMiddleButtonShapes().shape,
            enabled = albumNavEnabled,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_album),
                contentDescription = stringResource(R.string.player_cd_open_album),
            )
        }
        // Favorite toggle.
        FilledTonalToggleButton(
            checked = track.isFavorite,
            onCheckedChange = {
                hapticFeedback.toggle(!track.isFavorite)
                actions.onToggleFavorite()
            },
            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                painter = painterResource(
                    if (track.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                ),
                contentDescription = stringResource(
                    if (track.isFavorite) {
                        R.string.player_cd_remove_from_favorites
                    } else {
                        R.string.player_cd_add_to_favorites
                    },
                ),
            )
        }
        // Download toggle (matches Favorite's interaction language).
        FilledTonalToggleButton(
            checked = isTrackDownloaded || isLocalTrack,
            onCheckedChange = {
                if (isLocalTrack) return@FilledTonalToggleButton
                if (isTrackDownloaded) {
                    actions.onRequestDeleteDownload()
                } else if (!isDownloading) {
                    actions.onDownloadTrack()
                }
            },
            enabled = !isDownloading && !isLocalTrack,
            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
            modifier = Modifier.weight(1f),
        ) {
            if (isDownloading) {
                CircularWavyProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    painter = painterResource(
                        if (isTrackDownloaded || isLocalTrack) {
                            R.drawable.ic_download_done
                        } else {
                            R.drawable.ic_download
                        },
                    ),
                    contentDescription = stringResource(
                        when {
                            isLocalTrack -> R.string.player_cd_local_file
                            isTrackDownloaded -> R.string.player_cd_delete_download
                            else -> R.string.player_cd_download_track
                        },
                    ),
                )
            }
        }
        // Add to playlist (new - opens the existing AddToPlaylistSheet).
        FilledTonalToggleButton(
            checked = isInUserPlaylist,
            onCheckedChange = { actions.onAddToPlaylist() },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_playlist_add),
                contentDescription = stringResource(R.string.player_cd_add_to_playlist),
            )
        }
    }
}

/**
 * Wavy (or linear) seek bar keyed to [trackId] so scrub state resets on track
 * change, plus elapsed/remaining time labels that reflect the scrub position.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FullPlayerSeekBar(
    trackId: String,
    currentPosition: Long,
    duration: Long,
    isLoadingTrack: Boolean,
    progressBarStyle: String,
    progressBarSizeDp: Int,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var isSeeking by remember(trackId) { mutableStateOf(false) }
    var seekPosition by remember(trackId) { mutableFloatStateOf(0f) }
    var lastSeekStep by remember(trackId) { mutableIntStateOf(-1) }

    SideEffect {
        if (isSeeking && duration > 0L) {
            val playerFraction = currentPosition.toFloat() / duration.toFloat()
            if ((playerFraction - seekPosition).let { it * it } < 0.001f) {
                isSeeking = false
            }
        }
    }

    val sliderPosition = if (isSeeking) {
        seekPosition
    } else if (duration > 0L) {
        currentPosition.toFloat() / duration.toFloat()
    } else {
        0f
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .then(
                    if (isLoadingTrack) {
                        Modifier
                    } else {
                        seekGestureModifier(
                            trackId = trackId,
                            duration = duration,
                            seek = SeekGestureCallbacks(
                                onSeek = onSeek,
                                seekPosition = { seekPosition },
                                onSeekFraction = { fraction -> seekPosition = fraction },
                                setSeeking = { isSeeking = it },
                                lastSeekStep = { lastSeekStep },
                                setLastSeekStep = { lastSeekStep = it },
                                hapticTick = { hapticFeedback.tick() },
                            ),
                        )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            FullPlayerSeekProgressIndicator(
                isLoadingTrack = isLoadingTrack,
                isWavy = progressBarStyle == "wavy",
                barHeightDp = progressBarSizeDp,
                sliderPosition = sliderPosition,
            )
        }
        FullPlayerSeekTimeLabels(
            isLoadingTrack = isLoadingTrack,
            isSeeking = isSeeking,
            seekPosition = seekPosition,
            currentPosition = currentPosition,
            duration = duration,
        )
    }
}

private fun seekGestureModifier(
    trackId: String,
    duration: Long,
    seek: SeekGestureCallbacks,
): Modifier = Modifier
    .pointerInput(trackId) {
        detectTapGestures { offset ->
            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
            seek.onSeekFraction(fraction)
            seek.setSeeking(true)
            seek.setLastSeekStep(-1)
            seek.onSeek((fraction * duration).toLong())
            seek.hapticTick()
        }
    }
    .pointerInput(trackId) {
        detectDragGestures(
            onDragEnd = {
                seek.onSeek((seek.seekPosition() * duration).toLong())
                seek.setLastSeekStep(-1)
            },
            onDragCancel = {
                seek.setSeeking(false)
                seek.setLastSeekStep(-1)
            },
        ) { change, _ ->
            change.consume()
            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
            seek.setSeeking(true)
            seek.onSeekFraction(fraction)
            val step = (fraction * SEEK_TICK_SEGMENTS).toInt()
            if (step != seek.lastSeekStep()) {
                seek.hapticTick()
                seek.setLastSeekStep(step)
            }
        }
    }

private class SeekGestureCallbacks(
    val onSeek: (Long) -> Unit,
    val seekPosition: () -> Float,
    val onSeekFraction: (Float) -> Unit,
    val setSeeking: (Boolean) -> Unit,
    val lastSeekStep: () -> Int,
    val setLastSeekStep: (Int) -> Unit,
    val hapticTick: () -> Unit,
)


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayerSeekProgressIndicator(
    isLoadingTrack: Boolean,
    isWavy: Boolean,
    barHeightDp: Int,
    sliderPosition: Float,
) {
    val barHeight = barHeightDp.dp
    when {
        isLoadingTrack && isWavy -> LinearWavyProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(barHeight),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        isLoadingTrack -> LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(barHeight),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        isWavy -> LinearWavyProgressIndicator(
            progress = { sliderPosition.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(barHeight),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            stroke = Stroke(width = barHeight.value),
            trackStroke = Stroke(width = barHeight.value),
        )
        else -> LinearProgressIndicator(
            progress = { sliderPosition.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(barHeight),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun FullPlayerSeekTimeLabels(
    isLoadingTrack: Boolean,
    isSeeking: Boolean,
    seekPosition: Float,
    currentPosition: Long,
    duration: Long,
) {
    val displayPosition = when {
        isLoadingTrack -> null
        isSeeking -> (seekPosition * duration).toLong()
        else -> currentPosition
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (displayPosition != null) formatSeekTime(displayPosition) else "--:--",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(com.dustvalve.next.android.ui.TestTags.PLAYER_POSITION),
        )
        val remaining = displayPosition?.let { (duration - it).coerceAtLeast(0L) }
        Text(
            text = if (remaining != null) "-${formatSeekTime(remaining)}" else "--:--",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(com.dustvalve.next.android.ui.TestTags.PLAYER_DURATION),
        )
    }
}

/**
 * Bottom control row: Shuffle | Repeat - same connected ButtonGroup styling as
 * the top action row (8 dp gap, connected leading/trailing shapes, weight(1f)
 * per button, ~40 dp tall). Repeat is tri-state (OFF/ON/ONE): checked is
 * "any-on", icon swaps to repeat_one when in ONE mode.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FullPlayerShuffleRepeatRow(
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        FilledTonalToggleButton(
            checked = shuffleEnabled,
            onCheckedChange = {
                hapticFeedback.toggle(!shuffleEnabled)
                onToggleShuffle()
            },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shuffle),
                contentDescription = stringResource(R.string.player_cd_shuffle),
            )
        }
        FilledTonalToggleButton(
            checked = repeatMode != RepeatMode.OFF,
            onCheckedChange = {
                // Cycle OFF->ALL->ONE->OFF; only ONE->OFF is "turning off".
                hapticFeedback.toggle(repeatMode != RepeatMode.ONE)
                onToggleRepeat()
            },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                painter = painterResource(
                    when (repeatMode) {
                        RepeatMode.ONE -> R.drawable.ic_repeat_one
                        else -> R.drawable.ic_repeat
                    },
                ),
                contentDescription = stringResource(R.string.player_cd_repeat),
            )
        }
    }
}

private fun formatSeekTime(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
