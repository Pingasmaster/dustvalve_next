@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.crash

import android.app.ApplicationExitInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Covers the two hard requirements of the crash prompt:
 *
 *  1. A crash from the previous run IS surfaced (marker-file path).
 *  2. The user force-closing the app is NEVER treated as a crash
 *     (reason filter), and nothing is stored or shared automatically.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class CrashReportManagerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var manager: CrashReportManager

    private val crashDir get() = File(context.filesDir, "crash")
    private val pendingFile get() = File(crashDir, "pending_crash.txt")

    @Before fun setUp() {
        crashDir.deleteRecursively()
        manager = CrashReportManager(context, CoroutineScope(dispatcher), dispatcher)
    }

    @Test fun `uncaught exception writes the pending crash log`() {
        var chained = false
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> chained = true }
        manager.install()

        Thread.getDefaultUncaughtExceptionHandler()!!
            .uncaughtException(Thread.currentThread(), IllegalStateException("boom marker"))

        assertThat(pendingFile.exists()).isTrue()
        val text = pendingFile.readText()
        assertThat(text).contains("boom marker")
        assertThat(text).contains("IllegalStateException")
        assertThat(text).contains("Dustvalve Next crash log")
        // The platform handler must still run so the OS crash flow is intact.
        assertThat(chained).isTrue()
    }

    @Test fun `cold start with a pending log surfaces the prompt`() = runTest(dispatcher) {
        crashDir.mkdirs()
        pendingFile.writeText("previous crash stack trace")

        manager.checkOnColdStart()

        val state = manager.state.value
        assertThat(state).isInstanceOf(CrashReportManager.PromptState.Pending::class.java)
        val report = (state as CrashReportManager.PromptState.Pending).report
        assertThat(report.logText).contains("previous crash stack trace")
    }

    @Test fun `cold start with nothing pending stays hidden`() = runTest(dispatcher) {
        manager.checkOnColdStart()

        assertThat(manager.state.value).isEqualTo(CrashReportManager.PromptState.Hidden)
    }

    @Test fun `dismiss hides the prompt and deletes the stored log`() = runTest(dispatcher) {
        crashDir.mkdirs()
        pendingFile.writeText("stack")
        manager.checkOnColdStart()
        assertThat(manager.state.value).isInstanceOf(CrashReportManager.PromptState.Pending::class.java)

        manager.dismiss()

        assertThat(manager.state.value).isEqualTo(CrashReportManager.PromptState.Hidden)
        assertThat(pendingFile.exists()).isFalse()
    }

    @Test fun `crash ANR and native crash are reportable`() {
        assertThat(CrashReportManager.isReportableReason(ApplicationExitInfo.REASON_CRASH)).isTrue()
        assertThat(CrashReportManager.isReportableReason(ApplicationExitInfo.REASON_CRASH_NATIVE)).isTrue()
        assertThat(CrashReportManager.isReportableReason(ApplicationExitInfo.REASON_ANR)).isTrue()
    }

    @Test fun `user force close and routine exits are never reportable`() {
        assertThat(CrashReportManager.isReportableReason(ApplicationExitInfo.REASON_USER_REQUESTED)).isFalse()
        assertThat(CrashReportManager.isReportableReason(ApplicationExitInfo.REASON_USER_STOPPED)).isFalse()
        assertThat(CrashReportManager.isReportableReason(ApplicationExitInfo.REASON_EXIT_SELF)).isFalse()
        assertThat(CrashReportManager.isReportableReason(ApplicationExitInfo.REASON_LOW_MEMORY)).isFalse()
        assertThat(CrashReportManager.isReportableReason(ApplicationExitInfo.REASON_SIGNALED)).isFalse()
        assertThat(CrashReportManager.isReportableReason(ApplicationExitInfo.REASON_OTHER)).isFalse()
    }

    @Test fun `issue url points at the repo and encodes the log`() {
        val url = CrashReportManager.buildIssueUrl("stack trace with spaces & symbols")

        assertThat(url).startsWith("https://github.com/Pingasmaster/dustvalve_next/issues/new?title=")
        assertThat(url).contains("&body=")
        assertThat(url).doesNotContain("stack trace with spaces")
        assertThat(url).contains("stack+trace+with+spaces")
    }

    @Test fun `issue url truncates very long logs`() {
        val url = CrashReportManager.buildIssueUrl("x".repeat(50_000))

        assertThat(url.length).isLessThan(20_000)
        assertThat(url).contains(java.net.URLEncoder.encode("[log truncated", "UTF-8"))
    }
}
