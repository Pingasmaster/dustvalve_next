package com.dustvalve.next.android.crash

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import com.dustvalve.next.android.BuildConfig
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.ApplicationScope
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import com.dustvalve.next.android.update.AppUpdateService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privacy-first crash reporting: everything stays on-device until the user
 * explicitly chooses to share. No analytics SDK, no third-party crash
 * framework, no automatic upload of any kind.
 *
 * Two independent detectors feed the same prompt:
 *
 *  1. [install] plants an [Thread.UncaughtExceptionHandler] that writes the
 *     full Java stack trace to filesDir/crash/pending_crash.txt before
 *     delegating to the platform handler (so the OS crash flow is untouched).
 *  2. [checkOnColdStart] additionally queries [ApplicationExitInfo] for
 *     process deaths the in-process handler cannot see (native crashes, ANRs
 *     and hard freezes the user killed from the ANR dialog). Only
 *     REASON_CRASH / REASON_CRASH_NATIVE / REASON_ANR count -
 *     REASON_USER_REQUESTED (the user force-closing the app from Settings or
 *     Recents) and routine kills (LMK, EXIT_SELF, permission changes) never
 *     trigger the prompt.
 *
 * A timestamp watermark under filesDir/crash/ prevents the same exit record
 * from prompting twice. [MainActivity] hosts the prompt UI; the two share
 * actions (system share sheet, GitHub new-issue page) are the only paths a
 * log can leave the device, and both are explicit user gestures.
 */
