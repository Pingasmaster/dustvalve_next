package com.dustvalve.next.android.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import com.dustvalve.next.android.ui.util.isNearListEnd

internal const val QUEUE_WINDOW_PAGE_SIZE = 25

/**
 * Windows the Up Next list to [QUEUE_WINDOW_PAGE_SIZE] rows and grows it
 * when the user scrolls near the end.
 *
 * Must be [remember]ed without a current-index key. Keying the state on
 * currentQueueIndex allocates a new window after playQueueEntry, while a
 * still-running load-more effect keeps writing to the abandoned one.
 */
internal class UpNextQueueWindow(private val pageSize: Int = QUEUE_WINDOW_PAGE_SIZE) {
    private val displayCountState = mutableIntStateOf(pageSize)
    private var syncedIndex: Int? = null

    val displayCount: Int
        get() = displayCountState.intValue

    fun syncIndex(currentQueueIndex: Int) {
        if (syncedIndex != currentQueueIndex) {
            syncedIndex = currentQueueIndex
            displayCountState.intValue = pageSize
        }
    }

    fun displayedCount(remainingCount: Int): Int = minOf(displayCount, remainingCount.coerceAtLeast(0))

    fun hasMore(remainingCount: Int): Boolean = displayCount < remainingCount

    fun expandIfNearEnd(lastVisibleIndex: Int?, totalItemsCount: Int, remainingCount: Int): Boolean {
        if (!isNearListEnd(lastVisibleIndex, totalItemsCount)) return false
        if (remainingCount <= 0 || !hasMore(remainingCount)) return false
        displayCountState.intValue = displayCount + pageSize
        return true
    }
}

@Composable
internal fun rememberUpNextQueueWindow(currentQueueIndex: Int): UpNextQueueWindow {
    val window = remember { UpNextQueueWindow() }
    window.syncIndex(currentQueueIndex)
    return window
}
