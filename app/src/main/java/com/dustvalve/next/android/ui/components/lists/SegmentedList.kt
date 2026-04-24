package com.dustvalve.next.android.ui.components.lists

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.dustvalve.next.android.ui.theme.segmentedItemShape

/**
 * Segmented list item wrapper (Material 3 Expressive "segmented" list variant).
 *
 * Wraps `content` in a `Surface` whose shape adapts to the item's position in the list
 * (`segmentedItemShape(index, count)`) with a small 1-dp gap between rows. When
 * `isDragging` is true, the shape morphs to `shapes.large` and the container colour
 * lifts from `surfaceContainerLow` to `surfaceContainer`, using `motionScheme` springs.
 *
 * Use this for content/collection lists (tracks, playlists, albums). Do not use for
 * utility lists (search history, bottom-sheet menus, overflow popovers) — those use
 * the flat standard variant (`ListItem` directly).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SegmentedListItem(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    contentPadding: PaddingValues = defaultSegmentedPadding(index, count),
    shadowElevationWhenLifted: Dp = 2.dp,
    content: @Composable () -> Unit,
) {
    val shape: Shape = if (isDragging) MaterialTheme.shapes.large
        else segmentedItemShape(index, count)

    val containerColor by animateColorAsState(
        targetValue = if (isDragging) MaterialTheme.colorScheme.surfaceContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "segmentedContainerColor",
    )
    val elevation by animateDpAsState(
        targetValue = if (isDragging) shadowElevationWhenLifted else 0.dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "segmentedElevation",
    )

    Surface(
        modifier = modifier.padding(contentPadding),
        shape = shape,
        color = containerColor,
        shadowElevation = elevation,
    ) {
        content()
    }
}

/**
 * Same as [SegmentedListItem] but lets the caller override the container colour
 * (used by now-playing tinting).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SegmentedListItem(
    index: Int,
    count: Int,
    containerColor: Color,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    contentPadding: PaddingValues = defaultSegmentedPadding(index, count),
    shadowElevationWhenLifted: Dp = 2.dp,
    content: @Composable () -> Unit,
) {
    val shape: Shape = if (isDragging) MaterialTheme.shapes.large
        else segmentedItemShape(index, count)

    val elevation by animateDpAsState(
        targetValue = if (isDragging) shadowElevationWhenLifted else 0.dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "segmentedElevation",
    )

    Surface(
        modifier = modifier.padding(contentPadding),
        shape = shape,
        color = containerColor,
        shadowElevation = elevation,
    ) {
        content()
    }
}

private fun defaultSegmentedPadding(index: Int, count: Int): PaddingValues =
    PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = if (index == 0) 8.dp else 1.dp,
        bottom = if (index == count - 1) 0.dp else 1.dp,
    )
