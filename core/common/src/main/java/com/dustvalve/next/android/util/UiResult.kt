package com.dustvalve.next.android.util

import androidx.annotation.StringRes
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of [runCatchingUi] / [runCatchingUiSync]: either the block's value or
 * a UI-ready error. [CancellationException] never becomes [Failure] - it is
 * rethrown so structured concurrency stays cooperative.
 */
sealed class UiResult<out T> {
    data class Success<T>(val value: T) : UiResult<T>()

    data class Failure(val error: UiText, val cause: Throwable) : UiResult<Nothing>()
}

/** Maps a failure's message to [UiText], falling back to a string resource. */
fun Throwable.toUiText(@StringRes fallback: Int): UiText = UiText.orResource(message, fallback)

/**
 * Runs [block] for ViewModel UI surfaces. Rethrows [CancellationException].
 * Every other [Exception] becomes [UiResult.Failure] with [toUiText].
 *
 * This is the intentional single [Exception] catch choke point for feature
 * ViewModels (avoids TooGenericExceptionCaught at each call site). Unexpected
 * Errors still propagate.
 *
 * [block] is not crossinline so callers may use labeled returns such as
 * `return@launch` from inside the lambda.
 */
suspend inline fun <T> runCatchingUi(
    @StringRes fallback: Int,
    block: suspend () -> T,
): UiResult<T> = try {
    UiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    UiResult.Failure(e.toUiText(fallback), e)
}

/** Non-suspend form of [runCatchingUi] for sync helpers (parsers, encoders). */
inline fun <T> runCatchingUiSync(
    @StringRes fallback: Int,
    block: () -> T,
): UiResult<T> = try {
    UiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    UiResult.Failure(e.toUiText(fallback), e)
}

/**
 * Best-effort side effects (preference writes, silent precache). Rethrows
 * [CancellationException]; other [Exception]s are passed to [onFailure]
 * (default: ignore).
 *
 * Lambdas are not crossinline so `return@launch` from call sites remains valid.
 */
suspend inline fun runCatchingUiIgnore(
    onFailure: (Throwable) -> Unit = {},
    block: suspend () -> Unit,
) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure(e)
    }
}

/** Non-suspend form of [runCatchingUiIgnore]. */
inline fun runCatchingUiIgnoreSync(
    onFailure: (Throwable) -> Unit = {},
    block: () -> Unit,
) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure(e)
    }
}

/**
 * Runs [block] and returns its value, or null when a non-cancellation
 * [Exception] is thrown. Rethrows [CancellationException].
 */
suspend inline fun <T> runCatchingUiOrNull(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

/** Non-suspend form of [runCatchingUiOrNull]. */
inline fun <T> runCatchingUiOrNullSync(block: () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

inline fun <T> UiResult<T>.onFailure(action: (error: UiText, cause: Throwable) -> Unit): UiResult<T> {
    if (this is UiResult.Failure) action(error, cause)
    return this
}

inline fun <T> UiResult<T>.onSuccess(action: (T) -> Unit): UiResult<T> {
    if (this is UiResult.Success) action(value)
    return this
}

fun <T> UiResult<T>.getOrNull(): T? = (this as? UiResult.Success)?.value
