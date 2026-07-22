package com.dustvalve.next.android.ui.components

import android.graphics.Matrix
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import kotlinx.coroutines.delay

/**
 * A [Shape] that morphs between two RoundedPolygon states.
 *
 * Single shared implementation for the player art (FullPlayer's
 * square-to-heart double-tap morph, MiniPlayer's square-to-cookie playback
 * morph) and the detail-screen hero double-tap heart.
 *
 * Implements structural equality so that recompositions passing an
 * unchanged (morph, progress) pair don't force Compose to re-run
 * [createOutline] - the morph tessellation (toPath + calculateBounds +
 * transform) is comparatively expensive and used to run on every player
 * position tick because each recomposition allocated an unequal instance.
 */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress = progress)

        // Use graphics-shapes native bounds calculation
        val bounds = morph.calculateBounds()
        val pathWidth = bounds[2] - bounds[0] // right - left
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

    override fun equals(other: Any?): Boolean = other is MorphShape && other.morph === morph && other.progress == progress

    override fun hashCode(): Int = 31 * System.identityHashCode(morph) + progress.hashCode()
}

/**
 * State holder for the double-tap "art turns into a heart" animation.
 *
 * This is the exact motion the full player uses on its album art: the
 * artwork's clip morphs Square -> Heart with the default spatial spring,
 * holds the heart for [HOLD_MILLIS], then morphs back with the slow
 * spatial spring. Detail-screen heroes (album / artist / playlist /
 * collection) share it so a double-tap heart feels identical everywhere.
 */
@Stable
class HeartMorphState internal constructor(
    private val morph: Morph,
    private val inSpec: AnimationSpec<Float>,
    private val outSpec: AnimationSpec<Float>,
) {
    private val progressAnim = Animatable(0f)

    /** 0f at rest, 1f at the full heart. */
    val progress: Float get() = progressAnim.value

    /**
     * Clip shape at the current morph progress. Read this from a scope
     * that may recompose during the animation (the heroes read it inside
     * their image modifier); equality on [MorphShape] keeps unchanged
     * frames from re-tessellating.
     */
    val shape: Shape get() = MorphShape(morph, progressAnim.value)

    /** Run the full in -> hold -> out sequence (same timings as FullPlayer). */
    suspend fun play() {
        progressAnim.animateTo(1f, inSpec)
        delay(HOLD_MILLIS)
        progressAnim.animateTo(0f, outSpec)
    }

    companion object {
        const val HOLD_MILLIS = 1000L
    }
}

/** Remember a [HeartMorphState] wired to the M3 expressive motion scheme. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberHeartMorphState(): HeartMorphState {
    val squarePolygon = remember { MaterialShapes.Square }
    val heartPolygon = remember { MaterialShapes.Heart }
    val morph = remember(squarePolygon, heartPolygon) { Morph(squarePolygon, heartPolygon) }
    val inSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val outSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    return remember(morph, inSpec, outSpec) { HeartMorphState(morph, inSpec, outSpec) }
}
