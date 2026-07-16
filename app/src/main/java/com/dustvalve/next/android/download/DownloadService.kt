package com.dustvalve.next.android.download

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notificationCenter.setForegroundOwned(true)
        // Legacy branch: the typed startForeground overload + dataSync FGS
        // type are API 29+; below that the 2-arg form is the only option.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
                controller.isActive.collect { active ->
                    if (!active) {
                        notificationCenter.setForegroundOwned(false)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        // In-memory queue is lost on process death, so don't auto-restart.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        notificationCenter.setForegroundOwned(false)
        scope.cancel()
        super.onDestroy()
    }
}
