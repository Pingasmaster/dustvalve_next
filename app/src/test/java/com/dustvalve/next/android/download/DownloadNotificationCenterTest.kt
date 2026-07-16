package com.dustvalve.next.android.download

import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.repository.DownloadProgressReporter
import com.dustvalve.next.android.util.CookieEncryption
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Tests the pure state machine of [DownloadNotificationCenter] without
 * asserting on the actual notification side-effects. Each test exercises the
 * lifecycle methods and inspects [DownloadNotificationCenter.currentState]
 * directly.
 *
 * Notification posting is fire-and-forget on a debounced coroutine inside the
 * singleton; we don't wait for it. The shutdownForTest() in @After cancels
 * the background collector so tests don't leak coroutines into later classes.
 */
@RunWith(RobolectricTestRunner::class)
class DownloadNotificationCenterTest {

    private lateinit var center: DownloadNotificationCenter
    private lateinit var store: SettingsDataStore

    @Before fun setUp() {
        mockkObject(CookieEncryption)
        every { CookieEncryption.encrypt(any()) } answers { firstArg() }
        every { CookieEncryption.decrypt(any()) } answers { firstArg() }

        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(ctx.filesDir, "datastore/settings.preferences_pb").delete()
        store = SettingsDataStore(ctx)
        center = DownloadNotificationCenter(ctx, store, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }

    @After fun tearDown() {
        center.shutdownForTest()
        unmockkAll()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(ctx.filesDir, "datastore/settings.preferences_pb").delete()
    }

    // --- Single-track lifecycle -----------------------------------------

    @Test fun `trackStarted adds the track to activeTracks`() {
        center.trackStarted("t1", "Sunrise")

        val s = center.currentState
        assertThat(s.activeTracks).containsKey("t1")
        val tp = s.activeTracks.getValue("t1")
        assertThat(tp.title).isEqualTo("Sunrise")
        assertThat(tp.bytesWritten).isEqualTo(0L)
        assertThat(tp.expectedTotal).isNull()
        assertThat(s.batchStack).isEmpty()
    }

    @Test fun `trackProgress updates bytes and total for active track`() {
        center.trackStarted("t1", "Sunrise")
        center.trackProgress("t1", bytesWritten = 1024L, expectedTotal = 4096L)

        val tp = center.currentState.activeTracks.getValue("t1")
        assertThat(tp.bytesWritten).isEqualTo(1024L)
        assertThat(tp.expectedTotal).isEqualTo(4096L)
    }

    @Test fun `trackProgress for unknown track is a no-op`() {
        center.trackProgress("ghost", 999L, 1000L)
        assertThat(center.currentState.activeTracks).isEmpty()
    }

    @Test fun `trackFinished removes the track and clears state when idle`() {
        center.trackStarted("t1", "Sunrise")
        center.trackFinished("t1", success = true)

        val s = center.currentState
        assertThat(s.activeTracks).isEmpty()
        assertThat(s.batchStack).isEmpty()
        // Without an active batch, the completed counter stays at zero.
        assertThat(s.completedInBatch).isEqualTo(0)
    }

    @Test fun `multiple concurrent tracks all appear in activeTracks`() {
        center.trackStarted("t1", "Sunrise")
        center.trackStarted("t2", "Sunset")
        center.trackStarted("t3", "Midnight")

        assertThat(center.currentState.activeTracks.keys).containsExactly("t1", "t2", "t3")
    }

    // --- Batch wrapping --------------------------------------------------

    @Test fun `withBatch pushes a batch and clears it on completion`() = runTest {
        center.withBatch("Greatest Hits", totalTracks = 10, kind = DownloadProgressReporter.BatchKind.ALBUM) {
            val s = center.currentState
            assertThat(s.batchStack).hasSize(1)
            val b = s.batchStack.single()
            assertThat(b.label).isEqualTo("Greatest Hits")
            assertThat(b.totalTracks).isEqualTo(10)
            assertThat(b.kind).isEqualTo(DownloadProgressReporter.BatchKind.ALBUM)
        }

        val after = center.currentState
        assertThat(after.batchStack).isEmpty()
        assertThat(after.completedInBatch).isEqualTo(0)
    }

    @Test fun `withBatch clears stack even when block throws`() = runTest {
        val thrown = runCatching {
            center.withBatch("Sad Songs", 3, DownloadProgressReporter.BatchKind.PLAYLIST) {
                throw IllegalStateException("boom")
            }
        }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
        assertThat(center.currentState.batchStack).isEmpty()
        assertThat(center.currentState.completedInBatch).isEqualTo(0)
    }

    @Test fun `trackFinished inside a batch increments completedInBatch`() = runTest {
        center.withBatch("Greatest Hits", 5, DownloadProgressReporter.BatchKind.ALBUM) {
            center.trackStarted("t1", "A")
            center.trackFinished("t1", true)
            center.trackStarted("t2", "B")
            center.trackFinished("t2", true)

            assertThat(center.currentState.completedInBatch).isEqualTo(2)
        }
    }

    @Test fun `trackFinished outside any batch does not increment completedInBatch`() {
        center.trackStarted("t1", "A")
        center.trackFinished("t1", true)
        assertThat(center.currentState.completedInBatch).isEqualTo(0)
    }

    // --- Nested batches --------------------------------------------------

    @Test fun `nested batches both appear on the stack`() = runTest {
        center.withBatch("Artist Discography", 30, DownloadProgressReporter.BatchKind.ARTIST) {
            assertThat(center.currentState.batchStack).hasSize(1)
            assertThat(center.currentState.batchStack.first().kind)
                .isEqualTo(DownloadProgressReporter.BatchKind.ARTIST)

            center.withBatch("Album 1", 10, DownloadProgressReporter.BatchKind.ALBUM) {
                assertThat(center.currentState.batchStack).hasSize(2)
                // Outer (artist) is still first - the "outer wins" invariant for chip rendering.
                assertThat(center.currentState.batchStack.first().kind)
                    .isEqualTo(DownloadProgressReporter.BatchKind.ARTIST)
                assertThat(center.currentState.batchStack.last().kind)
                    .isEqualTo(DownloadProgressReporter.BatchKind.ALBUM)
            }

            // Inner batch popped; outer still active. completedInBatch survives.
            assertThat(center.currentState.batchStack).hasSize(1)
        }

        assertThat(center.currentState.batchStack).isEmpty()
    }

    @Test fun `popping inner batch preserves outer completedInBatch counter`() = runTest {
        center.withBatch("Outer", 20, DownloadProgressReporter.BatchKind.ARTIST) {
            center.trackStarted("t1", "A")
            center.trackFinished("t1", true)
            center.trackStarted("t2", "B")
            center.trackFinished("t2", true)

            center.withBatch("Inner", 5, DownloadProgressReporter.BatchKind.ALBUM) {
                center.trackStarted("t3", "C")
                center.trackFinished("t3", true)
            }

            // 3 tracks finished in total while outer was active -> counter is 3.
            assertThat(center.currentState.completedInBatch).isEqualTo(3)
        }
        // Outer popped -> counter reset.
        assertThat(center.currentState.completedInBatch).isEqualTo(0)
    }

    // --- Independence from settings flag ---------------------------------

    @Test fun `state machinery runs even when notifications are disabled`() = runTest {
        store.setDownloadNotificationsEnabled(false)
        center.trackStarted("t1", "A")
        // State still mutates; only the visible notification is gated.
        assertThat(center.currentState.activeTracks).containsKey("t1")
    }

    // --- BatchKind exhaustiveness ----------------------------------------

    @Test fun `every BatchKind round-trips through withBatch`() = runTest {
        for (kind in DownloadProgressReporter.BatchKind.values()) {
            center.withBatch("L", 1, kind) {
                assertThat(center.currentState.batchStack.single().kind).isEqualTo(kind)
            }
        }
        assertThat(center.currentState.batchStack).isEmpty()
    }

    // --- Channel creation is idempotent ----------------------------------

    @Test fun `ensureChannel can be called repeatedly without error`() {
        center.ensureChannel()
        center.ensureChannel()
        center.ensureChannel()
        // No exception = success.
    }
}
