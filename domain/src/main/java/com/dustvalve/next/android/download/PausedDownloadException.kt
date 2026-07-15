package com.dustvalve.next.android.download

import kotlin.coroutines.cancellation.CancellationException

/**
 * Cancellation used by [DownloadController.pause] to stop the in-flight
 * transfer **without** discarding the partial `.tmp` file, so [resume] can
 * continue from the last-written offset via an HTTP `Range` request.
 *
 * It subclasses [CancellationException] so structured concurrency unwinds
 * normally and every existing `catch (e: CancellationException) { throw e }`
 * site keeps rethrowing it. The repository's `.tmp` delete sites distinguish a
 * pause from a real failure/cancel via [isPauseCancellation] (which walks the
 * cause chain, because cancelling a parent job wraps this in a
 * `JobCancellationException` for child coroutines).
 */
class PausedDownloadException : CancellationException("download paused")

/** True if [this] is, or was caused by, a [PausedDownloadException]. */
fun Throwable.isPauseCancellation(): Boolean {
    var t: Throwable? = this
    while (t != null) {
        if (t is PausedDownloadException) return true
        t = t.cause
    }
    return false
}
