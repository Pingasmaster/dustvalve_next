package com.dustvalve.next.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FastScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val totalItems = listState.layoutInfo.totalItemsCount
    if (totalItems <= 0) return

    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var trackHeight by remember { mutableFloatStateOf(0f) }

    val firstVisible = listState.firstVisibleItemIndex
    val scrollFraction = if (totalItems > 1) {
        firstVisible.toFloat() / (totalItems - 1).toFloat()
    } else 0f

    // Auto-hide
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(firstVisible, isDragging) {
        visible = true
        if (!isDragging) {
            delay(1500)
            visible = false
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "scrollbarAlpha",
    )

    val thumbFraction = (1f / totalItems.toFloat()).coerceIn(0.05f, 0.3f)
    val thumbTopFraction = scrollFraction * (1f - thumbFraction)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(16.dp)
            .graphicsLayer { this.alpha = alpha }
            .onSizeChanged { trackHeight = it.height.toFloat() }
            .pointerInput(totalItems) {
                detectVerticalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val y = change.position.y
                        val fraction = (y / trackHeight).coerceIn(0f, 1f)
                        val targetIndex = (fraction * (totalItems - 1)).roundToInt()
                        scope.launch { listState.scrollToItem(targetIndex) }
                    },
                )
            },
    ) {
        // Track background
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .padding(start = 8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)),
        )

        // Thumb
        Box(
            modifier = Modifier
                .padding(start = 6.dp)
                .width(6.dp)
                .fillMaxHeight(thumbFraction)
                .graphicsLayer {
                    translationY = thumbTopFraction * trackHeight
                }
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
        )
    }
}
