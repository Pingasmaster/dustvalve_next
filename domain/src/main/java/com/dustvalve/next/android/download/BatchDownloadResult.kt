package com.dustvalve.next.android.download

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of a batch run by [downloadEachDeferringFailures].
 *
 * [attempted] counts only the items handed to the runner - callers filter
 * out what is already downloaded before calling, so "3 of 40" never counts
 * tracks that were skipped because they were already on disk.
 */
data class BatchDownloadResult<T>(
    val attempted: Int,
    val downloaded: Int,
    val unavailable: List<T>,
    /**
     * Error from the first item that failed *both* attempts, or null when
     * nothing was permanently lost. A first-pass error whose retry succeeded
     * is deliberately dropped: it describes a track the user did get.
     */
    val error: Throwable? = null,
) {
    /** True when at least one item failed twice and was given up on. */
    val hasUnavailable: Boolean get() = unavailable.isNotEmpty()

    /** True when every attempted item failed twice. */
    val allFailed: Boolean get() = attempted > 0 && downloaded == 0
}

/**
 * Runs [download] over [items], never letting one bad item abort the batch.
 *
 * A failing item is skipped **immediately** (the batch moves straight on to
 * the next item) and re-queued at the **very end** of the run for one final
 * attempt; an item that fails that second attempt is given up on and reported
 * in [BatchDownloadResult.unavailable].
 *
 * This exists because a single undownloadable track used to kill a whole
 * artist/album/playlist download. The common case is an age-restricted
 * YouTube video: `/player` answers `playabilityStatus=LOGIN_REQUIRED` with no
 * audio `adaptiveFormats`, the parser throws [IllegalStateException], and
 * every caller above it bailed out of its loop. The deferred retry is what
 * makes the transient half of that population (an expired stream URL, a
 * dropped connection) still land, without stalling the tracks behind it.
 *
 * Only the failure types download work actually produces are absorbed -
 * [IOException] (transport / storage), [IllegalStateException] (the YouTube
 * and Bandcamp parsers) and [IllegalArgumentException] (malformed responses),
 * matching what `DownloadController.runWork` treats as a work failure.
 * [CancellationException] is rethrown untouched, so structured concurrency
 * and the pause path (`PausedDownloadException`) keep working.
 *
 * [onAttemptFailed] fires for every absorbed failure, on both passes - it
 * exists so callers can keep logging what went by; the returned result is the
 * thing to make decisions from.
 */
suspend fun <T> downloadEachDeferringFailures(
    items: List<T>,
    onAttemptFailed: (T, Throwable) -> Unit = { _, _ -> },
    download: suspend (T) -> Unit,
): BatchDownloadResult<T> {
    if (items.isEmpty()) return BatchDownloadResult(attempted = 0, downloaded = 0, unavailable = emptyList())

    var downloaded = 0
    val deferred = mutableListOf<T>()
    for (item in items) {
        val error = attemptDownload(item, download)
        if (error == null) {
            downloaded++
        } else {
            onAttemptFailed(item, error)
            deferred += item
        }
    }

    // Second and last chance, in the order the items originally failed.
    val unavailable = mutableListOf<T>()
    var firstError: Throwable? = null
    for (item in deferred) {
        val error = attemptDownload(item, download)
        if (error == null) {
            downloaded++
        } else {
            onAttemptFailed(item, error)
            unavailable += item
            if (firstError == null) firstError = error
        }
    }

    return BatchDownloadResult(
        attempted = items.size,
        downloaded = downloaded,
        unavailable = unavailable,
        error = firstError,
    )
}

/**
 * Returns null when [download] succeeded, otherwise the absorbed failure.
 *
 * The [CancellationException] clause MUST stay first: it is a typealias for
 * `java.util.concurrent.CancellationException`, which extends
 * [IllegalStateException] - reorder these and every cancellation (including
 * `PausedDownloadException`) gets swallowed and retried instead of unwinding.
 */
private suspend fun <T> attemptDownload(item: T, download: suspend (T) -> Unit): Throwable? = try {
    download(item)
    null
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    e
} catch (e: IllegalStateException) {
    e
} catch (e: IllegalArgumentException) {
    e
}
