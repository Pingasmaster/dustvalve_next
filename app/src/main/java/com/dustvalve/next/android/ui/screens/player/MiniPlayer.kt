package com.dustvalve.next.android.ui.screens.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dustvalve.next.android.R
import com.dustvalve.next.android.ui.components.MorphShape
import com.dustvalve.next.android.ui.components.TrackArtPlaceholder
import com.dustvalve.next.android.ui.util.tick
import com.dustvalve.next.android.ui.util.toggle
import kotlinx.coroutines.launch

/**
 * Collapsed now-playing bar; the `false` (collapsed) state of the host
 * mini <-> full container transform.
 *
 * The bar is tagged with [PLAYER_SURFACE_KEY] / [PLAYER_ART_KEY] so it morphs
 * into [FullPlayer] via the host [SharedTransitionScope]. Vertical drag is
 * velocity-aware:
 * - drag UP feeds [onExpandSeek] (0..1 of the way to full) and commits via
 *   [onExpandSettle] (positive velocity fraction = upward/expand);
 * - drag DOWN past threshold (or a downward flick) stops playback, preserving
 *   the long-standing swipe-down-to-dismiss behaviour.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniPlayer(
    sharedScope: SharedTransitionScope,
    visScope: AnimatedVisibilityScope,
    expandDistancePx: Float,
    onExpandClick: () -> Unit,
    onExpandSeek: (Float) -> Unit,
    onExpandSettle: (Float) -> Unit,
    modifier: Modifier = Modifier,
    // Activity-scoped: hiltViewModel() resolves to MainActivity, so this is
    // the same PlayerViewModel instance the rest of the UI sees.
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by playerViewModel.uiState.collectAsStateWithLifecycle()

    // Remember the last non-null track so it persists during exit animation
    var lastTrack by remember { mutableStateOf(state.currentTrack) }
    if (state.currentTrack != null) {
        lastTrack = state.currentTrack
    }
    val track = lastTrack ?: return

    // Morph between Square (not playing) and Cookie9Sided (playing)
    val squarePolygon = remember { MaterialShapes.Square }
    val cookiePolygon = remember { MaterialShapes.Cookie9Sided }
    val morph = remember(squarePolygon, cookiePolygon) { Morph(squarePolygon, cookiePolygon) }

    val morphProgress by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "miniPlayerMorph",
    )

    // Continuous rotation when playing - accumulates smoothly, freezes on pause
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.isPlaying) {
        if (!state.isPlaying) return@LaunchedEffect
        var previousMs = withInfiniteAnimationFrameMillis { it }
        while (true) {
            val currentMs = withInfiniteAnimationFrameMillis { it }
            val deltaMs = currentMs - previousMs
            previousMs = currentMs
            rotationDegrees = (rotationDegrees + deltaMs * 360f / 8000f) % 360f
        }
    }

    val rawProgress = if (state.duration > 0L) {
        (state.currentPosition.toFloat() / state.duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "miniPlayerProgress",
    )

    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    // Downward dismiss translate (swipe-down-to-stop). The upward/expand preview
    // is handled entirely by the shared-bounds morph, so the bar itself does not
    // translate up.
    val dismissOffset = remember { Animatable(0f) }
    val dismissSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val boundsSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    var cumulativeDy by remember { mutableFloatStateOf(0f) }
    var pastThreshold by remember { mutableStateOf(false) }
    val velocityTracker = remember { VelocityTracker() }

    val surfaceModifier = with(sharedScope) {
        Modifier
            .fillMaxWidth()
            .sharedBounds(
                sharedContentState = rememberSharedContentState(PLAYER_SURFACE_KEY),
                animatedVisibilityScope = visScope,
                boundsTransform = BoundsTransform { _, _ -> boundsSpec },
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(0.dp)),
            )
    }
    val artShared = with(sharedScope) {
        Modifier.sharedElement(
            sharedContentState = rememberSharedContentState(PLAYER_ART_KEY),
            animatedVisibilityScope = visScope,
            boundsTransform = BoundsTransform { _, _ -> boundsSpec },
        )
    }

    Surface(
        tonalElevation = 2.dp,
        modifier = modifier
            .testTag(com.dustvalve.next.android.ui.TestTags.MINI_PLAYER)
            .then(surfaceModifier)
            .graphicsLayer {
                translationY = dismissOffset.value
                alpha = if (dismissOffset.value > 0f) {
                    (1f - (dismissOffset.value / size.height) * 0.6f).coerceIn(0.4f, 1f)
                } else {
                    1f
                }
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .pointerInput(expandDistancePx) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                cumulativeDy = 0f
                                pastThreshold = false
                                velocityTracker.resetTracking()
                            },
                            onDragEnd = {
                                val vy = velocityTracker.calculateVelocity().y
                                if (cumulativeDy < 0f) {
                                    // Upward gesture: hand the release velocity to the host,
                                    // which decides expand-vs-snap-back from position + fling.
                                    onExpandSettle(-vy / expandDistancePx)
                                    scope.launch { dismissOffset.snapTo(0f) }
                                } else {
                                    val dismiss = cumulativeDy > size.height * 0.5f ||
                                        vy > PLAYER_FLING_VELOCITY
                                    if (dismiss) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        playerViewModel.onStop()
                                        scope.launch { dismissOffset.snapTo(0f) }
                                    } else {
                                        scope.launch { dismissOffset.animateTo(0f, dismissSpec) }
                                    }
                                    onExpandSeek(0f)
                                }
                                cumulativeDy = 0f
                                pastThreshold = false
                            },
                            onDragCancel = {
                                scope.launch { dismissOffset.animateTo(0f, dismissSpec) }
                                onExpandSeek(0f)
                                cumulativeDy = 0f
                                pastThreshold = false
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                cumulativeDy += dragAmount
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                if (cumulativeDy < 0f) {
                                    if (dismissOffset.value != 0f) {
                                        scope.launch { dismissOffset.snapTo(0f) }
                                    }
                                    val frac = (-cumulativeDy / expandDistancePx).coerceIn(0f, 1f)
                                    onExpandSeek(frac)
                                    val crossed = frac >= 0.25f
                                    if (crossed && !pastThreshold) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        pastThreshold = true
                                    } else if (!crossed) {
                                        pastThreshold = false
                                    }
                                } else {
                                    onExpandSeek(0f)
                                    scope.launch { dismissOffset.snapTo(cumulativeDy) }
                                    val crossed = cumulativeDy >= size.height * 0.5f
                                    if (crossed && !pastThreshold) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        pastThreshold = true
                                    } else if (!crossed) {
                                        pastThreshold = false
                                    }
                                }
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onExpandClick() })
                    }
                    .padding(horizontal = 12.dp),
            ) {
                val artModifier = Modifier
                    .size(48.dp)
                    .then(artShared)
                    .graphicsLayer { rotationZ = rotationDegrees }
                    .clip(
                        MorphShape(
                            morph = morph,
                            progress = morphProgress,
                        ),
                    )
                if (track.artUrl.isNotBlank()) {
                    AsyncImage(
                        model = track.artUrl,
                        contentDescription = track.albumTitle.ifEmpty { track.title },
                        contentScale = ContentScale.Crop,
                        modifier = artModifier,
                    )
                } else {
                    TrackArtPlaceholder(modifier = artModifier)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = {
                        hapticFeedback.tick()
                        playerViewModel.onPrevious()
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_previous),
                        contentDescription = stringResource(R.string.player_cd_previous),
                    )
                }

                IconButton(
                    onClick = {
                        hapticFeedback.toggle(!state.isPlaying)
                        playerViewModel.onPlayPause()
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                        contentDescription = stringResource(if (state.isPlaying) R.string.player_cd_pause else R.string.player_cd_play),
                    )
                }

                IconButton(
                    onClick = {
                        hapticFeedback.tick()
                        playerViewModel.onNext()
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_next),
                        contentDescription = stringResource(R.string.player_cd_next),
                    )
                }
            }

            // Mini-player uses the same wavy/linear style preference as the full
            // player but stays slim - the user-configurable height applies only
            // to the seek bar in the full player.
            val miniIsWavy = state.progressBarStyle == "wavy"
            val miniMod = Modifier.fillMaxWidth().height(2.dp)
            if (state.isLoadingTrack) {
                if (miniIsWavy) {
                    LinearWavyProgressIndicator(
                        modifier = miniMod,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = miniMod,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            } else if (miniIsWavy) {
                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = miniMod,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            } else {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = miniMod,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}
