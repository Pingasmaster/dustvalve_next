package com.dustvalve.next.android.ui.components.lists

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * LazyColumn wrapper for drag-to-reorder lists.
 *
 * Manages an internal `mutableStateListOf` mirror of [items] so that in-flight swaps
 * animate immediately. The canonical `onMove(fromIndex, toIndex)` is dispatched
 * exactly once per drag gesture, on drop. Haptic feedback fires on drag start
 * (LongPress) and drag end (GestureEnd); both are routed through the drag-handle
 * modifier supplied to [itemContent].
 *
 * Callers receive `isDragging` (true while this row is the one being dragged) and
 * `dragHandleModifier` — attach it to whichever child should grab-scroll (typically
 * the trailing drag handle icon).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T : Any> ReorderableMusicList(
    items: List<T>,
    keyFn: (T) -> Any,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(bottom = 10.dp),
    header: (LazyListScope.() -> Unit)? = null,
    footer: (LazyListScope.() -> Unit)? = null,
    itemContent: @Composable (
        index: Int,
        item: T,
        isDragging: Boolean,
        dragHandleModifier: Modifier,
    ) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current

    val displayList = remember { mutableStateListOf<T>() }
    var dragStartIndex by remember { mutableIntStateOf(-1) }

    // Keep displayList in sync with upstream when not actively dragging.
    LaunchedEffect(items) {
        if (dragStartIndex == -1) {
            displayList.clear()
            displayList.addAll(items)
        }
    }

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = displayList.indexOfFirst { keyFn(it) == from.key }
        val toIdx = displayList.indexOfFirst { keyFn(it) == to.key }
        if (fromIdx in displayList.indices && toIdx in displayList.indices && fromIdx != toIdx) {
            val moved = displayList.removeAt(fromIdx)
            displayList.add(toIdx, moved)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        header?.invoke(this)

        items(
            items = displayList,
            key = keyFn,
        ) { item ->
            val index = displayList.indexOf(item)
            ReorderableItem(
                state = reorderableState,
                key = keyFn(item),
            ) { isDragging ->
                val handleModifier = Modifier.longPressDraggableHandle(
                    onDragStarted = {
                        dragStartIndex = displayList.indexOfFirst { keyFn(it) == keyFn(item) }
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragStopped = {
                        val start = dragStartIndex
                        val end = displayList.indexOfFirst { keyFn(it) == keyFn(item) }
                        dragStartIndex = -1
                        if (start >= 0 && end >= 0 && start != end) {
                            onMove(start, end)
                        }
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    },
                )
                itemContent(index, item, isDragging, handleModifier)
            }
        }

        footer?.invoke(this)
    }
}
