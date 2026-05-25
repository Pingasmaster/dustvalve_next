package com.dustvalve.next.android.di.qualifiers

import jakarta.inject.Qualifier
import kotlin.annotation.AnnotationRetention.RUNTIME

/**
 * Hilt qualifier for injectable CoroutineDispatcher instances. NowInAndroid
 * pattern: one Qualifier parameterised by an enum, not three sibling
 * annotations — scales when a new dispatcher type is added without forcing
 * a new annotation class.
 *
 * Main is intentionally absent: it has only one sensible value, and tests
 * substitute it globally via Dispatchers.setMain(testDispatcher) /
 * MainDispatcherRule, so qualifying it would add ceremony for no benefit.
 */
@Qualifier
@Retention(RUNTIME)
annotation class Dispatcher(val dispatcher: AppDispatchers)

enum class AppDispatchers {
    Default,
    IO,
}
