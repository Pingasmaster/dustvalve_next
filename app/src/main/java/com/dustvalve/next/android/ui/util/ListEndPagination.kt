package com.dustvalve.next.android.ui.util

internal const val LIST_END_LOAD_THRESHOLD = 3

/** True when the last visible row is within [LIST_END_LOAD_THRESHOLD] of the list end. */
internal fun isNearListEnd(lastVisibleIndex: Int?, totalItemsCount: Int): Boolean = lastVisibleIndex != null &&
    totalItemsCount > 0 &&
    lastVisibleIndex >= totalItemsCount - LIST_END_LOAD_THRESHOLD

/**
 * Search-result infinite scroll. Callers must pass the *current* ViewModel
 * flags each emission -- capturing the first composition's hasMore/results
 * freezes pagination after the empty initial frame.
 */
internal fun shouldLoadMoreSearchPage(
    lastVisibleIndex: Int?,
    totalItemsCount: Int,
    hasMore: Boolean,
    isLoading: Boolean,
    resultCount: Int,
): Boolean = isNearListEnd(lastVisibleIndex, totalItemsCount) &&
    hasMore &&
    !isLoading &&
    resultCount > 0
