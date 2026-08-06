package com.dustvalve.next.android.download

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import com.dustvalve.next.android.util.isAtLeastQ
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground shell that keeps the process alive while [DownloadController] has
 * work, and hosts the shared download notification ([DownloadNotificationCenter.NOTIFICATION_ID])
 * as its foreground notification. It owns no download logic - the controller
 * runs the transfers on its own scope; this service exists purely so downloads
 * survive the UI being backgrounded/closed.
 *
 * Lifecycle: started (idempotently) from [DownloadController.enqueue]; calls
 * `startForeground` immediately to satisfy the 5s deadline, observes
 * [DownloadController.isActive], and tears itself down when work drains.
 *
 * ## Self-stop rules (v0.5.20 crash: ForegroundServiceDidNotStartInTimeException)
 *
 * Batch downloads are enqueued one work item at a time - "download all" on an
 * artist awaits track N before enqueueing track N+1 - so the queue drains
 * (isActive false) and re-arms (isActive true + a fresh
 * `startForegroundService`) once *per track*. Tearing down on every drain made
 * the app race the system:
 *
 * 1. The drain handler called `stopForeground`, which clears
 *    `ServiceRecord.isForeground`.
 * 2. The next track's `startForegroundService` landed in that gap. Because the
 *    record was no longer foreground, ActivityManager set `fgRequired = true`
 *    and armed a new "must call startForeground" deadline
 *    (`ActiveServices.sendServiceArgsLocked`).
 * 3. The unconditional `stopSelf()` from step 1 then tore the record down with
 *    `fgRequired` still set, and `ActiveServices.bringDownServiceLocked` posts
 *    SERVICE_FOREGROUND_CRASH_MSG for exactly that case - an *immediate*
 *    `RemoteServiceException$ForegroundServiceDidNotStartInTimeException`, not
 *    a timeout (the 10s timeout path raises an ANR instead).
 *
 * Two changes close it:
 *
 * - Idle teardown lingers ([IDLE_LINGER_MS]) instead of firing on every drain,
 *   so consecutive items of one batch keep a single foreground service alive.
 *   That also stops the per-track notification flicker and avoids re-asking for
 *   a background FGS start mid-batch (which Android 12+ can refuse outright).
 * - The self-stop is scoped to the last delivered `startId` via
 *   [Service.stopSelfResult], and only then does the service leave the
 *   foreground. `ActiveServices.stopServiceTokenLocked` compares against
 *   `ServiceRecord.getLastStartId()`, which is bumped when a start is *queued*
 *   rather than when it is delivered, so a start request that has not reached
 *   `onStartCommand` yet still vetoes the stop - under the same
 *   ActivityManager lock that would otherwise bring the record down.
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var controller: DownloadController

    @Inject
    lateinit var notificationCenter: DownloadNotificationCenter

    @Inject
    @Dispatcher(AppDispatchers.IO)
    lateinit var ioDispatcher: CoroutineDispatcher

    private val scope by lazy { CoroutineScope(SupervisorJob() + ioDispatcher) }
    private var observing = false

    /** startId of the most recent [onStartCommand]; every self-stop is scoped to it. */
    @Volatile
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        notificationCenter.setForegroundOwned(true)
        // Typed startForeground + dataSync FGS type are API 29+; below that
        // the 2-arg form is the only option (compat minSdk 26).
        if (isAtLeastQ()) {
            startForeground(
                DownloadNotificationCenter.NOTIFICATION_ID,
                notificationCenter.currentForegroundNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(
                DownloadNotificationCenter.NOTIFICATION_ID,
                notificationCenter.currentForegroundNotification(),
            )
        }
        if (!observing) {
            observing = true
            scope.launch {
                // collectLatest, so an isActive=true arriving during the linger
                // cancels the pending teardown outright.
                controller.isActive.collectLatest { active ->
                    if (!active) {
                        delay(IDLE_LINGER_MS)
                        stopIfStillIdle()
                    }
                }
            }
        }
        // In-memory queue is lost on process death, so don't auto-restart.
        return START_NOT_STICKY
    }

    /**
     * Stops the service iff it is still idle AND no start request is pending.
     * See the class KDoc for why the order here - stop first, leave the
     * foreground second - is the part that must not be flipped back.
     */
    private fun stopIfStillIdle() {
        // Covers the window where work re-armed after the last emission but
        // before collectLatest could cancel this coroutine.
        if (controller.isActive.value) return
        if (!stopSelfResult(lastStartId)) return
        notificationCenter.setForegroundOwned(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * Android 15+ caps dataSync foreground services at ~6h per day and crashes
     * apps that are still foregrounded when the cap hits and don't stop here.
     * Pause instead of dying: the controller keeps every `.tmp` partial, the
     * notification center posts its regular (non-foreground) "downloads
     * paused" card with a Resume action, and tapping Resume walks the normal
     * [DownloadController.resume] path - which restarts this service once the
     * FGS budget allows and continues from the partial's offset.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        controller.pause()
        notificationCenter.setForegroundOwned(false)
        // Leaving the foreground is what satisfies the timeout contract and
        // stops the budget burning, so it happens unconditionally here.
        stopForeground(STOP_FOREGROUND_REMOVE)
        // If the state was already paused, no StateFlow change fires to
        // re-post the card stopForeground just removed - re-post explicitly.
        notificationCenter.repostAfterForegroundTimeout()
        // Scoped like the idle path: a start request queued while we were
        // leaving the foreground must not be answered with a teardown.
        stopSelfResult(lastStartId)
    }

    override fun onDestroy() {
        notificationCenter.setForegroundOwned(false)
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        /**
         * How long the service stays up after the queue drains. Batch
         * downloads re-enqueue within milliseconds of the previous item's
         * terminal event; anything comfortably above that scheduling gap keeps
         * one service alive for the whole batch. Kept short so the final
         * progress card is not left on screen after the last track lands.
         */
        const val IDLE_LINGER_MS = 2_000L
    }
}
