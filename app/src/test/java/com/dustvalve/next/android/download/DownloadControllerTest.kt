@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.data.local.db.entity.DownloadEntity
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * JVM tests for [DownloadController]'s queue/foreground lifecycle and the
 * cold-start sweep. An unconfined dispatcher makes every internal launch run
 * synchronously on the test thread, so state assertions are deterministic.
 */
@RunWith(RobolectricTestRunner::class)
class DownloadControllerTest {

    private lateinit var context: Context
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var downloadDao: DownloadDao
    private lateinit var controller: DownloadController

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        downloadRepository = mockk(relaxed = true)
        downloadDao = mockk()
        coEvery { downloadDao.getAllSync() } returns emptyList()
        controller = DownloadController(
            context = context,
            downloadRepository = downloadRepository,
            downloadAlbumUseCase = mockk<DownloadAlbumUseCase>(relaxed = true),
            notificationCenter = mockk<DownloadNotificationCenter>(relaxed = true),
            downloadDao = downloadDao,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After fun tearDown() {
        unmockkAll()
        File(context.filesDir, "downloads").deleteRecursively()
    }

    private fun track(id: String) = Track(
        id = id,
        albumId = "al1",
        title = id,
        artist = "Artist",
        trackNumber = 1,
        duration = 60f,
        streamUrl = "https://s/$id",
        artUrl = "",
        albumTitle = "Alb",
    )

    // --- M4: isActive is serialized with queue state --------------------

    @Test fun `isActive stays true while work runs and drops only when the queue drains`() {
        val gate = CompletableDeferred<Unit>()
        coEvery { downloadRepository.downloadTrack(any(), any()) } coAnswers { gate.await() }

        controller.enqueueTrack(track("t1"))
        assertThat(controller.isActive.value).isTrue()

        gate.complete(Unit)
        assertThat(controller.isActive.value).isFalse()
    }

    @Test fun `queue re-arms after a drain - a fresh enqueue flips isActive back on and runs`() {
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        coEvery { downloadRepository.downloadTrack(match { it.id == "t1" }, any()) } coAnswers { firstGate.await() }
        coEvery { downloadRepository.downloadTrack(match { it.id == "t2" }, any()) } coAnswers { secondGate.await() }

        controller.enqueueTrack(track("t1"))
        firstGate.complete(Unit)
        assertThat(controller.isActive.value).isFalse()

        // The drained loop must have released loopRunning inside the lock so
        // a fresh enqueue starts a new loop rather than being ignored.
        controller.enqueueTrack(track("t2"))
        assertThat(controller.isActive.value).isTrue()
        secondGate.complete(Unit)
        assertThat(controller.isActive.value).isFalse()
    }

    // --- L31: cold-start sweep ------------------------------------------

    @Test fun `cold-start sweep purges partials and orphans but keeps referenced, fresh, and image files`() = runBlocking {
        val downloads = File(context.filesDir, "downloads")
        val albumDir = File(downloads, "al1").also { it.mkdirs() }
        val imagesDir = File(downloads, "images").also { it.mkdirs() }
        val oldMtime = System.currentTimeMillis() - 60L * 60L * 1000L

        val kept = File(albumDir, "kept.mp3").also { it.writeBytes(ByteArray(4)); it.setLastModified(oldMtime) }
        val orphan = File(albumDir, "orphan.mp3").also { it.writeBytes(ByteArray(4)); it.setLastModified(oldMtime) }
        val fresh = File(albumDir, "fresh.mp3").also { it.writeBytes(ByteArray(4)) }
        val partial = File(albumDir, "part.mp3.tmp").also { it.writeBytes(ByteArray(4)) }
        val sidecar = File(albumDir, "part.mp3.tmp.meta").also { it.writeText("{}") }
        val image = File(imagesDir, "cover.jpg").also { it.writeBytes(ByteArray(4)); it.setLastModified(oldMtime) }

        coEvery { downloadDao.getAllSync() } returns listOf(
            DownloadEntity(trackId = "t1", albumId = "al1", filePath = kept.absolutePath, sizeBytes = 4L),
            DownloadEntity(trackId = "t2", albumId = "al2", filePath = "content://saf/doc/42", sizeBytes = 4L),
        )

        controller.awaitColdStartPurge()

        assertThat(partial.exists()).isFalse()
        assertThat(sidecar.exists()).isFalse()
        assertThat(orphan.exists()).isFalse()
        assertThat(kept.exists()).isTrue()
        assertThat(fresh.exists()).isTrue()
        assertThat(image.exists()).isTrue()
    }

    @Test fun `cold-start sweep skips the orphan pass when the DB query fails but still purges partials`() = runBlocking {
        val downloads = File(context.filesDir, "downloads")
        val albumDir = File(downloads, "al1").also { it.mkdirs() }
        val oldMtime = System.currentTimeMillis() - 60L * 60L * 1000L
        val orphan = File(albumDir, "orphan.mp3").also { it.writeBytes(ByteArray(4)); it.setLastModified(oldMtime) }
        val partial = File(albumDir, "part.mp3.tmp").also { it.writeBytes(ByteArray(4)) }
        coEvery { downloadDao.getAllSync() } throws IllegalStateException("db not ready")

        controller.awaitColdStartPurge()

        assertThat(partial.exists()).isFalse()
        // Conservative: without the row set we cannot tell orphans apart.
        assertThat(orphan.exists()).isTrue()
    }
}