@Singleton
class CrashReportManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    sealed interface PromptState {
        data object Hidden : PromptState
        data class Pending(val report: CrashReport) : PromptState
    }

    /** [logText] is exactly what "Share crash log" exports - nothing more. */
    data class CrashReport(val logText: String)

    private val _state = MutableStateFlow<PromptState>(PromptState.Hidden)
    val state: StateFlow<PromptState> = _state.asStateFlow()

    private val crashDir: File get() = File(context.filesDir, CRASH_DIR)
    private val pendingFile: File get() = File(crashDir, PENDING_FILE)
    private val watermarkFile: File get() = File(crashDir, WATERMARK_FILE)

    /**
     * Plant the uncaught-exception hook. Called once from
     * Application.onCreate, before any other init that could throw.
     */
    fun install() {
        val platformHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writePendingCrash(thread, throwable)
            platformHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * One-shot per process: look for a crash marker from the previous run
     * and for reportable [ApplicationExitInfo] records, then surface the
     * prompt. Runs off the main thread; cold-start cost is one stat().
     */
    fun checkOnColdStart() {
        appScope.launch(ioDispatcher) {
            val report = collectPendingReport()
            if (report != null) _state.value = PromptState.Pending(report)
        }
    }

    /** "Not now" - drop the stored log and never ask about this crash again. */
    fun dismiss() {
        _state.value = PromptState.Hidden
        appScope.launch(ioDispatcher) {
            pendingFile.delete()
        }
    }

    /**
     * Hand the log to the system share sheet. The user picks the target
     * (mail, messenger, file save...); nothing is pre-selected and nothing
     * leaves the device without this explicit gesture.
     */
    fun sharePendingLog(activityContext: Context) {
        val pending = (_state.value as? PromptState.Pending) ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, SHARE_SUBJECT)
            putExtra(Intent.EXTRA_TEXT, pending.report.logText)
        }
        activityContext.startActivity(Intent.createChooser(send, null))
        consumePendingFile()
    }

    /**
     * Open the repo's new-issue page in the browser with a pre-filled title
     * and a truncated log excerpt. GitHub caps URL length, so the body keeps
     * only the head of the log; the full text is available via the share
     * action. Opening the page is not submitting - the user still reviews
     * and confirms on github.com.
     */
    fun openGitHubIssue(activityContext: Context) {
        val pending = (_state.value as? PromptState.Pending) ?: return
        val view = Intent(Intent.ACTION_VIEW, buildIssueUrl(pending.report.logText).toUri())
        try {
            activityContext.startActivity(view)
            consumePendingFile()
        } catch (_: android.content.ActivityNotFoundException) {
            // No browser/URL handler on this device. The one button meant to
            // REPORT a crash must never itself crash the app. The pending
            // file is kept: nothing was reported.
        }
    }

    /**
     * The user has acted on the report (share sheet or GitHub issue page):
     * delete the on-disk log so the next launch does not re-prompt for the
     * same crash. The in-memory [PromptState.Pending] is deliberately kept
     * so the sheet stays usable for the rest of this session (e.g. share
     * AND open an issue); nothing new is persisted.
     */
    private fun consumePendingFile() {
        appScope.launch(ioDispatcher) {
            pendingFile.delete()
        }
    }

    // ---------------------------------------------------------------- impl

    // The process is already dying from `throwable`; any secondary failure
    // (disk full, sealed filesystem...) must be swallowed, never rethrown.
    @Suppress("TooGenericExceptionCaught")
    private fun writePendingCrash(thread: Thread, throwable: Throwable) {
        // Deliberately no coroutines here: the process is dying and this is
        // the only chance to persist the trace. Keep it plain and blocking.
        try {
            crashDir.mkdirs()
            val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }
            pendingFile.writeText(
                buildString {
                    appendLine(logHeader())
                    appendLine("thread=${thread.name}")
                    appendLine()
                    append(stackTrace)
                },
            )
        } catch (t: Throwable) {
            // Never mask the original crash with a reporting failure.
            Log.w(TAG, "failed to persist crash log", t)
        }
    }

    private fun collectPendingReport(): CrashReport? {
        val markerText = try {
            if (pendingFile.exists()) pendingFile.readText() else null
        } catch (_: IOException) {
            null
        }
        val exitLines = collectReportableExits()
        return when {
            markerText != null -> {
                // The JVM crash also leaves an exit record; the watermark
                // advance above keeps it from re-prompting on the next run.
                CrashReport(markerText)
            }

            exitLines.isNotEmpty() -> {
                // Native crash / ANR / frozen-then-killed-by-system: no Java
                // stack trace exists, so ship the OS exit records instead.
                CrashReport(
                    buildString {
                        appendLine(logHeader())
                        appendLine()
                        appendLine("No in-app stack trace was captured; the OS reported:")
                        exitLines.forEach { appendLine(it) }
                    },
                )
            }

            else -> null
        }
    }

    /**
     * Exit records that indicate an app problem, newer than the watermark.
     * Everything user-initiated or routine is filtered out so force-closing
     * the app never nags on the next launch.
     */
    @Suppress("TooGenericExceptionCaught") // Robolectric NPE catch - see below.
    private fun collectReportableExits(): List<String> = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val watermark = try {
            watermarkFile.readText().trim().toLong()
        } catch (_: IOException) {
            0L
        } catch (_: NumberFormatException) {
            0L
        }
        val records = am.getHistoricalProcessExitReasons(context.packageName, PID_UNQUERIED, MAX_EXIT_RECORDS)
        val newest = records.maxOfOrNull { it.timestamp } ?: 0L
        if (newest > watermark) {
            try {
                crashDir.mkdirs()
                watermarkFile.writeText(newest.toString())
            } catch (io: IOException) {
                Log.w(TAG, "failed to advance exit-info watermark", io)
            }
        }
        records
            .filter { it.timestamp > watermark && isReportableReason(it.reason) }
            .map { info ->
                buildString {
                    append("reason=").append(reasonName(info.reason))
                    append(" timestamp=").append(info.timestamp)
                    append(" importance=").append(info.importance)
                    info.description?.let { append(" description=").append(it) }
                }
            }
    } catch (t: Throwable) {
        // Robolectric / exotic OEM builds: exit-info is best-effort, the
        // marker-file path above still works everywhere.
        Log.w(TAG, "exit-info unavailable", t)
        emptyList()
    }

    private fun logHeader(): String = buildString {
        appendLine("Dustvalve Next crash log")
        appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("android=${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        append("device=${Build.MANUFACTURER} ${Build.MODEL}")
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        else -> "OTHER($reason)"
    }

    companion object {
        private const val TAG = "CrashReport"
        private const val CRASH_DIR = "crash"
        private const val PENDING_FILE = "pending_crash.txt"
        private const val WATERMARK_FILE = "exit_watermark.txt"
        private const val PID_UNQUERIED = 0
        private const val MAX_EXIT_RECORDS = 8
        private const val SHARE_SUBJECT = "Dustvalve Next crash log"

        /** Encoded issue bodies beyond this risk tripping GitHub's URL cap. */
        private const val MAX_ISSUE_BODY_CHARS = 3000

        /**
         * True only for exit reasons that mean the app itself misbehaved.
         * REASON_USER_REQUESTED / REASON_USER_STOPPED are the force-close
         * cases the prompt must never fire for; LMK and self-exit are
         * routine process churn.
         */
        fun isReportableReason(reason: Int): Boolean = when (reason) {
            ApplicationExitInfo.REASON_CRASH,
            ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_ANR,
            -> true

            else -> false
        }

        fun buildIssueUrl(logText: String): String {
            val title = "[crash] automatic report from ${BuildConfig.VERSION_NAME}"
            val body = buildString {
                appendLine("<!-- Feel free to describe what you were doing when it crashed. -->")
                appendLine()
                appendLine("```")
                appendLine(logText.take(MAX_ISSUE_BODY_CHARS))
                if (logText.length > MAX_ISSUE_BODY_CHARS) appendLine("[log truncated - full log available on request]")
                appendLine("```")
            }
            return AppUpdateService.REPO_URL +
                "/issues/new?title=" + URLEncoder.encode(title, Charsets.UTF_8.name()) +
                "&body=" + URLEncoder.encode(body, Charsets.UTF_8.name())
        }
    }
}
