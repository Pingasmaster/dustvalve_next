package com.dustvalve.next.android.ui.screens.player

import com.dustvalve.next.android.ui.components.TrackArtPlaceholder

import android.graphics.Matrix
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dustvalve.next.android.R
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniPlayer(
    playerViewModel: PlayerViewModel,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by playerViewModel.uiState.collectAsStateWithLifecycle()

    // Full-height slide uses slow spec for a grander entrance/exit
    val slideSpec = MaterialTheme.motionScheme.slowSpatialSpec<IntOffset>()
    // Remember the last non-null track so it persists during exit animation
    var lastTrack by remember { mutableStateOf(state.currentTrack) }
    if (state.currentTrack != null) {
        lastTrack = state.currentTrack
    }
    val displayTrack = lastTrack

    // Morph between Square (not playing) and Cookie9Sided (playing)
    val squarePolygon = remember { MaterialShapes.Square }
    val cookiePolygon = remember { MaterialShapes.Cookie9Sided }
    val morph = remember(squarePolygon, cookiePolygon) { Morph(squarePolygon, cookiePolygon) }

    val morphProgress by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "miniPlayerMorph",
    )

    // Continuous rotation when playing — accumulates smoothly, freezes on pause
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
    val effectiveRotation = rotationDegrees

    AnimatedVisibility(
        visible = state.isMiniPlayerVisible,
        modifier = modifier,
        enter = slideInVertically(
            animationSpec = slideSpec,
            initialOffsetY = { it },
        ),
        exit = slideOutVertically(
            animationSpec = slideSpec,
            targetOffsetY = { it },
        ),
    ) {
        val track = displayTrack ?: return@AnimatedVisibility
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
        val swipeOffsetY = remember { Animatable(0f) }
        val swipeSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
        var hasPassedThreshold by remember { mutableStateOf(false) }

        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = swipeOffsetY.value
                    alpha = if (swipeOffsetY.value > 0f) {
                        (1f - (swipeOffsetY.value / size.height) * 0.6f).coerceIn(0.4f, 1f)
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
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { hasPassedThreshold = false },
                                onDragEnd = {
                                    val threshold = size.height * 0.35f
                                    if (swipeOffsetY.value > threshold) {
                                        scope.launch {
                                            swipeOffsetY.animateTo(size.height.toFloat(), swipeSpec)
                                            playerViewModel.onStop()
                                            swipeOffsetY.snapTo(0f)
                                        }
                                    } else if (swipeOffsetY.value < -threshold) {
                                        scope.launch {
                                            swipeOffsetY.animateTo(-size.height.toFloat(), swipeSpec)
                                            onExpandClick()
                                            swipeOffsetY.snapTo(0f)
                                        }
                                    } else {
                                        scope.launch { swipeOffsetY.animateTo(0f, swipeSpec) }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch { swipeOffsetY.animateTo(0f, swipeSpec) }
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch { swipeOffsetY.snapTo(swipeOffsetY.value + dragAmount) }
                                    val threshold = size.height * 0.35f
                                    val nowPastThreshold = swipeOffsetY.value > threshold || swipeOffsetY.value < -threshold
                                    if (nowPastThreshold && !hasPassedThreshold) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        hasPassedThreshold = true
                                    } else if (!nowPastThreshold) {
                                        hasPassedThreshold = false
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
                        .graphicsLayer { rotationZ = effectiveRotation }
                        .clip(
                            MorphShape(
                                morph = morph,
                                progress = morphProgress,
                            )
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

                    IconButton(onClick = { playerViewModel.onPrevious() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_skip_previous),
                            contentDescription = "Previous",
                        )
                    }

                    IconButton(onClick = { playerViewModel.onPlayPause() }) {
                        Icon(
                            painter = painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                        )
                    }

                    IconButton(onClick = { playerViewModel.onNext() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_skip_next),
                            contentDescription = "Next",
                        )
                    }
                }

                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

/**
 * A shape that morphs between two RoundedPolygon states.
 * Rotation is handled externally via graphicsLayer.
 */
private class MorphShape(
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
