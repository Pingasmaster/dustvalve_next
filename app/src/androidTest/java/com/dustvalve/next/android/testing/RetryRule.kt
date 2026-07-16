package com.dustvalve.next.android.testing

import android.util.Log
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Retries tests annotated (at method or class level) with [LiveNetwork] up to
 * [maxExtraAttempts] extra times with a small backoff. Hermetic tests never
 * retry: a deterministic failure must fail loudly.
 */
class RetryRule(private val maxExtraAttempts: Int = 2, private val backoffMs: Long = 5_000) : TestRule {

    override fun apply(base: Statement, description: Description): Statement {
        val isLive = description.getAnnotation(LiveNetwork::class.java) != null ||
            description.testClass?.getAnnotation(LiveNetwork::class.java) != null
        if (!isLive) return base

        return object : Statement() {
            override fun evaluate() {
                var lastFailure: Throwable? = null
                for (attempt in 0..maxExtraAttempts) {
                    try {
                        base.evaluate()
                        return
                    } catch (t: Throwable) {
                        if (t is org.junit.AssumptionViolatedException) throw t
                        lastFailure = t
                        Log.w(
                            "LiveRetry",
                            "${description.displayName} failed attempt ${attempt + 1}/${maxExtraAttempts + 1}",
                            t,
                        )
                        if (attempt < maxExtraAttempts) Thread.sleep(backoffMs)
                    }
                }
                throw lastFailure!!
            }
        }
    }
}
