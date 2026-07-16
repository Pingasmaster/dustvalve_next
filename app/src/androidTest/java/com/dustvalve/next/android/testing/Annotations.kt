package com.dustvalve.next.android.testing

/**
 * Fast on-device checks that gate everything else in CI: app boots, local
 * playback advances past 0:00, provider screens open without crashing.
 * Filter: -Pandroid.testInstrumentationRunnerArguments.annotation=
 *   com.dustvalve.next.android.testing.SmokeTest
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class SmokeTest

/**
 * Tests that talk to the real Bandcamp / YouTube services. They run in CI
 * as a SEPARATE invocation from the hermetic pass so a service outage never
 * masks a hermetic regression. RetryRule retries these (and only these) up
 * to 2 extra times.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class LiveNetwork
