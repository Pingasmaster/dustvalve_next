@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.download

import android.app.Notification
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.dustvalve.next.android.R
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows

/**
 * Covers the two ways [DownloadService] is allowed to stop itself:
 *
 * - the Android 15+ dataSync timeout ([DownloadService.onTimeout]), which must
 *   pause (keeping partials) rather than let the system crash the app, and must
 *   fully leave the foreground so the ~6h budget stops burning;
 * - the idle drain, which must NOT fire on every item of a batch download. That
 *   churn is what produced the v0.5.20 crash log
 *   (`ForegroundServiceDidNotStartInTimeException`): the service left the
 *   foreground and stopped itself while the next track's
 *   `startForegroundService` was already queued, and ActivityManager crashes an
 *   app that brings a service down with a start request still awaiting
 *   `startForeground`.
 *
 * The service is built attached-but-not-created so Hilt injection never runs;
 * the injected collaborators are replaced with mocks directly.
 */
@RunWith(RobolectricTestRunner::class)
class DownloadServiceTest {

    private val controller = mockk<DownloadController>(relaxed = true)
    private val notificationCenter = mockk<DownloadNotificationCenter>(relaxed = true)

    @After fun tearDown() {
        unmockkAll()
    }

    private fun service(
        dispatcher: CoroutineDispatcher,
        isActive: MutableStateFlow<Boolean>? = null,
    ): DownloadService {
        val service = Robolectric.buildService(DownloadService::class.java).get()
        if (isActive != null) every { controller.isActive } returns isActive
        every { notificationCenter.currentForegroundNotification() } returns placeholderNotification()
        service.controller = controller
        service.notificationCenter = notificationCenter
        service.ioDispatcher = dispatcher
        return service
    }

    private fun placeholderNotification(): Notification = NotificationCompat.Builder(
        RuntimeEnvironment.getApplication(),
        DownloadNotificationCenter.CHANNEL_ID,
    ).setSmallIcon(R.drawable.ic_download).build()

    @Test fun `onTimeout pauses the controller, leaves the foreground, and re-posts the paused card`() {
        val service = service(UnconfinedTestDispatcher())

        service.onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        // Pause keeps .tmp partials; the notification's Resume action drives
        // DownloadController.resume, which restarts the service later.
        verify(exactly = 1) { controller.pause() }
        verify(exactly = 1) { notificationCenter.setForegroundOwned(false) }
        verify(exactly = 1) { notificationCenter.repostAfterForegroundTimeout() }
        assertThat(Shadows.shadowOf(service).isStoppedBySelf).isTrue()
    }

    @Test fun `a drain that re-arms inside the linger never stops the service`() = runTest {
        val isActive = MutableStateFlow(true)
        val service = service(StandardTestDispatcher(testScheduler), isActive)
        service.onStartCommand(null, 0, 7)
        runCurrent()

        // Track N finishes: the queue drains for the scheduling gap it takes
        // the awaiting caller to enqueue track N+1.
        isActive.value = false
        advanceTimeBy(HALF_LINGER_MS)
        isActive.value = true
        advanceTimeBy(FAR_PAST_LINGER_MS)
        runCurrent()

        assertThat(Shadows.shadowOf(service).isStoppedBySelf).isFalse()
        assertThat(Shadows.shadowOf(service).isForegroundStopped).isFalse()
        verify(exactly = 0) { notificationCenter.setForegroundOwned(false) }

        service.onDestroy()
    }

    @Test fun `a drain that outlasts the linger stops the service, scoped to the last startId`() = runTest {
        val isActive = MutableStateFlow(true)
        val service = service(StandardTestDispatcher(testScheduler), isActive)
        service.onStartCommand(null, 0, 7)
        runCurrent()

        isActive.value = false
        advanceTimeBy(HALF_LINGER_MS)
        runCurrent()
        // Still inside the linger: nothing torn down yet.
        assertThat(Shadows.shadowOf(service).isStoppedBySelf).isFalse()

        advanceTimeBy(FAR_PAST_LINGER_MS)
        runCurrent()

        assertThat(Shadows.shadowOf(service).isStoppedBySelf).isTrue()
        // stopSelfResult(startId), not stopSelf(): ActivityManager refuses the
        // stop while a newer (possibly still undelivered) start is queued,
        // which is what keeps a pending startForegroundService from being
        // answered with a teardown.
        assertThat(Shadows.shadowOf(service).stopSelfResultId).isEqualTo(7)
        // ... and the foreground is only released once the stop is accepted.
        assertThat(Shadows.shadowOf(service).isForegroundStopped).isTrue()
        verify(exactly = 1) { notificationCenter.setForegroundOwned(false) }

        service.onDestroy()
    }

    private companion object {
        /** Both sit either side of DownloadService.IDLE_LINGER_MS (2s). */
        const val HALF_LINGER_MS = 1_000L
        const val FAR_PAST_LINGER_MS = 10_000L
    }
}
