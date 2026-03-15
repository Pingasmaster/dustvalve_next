package com.dustvalve.next.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavySeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
) {
    val density = LocalDensity.current
    val trackHeightPx = with(density) { 4.dp.toPx() }
    val thumbRadiusPx = with(density) { 8.dp.toPx() }
    val maxAmplitudePx = with(density) { 6.dp.toPx() }
    val wavelengthPx = with(density) { 32.dp.toPx() }

    // Wave amplitude: animated between flat (paused) and wavy (playing)
    val targetAmplitude = if (isPlaying) 1f else 0f
    val waveAmplitudeFraction by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "waveAmplitude",
    )

    // Wave phase: continuous animation when playing
    var wavePhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var prevMs = withInfiniteAnimationFrameMillis { it }
        while (true) {
            val ms = withInfiniteAnimationFrameMillis { it }
            val delta = ms - prevMs
            wavePhase = (wavePhase + delta * 0.005f) % (2f * PI.toFloat())
            prevMs = ms
        }
    }

    Canvas(
        modifier = modifier
            .height(40.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val padding = thumbRadiusPx
                    val trackWidth = size.width - 2 * padding
                    val newValue = ((offset.x - padding) / trackWidth).coerceIn(0f, 1f)
                    onValueChange(newValue)
                    onValueChangeFinished()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onValueChangeFinished() },
                    onDragCancel = { onValueChangeFinished() },
                ) { change, _ ->
                    change.consume()
                    val padding = thumbRadiusPx
                    val trackWidth = size.width - 2 * padding
                    val newValue = ((change.position.x - padding) / trackWidth).coerceIn(0f, 1f)
                    onValueChange(newValue)
                }
            },
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2f
        val padding = thumbRadiusPx
        val trackWidth = canvasWidth - 2 * padding
        val thumbX = padding + value * trackWidth
        val amplitude = maxAmplitudePx * waveAmplitudeFraction

        // Draw active track (start to thumb)
        drawWavyPath(
            startX = padding,
            endX = thumbX,
            centerY = centerY,
            amplitude = amplitude,
            wavelength = wavelengthPx,
            phase = wavePhase,
            color = activeColor,
            strokeWidth = trackHeightPx,
        )

        // Draw inactive track (thumb to end)
        drawWavyPath(
            startX = thumbX,
            endX = canvasWidth - padding,
            centerY = centerY,
            amplitude = amplitude,
            wavelength = wavelengthPx,
            phase = wavePhase,
            color = inactiveColor,
            strokeWidth = trackHeightPx,
        )

        // Draw thumb
        val thumbY = if (amplitude > 0.01f) {
            centerY + amplitude * sin(2.0 * PI * thumbX / wavelengthPx + wavePhase).toFloat()
        } else {
            centerY
        }
        drawCircle(
            color = thumbColor,
            radius = thumbRadiusPx,
            center = Offset(thumbX, thumbY),
        )
    }
}

private fun DrawScope.drawWavyPath(
    startX: Float,
    endX: Float,
    centerY: Float,
    amplitude: Float,
    wavelength: Float,
    phase: Float,
    color: Color,
    strokeWidth: Float,
) {
    if (endX <= startX) return

    val path = Path()
    val step = 2f // pixel step for smooth curve
    var x = startX
    val startY = if (amplitude > 0.01f) {
        centerY + amplitude * sin(2.0 * PI * x / wavelength + phase).toFloat()
    } else {
        centerY
    }
    path.moveTo(x, startY)

    x += step
    while (x <= endX) {
        val y = if (amplitude > 0.01f) {
            centerY + amplitude * sin(2.0 * PI * x / wavelength + phase).toFloat()
        } else {
            centerY
        }
        path.lineTo(x, y)
        x += step
    }

    // Final point at endX
    val finalY = if (amplitude > 0.01f) {
        centerY + amplitude * sin(2.0 * PI * endX / wavelength + phase).toFloat()
    } else {
        centerY
    }
    path.lineTo(endX, finalY)

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}
