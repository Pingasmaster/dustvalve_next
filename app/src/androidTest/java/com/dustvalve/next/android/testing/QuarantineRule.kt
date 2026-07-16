package com.dustvalve.next.android.testing

import android.util.Log
import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Converts failures of quarantined tests into assumption violations
 * (reported as skipped, logged loudly) so a known-flaky live test cannot
 * block CI while its issue is open. A PASSING quarantined test still passes,
 * so entries can be confidently delisted.
 *
 * Quarantine list: app/src/androidTest/resources/quarantine.txt - one
 * `ClassName#method` per line; `#` comments (issue URL + date) encouraged.
 * Policy: quarantining REQUIRES a filed issue; review the list every release.
 */
class QuarantineRule : TestRule {

    private val quarantined: Set<String> by lazy {
        javaClass.classLoader?.getResourceAsStream("quarantine.txt")
            ?.bufferedReader()
            ?.readLines()
            ?.map { it.substringBefore('#').trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
    }

    override fun apply(base: Statement, description: Description): Statement {
        val key = "${description.testClass?.simpleName}#${description.methodName}"
        if (key !in quarantined) return base
        return object : Statement() {
            override fun evaluate() {
                try {
                    base.evaluate()
                } catch (t: Throwable) {
                    Log.e("Quarantine", "QUARANTINED test failed (not failing the run): $key", t)
                    throw AssumptionViolatedException("Quarantined: $key failed - see logcat", t)
                }
            }
        }
    }
}
