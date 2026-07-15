package com.dustvalve.next.android.di.qualifiers

import jakarta.inject.Qualifier
import kotlin.annotation.AnnotationRetention.RUNTIME

/**
 * Hilt qualifier for the app-lifetime [kotlinx.coroutines.CoroutineScope].
 * Inject this instead of constructing `CoroutineScope(SupervisorJob() + ...)`
 * inline: it keeps singletons testable (tests substitute a TestScope) and
 * gives all app-lifetime work one supervised root job.
 */
@Qualifier
@Retention(RUNTIME)
annotation class ApplicationScope
