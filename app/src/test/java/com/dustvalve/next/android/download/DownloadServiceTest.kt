@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.download

import android.content.pm.ServiceInfo
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

/**
 * Verifies the Android 15+ dataSync timeout path: [DownloadService.onTimeout]
 * must pause (keeping partials) rather than let the system crash the app, and
 * must fully leave the foreground so the ~6h budget stops burning.
 *
 * The service is built attached-but-not-created so Hilt injection never runs;
 * the injected collaborators are replaced with mocks directly.
 */
@RunWith(RobolectricTestRunner::class)
class DownloadServiceTest {

    @After fun tearDown() {
        unmockkAll()
    }

    @Test fun `onTimeout pauses the controller, leaves the foreground, and re-posts the paused card`() {
        val service = Robolectric.buildService(DownloadService::class.java).get()
        val controller = mockk<DownloadController>(relaxed = true)
        val notificationCenter = mockk<DownloadNotificationCenter>(relaxed = true)
        service.controller = controller
        service.notificationCenter = notificationCenter
        service.ioDispatcher = UnconfinedTestDispatcher()

        service.onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        // Pause keeps .tmp partials; the notification's Resume action drives
        // DownloadController.resume, which restarts the service later.
        verify(exactly = 1) { controller.pause() }
        verify(exactly = 1) { notificationCenter.setForegroundOwned(false) }
        verify(exactly = 1) { notificationCenter.repostAfterForegroundTimeout() }
        assertThat(Shadows.shadowOf(service).isStoppedBySelf).isTrue()
    }
}
