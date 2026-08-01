package com.dustvalve.next.android.data.util

import kotlinx.serialization.SerializationException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block], returning [default] when a remote fetch/parse fails with the
 * exception types those paths realistically raise: transport errors
 * ([IOException]), malformed payloads ([SerializationException]), or bad
 * URLs/arguments ([IllegalArgumentException]).
 *
 * Coroutine cancellation is never swallowed: [CancellationException] is
 * rethrown so structured concurrency stays cooperative. Unexpected exceptions
 * propagate instead of being hidden behind a soft fallback.
 */
internal inline fun <T> orOnRemoteFailure(default: T, block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: IOException) {
    default
} catch (_: SerializationException) {
    default
} catch (_: IllegalArgumentException) {
    default
}

/**
 * Unit-returning form of [orOnRemoteFailure] for best-effort side effects.
 * Cancellation still propagates.
 */
internal inline fun ignoringRemoteFailures(block: () -> Unit) {
    orOnRemoteFailure(Unit) { block() }
}
