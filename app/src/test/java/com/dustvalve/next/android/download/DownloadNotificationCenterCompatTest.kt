package com.dustvalve.next.android.download

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.repository.DownloadProgressReporter.BatchKind
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * Regression net for the v0.5.0-v0.5.2 "any download instantly crashes the
 * app" bug on Android 8-15.
 *
 * [DownloadService.onStartCommand] hands [DownloadNotificationCenter.currentForegroundNotification]
 * to startForeground on EVERY download. In v0.5.0-v0.5.2 that call built a
 * platform `Notification.ProgressStyle` - an API 36 class - unconditionally
 * (hidden behind `@SuppressLint("NewApi")`), so on any device below Android 16
 * the app died with NoClassDefFoundError the moment a download started, and
 * then crash-looped as the system restarted the service.
 *
 * SDK_INT is forced below 36 via ReflectionHelpers rather than @Config
 * multi-sdk sandboxes: an extra sandbox per SDK level re-triggers
 * Robolectric's native-runtime font copy and races it into
 * FileSystemAlreadyExistsException across the rest of the suite. Forcing the
 * field exercises the exact gate ([DownloadNotificationCenter.liveUpdateCapable]
 * and the compat builders) that decides between the crashing and safe paths.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class DownloadNotificationCenterCompatTest {

    private var realSdk = 0

    @Before fun rememberSdk() {
        realSdk = Build.VERSION.SDK_INT
    }

    @After fun restoreSdk() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", realSdk)
    }

    private fun forceSdk(sdk: Int) {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)
    }

    private fun newCenter(): DownloadNotificationCenter = DownloadNotificationCenter(
        context = ApplicationProvider.getApplicationContext(),
        settingsDataStore = SettingsDataStore(ApplicationProvider.getApplicationContext()),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    @Test
    fun foregroundNotification_emptyState_api26_buildsPlaceholderWithoutCrashing() {
        forceSdk(26)
        val center = newCenter()
        // Exactly what DownloadService.onStartCommand does before any track
        // has reported progress.
        assertThat(center.currentForegroundNotification()).isNotNull()
        center.shutdownForTest()
    }

    @Test
    fun foregroundNotification_singleTrackInFlight_api26_buildsProgressCard() {
        forceSdk(26)
        val center = newCenter()
        center.trackStarted("t1", "Some Track")
        center.trackProgress("t1", bytesWritten = 500L, expectedTotal = 1000L)

        assertThat(center.currentForegroundNotification()).isNotNull()
        center.shutdownForTest()
    }

    @Test
    fun foregroundNotification_insideAlbumBatch_api26_buildsBatchCard() = runTest {
        forceSdk(26)
        val center = newCenter()
        center.withBatch(label = "Some Album", totalTracks = 3, kind = BatchKind.ALBUM) {
            center.trackStarted("t1", "Track One")
            center.trackProgress("t1", bytesWritten = 10L, expectedTotal = 100L)

            assertThat(center.currentForegroundNotification()).isNotNull()
        }
        center.shutdownForTest()
    }

    @Test
    fun foregroundNotification_pausedState_api26_buildsPausedCard() {
        forceSdk(26)
        val center = newCenter()
        center.setPaused(true)

        assertThat(center.currentForegroundNotification()).isNotNull()
        center.shutdownForTest()
    }

    @Test
    fun foregroundNotification_api33_buildsCompatCardWithoutCrashing() {
        forceSdk(33)
        val center = newCenter()
        center.trackStarted("t1", "Some Track")

        assertThat(center.currentForegroundNotification()).isNotNull()
        center.shutdownForTest()
    }

    @Test
    fun foregroundNotification_api35_buildsCompatCardWithoutCrashing() {
        forceSdk(35)
        val center = newCenter()
        center.setPaused(true)

        assertThat(center.currentForegroundNotification()).isNotNull()
        center.shutdownForTest()
    }
}
