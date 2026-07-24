package com.dustvalve.next.android.data.util

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block], returning [default] when a Storage Access Framework / file
 * call fails with one of the exception types those APIs realistically raise:
 * a stream error ([IOException]), a revoked or missing permission
 * ([SecurityException]), a bad document URI or argument
 * ([IllegalArgumentException]), a provider that refuses the operation
 * ([UnsupportedOperationException]) or otherwise reports it illegal
 * ([IllegalStateException]).
 *
 * Coroutine cancellation is never swallowed: [CancellationException] is a
 * subtype of [IllegalStateException], so it is rethrown explicitly to keep
 * structured concurrency cooperative. Any other (unexpected) exception is left
 * to propagate rather than being hidden.
 */
internal inline fun <T> orOnStorageFailure(default: T, block: () -> T): T = try {
    block()
} catch (ignored: IOException) {
    default
} catch (ignored: SecurityException) {
    default
} catch (ignored: IllegalArgumentException) {
    default
} catch (ignored: UnsupportedOperationException) {
    default
} catch (e: IllegalStateException) {
    if (e is CancellationException) throw e
    default
}

/**
 * Unit-returning form of [orOnStorageFailure] for best-effort side effects
 * (delete a stale temp file, quarantine a corrupt snapshot, ...) whose result
 * is intentionally ignored. Cancellation still propagates.
 */
internal inline fun ignoringStorageFailures(block: () -> Unit) {
    orOnStorageFailure(Unit) { block() }
}
