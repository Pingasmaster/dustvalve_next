package com.dustvalve.next.android.ui.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ListEndPaginationTest {

    @Test
    fun nearEndWhenLastVisibleIsWithinThreshold() {
        assertThat(isNearListEnd(lastVisibleIndex = 24, totalItemsCount = 27)).isTrue()
        assertThat(isNearListEnd(lastVisibleIndex = 8, totalItemsCount = 27)).isFalse()
        assertThat(isNearListEnd(lastVisibleIndex = null, totalItemsCount = 27)).isFalse()
        assertThat(isNearListEnd(lastVisibleIndex = 0, totalItemsCount = 0)).isFalse()
    }

    @Test
    fun searchDoesNotPaginateOnTheEmptyFirstComposition() {
        assertThat(
            shouldLoadMoreSearchPage(
                lastVisibleIndex = 0,
                totalItemsCount = 1,
                hasMore = true,
                isLoading = false,
                resultCount = 0,
            ),
        ).isFalse()
    }

    @Test
    fun searchPaginatesFromLiveFlagsOnceResultsExist() {
        assertThat(
            shouldLoadMoreSearchPage(
                lastVisibleIndex = 20,
                totalItemsCount = 22,
                hasMore = true,
                isLoading = false,
                resultCount = 20,
            ),
        ).isTrue()
    }

    @Test
    fun searchDoesNotPaginateWhileAPageIsInFlight() {
        assertThat(
            shouldLoadMoreSearchPage(
                lastVisibleIndex = 20,
                totalItemsCount = 22,
                hasMore = true,
                isLoading = true,
                resultCount = 20,
            ),
        ).isFalse()
    }

    @Test
    fun searchDoesNotPaginateWhenHasMoreIsFalse() {
        assertThat(
            shouldLoadMoreSearchPage(
                lastVisibleIndex = 20,
                totalItemsCount = 22,
                hasMore = false,
                isLoading = false,
                resultCount = 20,
            ),
        ).isFalse()
    }
}
