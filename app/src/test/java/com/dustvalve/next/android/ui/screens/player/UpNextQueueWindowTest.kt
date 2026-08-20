package com.dustvalve.next.android.ui.screens.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Catalog: local-queue-window-load-more-after-click
 *
 * Clicking an Up Next row changes currentQueueIndex. The window must reset
 * to the first page and still accept later near-end expands on the *same*
 * instance -- replacing the instance is what used to leave the loading
 * footer stuck on screen.
 */
class UpNextQueueWindowTest {

    @Test
    fun firstPageIsTwentyFive() {
        val window = UpNextQueueWindow()
        window.syncIndex(0)
        assertThat(window.displayCount).isEqualTo(QUEUE_WINDOW_PAGE_SIZE)
        assertThat(window.displayedCount(remainingCount = 200)).isEqualTo(QUEUE_WINDOW_PAGE_SIZE)
        assertThat(window.hasMore(remainingCount = 200)).isTrue()
    }

    @Test
    fun nearEndScrollExpandsByOnePage() {
        val window = UpNextQueueWindow()
        window.syncIndex(0)
        val expanded = window.expandIfNearEnd(
            lastVisibleIndex = 24,
            totalItemsCount = 27,
            remainingCount = 200,
        )
        assertThat(expanded).isTrue()
        assertThat(window.displayCount).isEqualTo(QUEUE_WINDOW_PAGE_SIZE * 2)
    }

    @Test
    fun notNearEndDoesNotExpand() {
        val window = UpNextQueueWindow()
        window.syncIndex(0)
        val expanded = window.expandIfNearEnd(
            lastVisibleIndex = 8,
            totalItemsCount = 27,
            remainingCount = 200,
        )
        assertThat(expanded).isFalse()
        assertThat(window.displayCount).isEqualTo(QUEUE_WINDOW_PAGE_SIZE)
    }

    @Test
    fun doesNotExpandWhenEveryRemainingRowIsAlreadyShown() {
        val window = UpNextQueueWindow()
        window.syncIndex(0)
        val expanded = window.expandIfNearEnd(
            lastVisibleIndex = 9,
            totalItemsCount = 11,
            remainingCount = 10,
        )
        assertThat(expanded).isFalse()
        assertThat(window.displayCount).isEqualTo(QUEUE_WINDOW_PAGE_SIZE)
        assertThat(window.hasMore(remainingCount = 10)).isFalse()
    }

    @Test
    fun footerStillInViewAfterExpandLoadsAnotherPage() {
        val window = UpNextQueueWindow()
        window.syncIndex(0)
        window.expandIfNearEnd(lastVisibleIndex = 24, totalItemsCount = 27, remainingCount = 200)
        val expandedAgain = window.expandIfNearEnd(
            lastVisibleIndex = 49,
            totalItemsCount = 52,
            remainingCount = 200,
        )
        assertThat(expandedAgain).isTrue()
        assertThat(window.displayCount).isEqualTo(QUEUE_WINDOW_PAGE_SIZE * 3)
    }

    @Test
    fun clickingALaterTrackResetsThenNearEndStillExpandsTheSameWindow() {
        val window = UpNextQueueWindow()
        window.syncIndex(0)
        window.expandIfNearEnd(lastVisibleIndex = 24, totalItemsCount = 27, remainingCount = 200)
        assertThat(window.displayCount).isEqualTo(QUEUE_WINDOW_PAGE_SIZE * 2)

        // playQueueEntry on an Up Next row -- same object the load-more
        // effect still holds.
        window.syncIndex(currentQueueIndex = 8)
        assertThat(window.displayCount).isEqualTo(QUEUE_WINDOW_PAGE_SIZE)

        val remainingAfterClick = 191
        val expanded = window.expandIfNearEnd(
            lastVisibleIndex = 24,
            totalItemsCount = 27,
            remainingCount = remainingAfterClick,
        )
        assertThat(expanded).isTrue()
        assertThat(window.displayCount).isEqualTo(QUEUE_WINDOW_PAGE_SIZE * 2)
        assertThat(window.hasMore(remainingAfterClick)).isTrue()
    }

    @Test
    fun sameIndexDoesNotResetAnAlreadyExpandedWindow() {
        val window = UpNextQueueWindow()
        window.syncIndex(0)
        window.expandIfNearEnd(lastVisibleIndex = 24, totalItemsCount = 27, remainingCount = 200)
        window.syncIndex(0)
        assertThat(window.displayCount).isEqualTo(QUEUE_WINDOW_PAGE_SIZE * 2)
    }
}
