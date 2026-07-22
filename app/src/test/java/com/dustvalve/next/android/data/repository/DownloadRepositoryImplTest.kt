package com.dustvalve.next.android.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.dustvalve.next.android.cache.StorageTracker
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.AlbumDao
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.DownloadEntity
import com.dustvalve.next.android.data.remote.DustvalveDownloadScraper
import com.dustvalve.next.android.data.remote.RangeResumeDownloader
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.DownloadProgressReporter
import com.dustvalve.next.android.domain.repository.MediaCacheClearer
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * JVM tests for [DownloadRepositoryImpl]'s file/DB orchestration. The actual
 * HTTP transfer ([RangeResumeDownloader]) is object-mocked - its own behavior
 * is covered by RangeResumeDownloaderTest - so these tests drive the
 * repository's single-flight guard, upgrade commit ordering, and resume
 * sidecar handling deterministically.
 */
class DownloadRepositoryImplTest {

    private lateinit var filesRoot: File
    private lateinit var cacheRoot: File

    private lateinit var database: DustvalveNextDatabase
    private lateinit var downloadDao: DownloadDao
    private lateinit var trackDao: TrackDao
    private lateinit var albumDao: AlbumDao
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var youtubeRepository: YouTubeRepository
    private lateinit var mediaCacheClearer: MediaCacheClearer
    private lateinit var repo: DownloadRepositoryImpl

    @Before fun setUp() {
        filesRoot = Files.createTempDirectory("dl_files").toFile()
        cacheRoot = Files.createTempDirectory("dl_cache").toFile()

        database = mockk()
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            firstArg<suspend () -> Any?>().invoke()
        }

        downloadDao = mockk(relaxed = true)
        coEvery { downloadDao.getByTrackId(any()) } returns null
        trackDao = mockk(relaxed = true)
        coEvery { trackDao.getById(any()) } returns null
        albumDao = mockk()
        coEvery { albumDao.getById(any()) } returns null
        settingsDataStore = mockk()
        coEvery { settingsDataStore.getDownloadFormatSync() } returns "mp3-128"
        coEvery { settingsDataStore.getDedicatedFolderEnabledSync() } returns false
        youtubeRepository = mockk()
        mediaCacheClearer = mockk()

        val context = mockk<Context>()
        every { context.filesDir } returns filesRoot
        every { context.cacheDir } returns cacheRoot

        repo = DownloadRepositoryImpl(
            database = database,
            downloadDao = downloadDao,
            trackDao = trackDao,
            albumDao = albumDao,
            client = OkHttpClient(),
            storageTracker = mockk<StorageTracker>(relaxed = true),
            downloadScraper = mockk<DustvalveDownloadScraper>(relaxed = true),
            settingsDataStore = settingsDataStore,
            youtubeRepository = youtubeRepository,
            notificationCenter = mockk<DownloadProgressReporter>(relaxed = true),
            mediaCacheClearer = mediaCacheClearer,
            context = context,
            ioDispatcher = Dispatchers.IO,
        )

        mockkObject(RangeResumeDownloader)
    }

    @After fun tearDown() {
        unmockkAll()
        filesRoot.deleteRecursively()
        cacheRoot.deleteRecursively()
    }

    private fun bandcampTrack(id: String) = Track(
        id = id,
        albumId = "al1",
        title = id,
        artist = "Artist",
        trackNumber = 1,
        duration = 60f,
        streamUrl = "https://cdn.example.com/$id.mp3",
        artUrl = "",
        albumTitle = "Alb",
    )

    private fun youtubeTrack(id: String) = bandcampTrack(id).copy(
        streamUrl = "https://www.youtube.com/watch?v=${id.removePrefix("yt_")}",
        source = TrackSource.YOUTUBE,
    )

    private fun stubStream(onCall: suspend (sink: OutputStream, startOffset: Long, expectedTotal: Long?) -> Long) {
        coEvery {
            RangeResumeDownloader.stream(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            onCall(arg(2), arg(4), arg(6))
        }
    }

    // --- M6: per-track single-flight ------------------------------------

    @Test fun `concurrent downloadTrack calls for one track serialize and download once`() = runBlocking {
        val body = ByteArray(2048) { 1 }
        val firstEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val streamCalls = AtomicInteger(0)
        stubStream { sink, _, _ ->
            streamCalls.incrementAndGet()
            firstEntered.complete(Unit)
            release.await()
            sink.write(body)
            body.size.toLong()
        }

        // Shared fake DB row: appears once the first call inserts, which lets
        // the second call short-circuit via the same-quality check.
        var row: DownloadEntity? = null
        coEvery { downloadDao.getByTrackId("t1") } answers { row }
        coEvery { downloadDao.insert(any()) } answers { row = firstArg() }

        val track = bandcampTrack("t1")
        val first = async(Dispatchers.IO) { repo.downloadTrack(track) }
        firstEntered.await()
        val second = async(Dispatchers.IO) { repo.downloadTrack(track) }
        delay(100) // let the second call reach the per-track lock
        release.complete(Unit)
        first.await()
        second.await()

        // Without the guard both calls pass the null getByTrackId check and
        // both stream (interleaving into one .tmp); with it, exactly one does.
        assertThat(streamCalls.get()).isEqualTo(1)
        assertThat(File(filesRoot, "downloads/al1/t1.mp3").readBytes()).isEqualTo(body)
    }

    // --- M5: quality upgrade commit ordering ----------------------------

    @Test fun `failed quality upgrade keeps the old file and row`() = runBlocking {
        val oldBytes = ByteArray(10) { 9 }
        val oldDir = File(filesRoot, "downloads/al1").also { it.mkdirs() }
        val oldFile = File(oldDir, "yt_x.mp3").also { it.writeBytes(oldBytes) }
        coEvery { downloadDao.getByTrackId("yt_x") } returns DownloadEntity(
            trackId = "yt_x",
            albumId = "al1",
            filePath = oldFile.absolutePath,
            sizeBytes = oldBytes.size.toLong(),
            format = "mp3-128",
        )
        coEvery { youtubeRepository.getDownloadableStream(any()) } returns
            ("https://rr1---sn-abc.googlevideo.com/videoplayback?itag=251" to AudioFormat.OPUS)
        stubStream { _, _, _ -> throw IOException("cdn broke") }

        val ex = runCatching { repo.downloadTrack(youtubeTrack("yt_x")) }.exceptionOrNull()

        assertThat(ex).isInstanceOf(IOException::class.java)
        // Old audio and its row survive: no phantom "downloaded" track.
        assertThat(oldFile.readBytes()).isEqualTo(oldBytes)
        coVerify(exactly = 0) { downloadDao.insert(any()) }
        coVerify(exactly = 0) { downloadDao.delete(any()) }
    }

    @Test fun `successful quality upgrade deletes the old file only after commit`() = runBlocking {
        val oldDir = File(filesRoot, "downloads/al1").also { it.mkdirs() }
        val oldFile = File(oldDir, "yt_x.mp3").also { it.writeBytes(ByteArray(10) { 9 }) }
        coEvery { downloadDao.getByTrackId("yt_x") } returns DownloadEntity(
            trackId = "yt_x",
            albumId = "al1",
            filePath = oldFile.absolutePath,
            sizeBytes = 10L,
            format = "mp3-128",
        )
        coEvery { youtubeRepository.getDownloadableStream(any()) } returns
            ("https://rr1---sn-abc.googlevideo.com/videoplayback?itag=251" to AudioFormat.OPUS)
        val newBody = ByteArray(4096) { 5 }
        stubStream { sink, _, _ ->
            sink.write(newBody)
            newBody.size.toLong()
        }
        val inserted = slot<DownloadEntity>()
        coEvery { downloadDao.insert(capture(inserted)) } just Runs

        repo.downloadTrack(youtubeTrack("yt_x"))

        assertThat(inserted.captured.format).isEqualTo("opus")
        assertThat(File(inserted.captured.filePath).readBytes()).isEqualTo(newBody)
        assertThat(oldFile.exists()).isFalse()
    }

    // --- M1/M2: resume sidecar ------------------------------------------

    @Test fun `partial from a different source variant restarts from zero`() = runBlocking {
        val dir = File(filesRoot, "downloads/al1").also { it.mkdirs() }
        File(dir, "t1.mp3.tmp").writeBytes(ByteArray(500) { 3 })
        File(dir, "t1.mp3.tmp.meta")
            .writeText("""{"expectedTotalBytes":1000,"sourceIdentity":"itag=140"}""")

        val body = ByteArray(1000) { 4 }
        val offsets = mutableListOf<Long>()
        stubStream { sink, startOffset, _ ->
            offsets += startOffset
            sink.write(body)
            startOffset + body.size
        }

        repo.downloadTrack(bandcampTrack("t1"))

        // Identity of the new URL (no-query form) mismatches "itag=140", so
        // the stale 500 bytes are discarded instead of spliced.
        assertThat(offsets).containsExactly(0L)
        assertThat(File(dir, "t1.mp3").readBytes()).isEqualTo(body)
        assertThat(File(dir, "t1.mp3.tmp.meta").exists()).isFalse()
    }

    @Test fun `partial with a matching sidecar resumes with its offset and total`() = runBlocking {
        val stale = ByteArray(500) { 3 }
        val dir = File(filesRoot, "downloads/al1").also { it.mkdirs() }
        File(dir, "t1.mp3.tmp").writeBytes(stale)
        File(dir, "t1.mp3.tmp.meta").writeText(
            """{"expectedTotalBytes":1000,"sourceIdentity":"https://cdn.example.com/t1.mp3"}""",
        )

        val remainder = ByteArray(500) { 4 }
        var seenOffset = -1L
        var seenTotal: Long? = null
        stubStream { sink, startOffset, expectedTotal ->
            seenOffset = startOffset
            seenTotal = expectedTotal
            sink.write(remainder)
            startOffset + remainder.size
        }

        repo.downloadTrack(bandcampTrack("t1"))

        assertThat(seenOffset).isEqualTo(500L)
        assertThat(seenTotal).isEqualTo(1000L)
        assertThat(File(dir, "t1.mp3").readBytes()).isEqualTo(stale + remainder)
        assertThat(File(dir, "t1.mp3.tmp.meta").exists()).isFalse()
    }

    @Test fun `download writes a resume sidecar once the total is known and removes it on success`() = runBlocking {
        val body = ByteArray(2048) { 1 }
        var metaMidTransfer: String? = null
        val metaFile = File(filesRoot, "downloads/al1/t1.mp3.tmp.meta")
        coEvery {
            RangeResumeDownloader.stream(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            val sink = arg<OutputStream>(2)
            val onProgress = arg<((Long, Long?) -> Unit)?>(8)
            onProgress?.invoke(1024L, body.size.toLong())
            metaMidTransfer = metaFile.takeIf { it.exists() }?.readText()
            sink.write(body)
            body.size.toLong()
        }

        repo.downloadTrack(bandcampTrack("t1"))

        // Sidecar existed while the transfer ran, recording total + identity...
        assertThat(metaMidTransfer).contains("\"expectedTotalBytes\":${body.size}")
        assertThat(metaMidTransfer).contains("https://cdn.example.com/t1.mp3")
        // ...and is cleaned up once the download commits.
        assertThat(metaFile.exists()).isFalse()
    }

    @Test fun `resume mismatch reported by the downloader restarts from zero once`() = runBlocking {
        val dir = File(filesRoot, "downloads/al1").also { it.mkdirs() }
        File(dir, "t1.mp3.tmp").writeBytes(ByteArray(500) { 3 })
        File(dir, "t1.mp3.tmp.meta").writeText(
            """{"expectedTotalBytes":1000,"sourceIdentity":"https://cdn.example.com/t1.mp3"}""",
        )

        val body = ByteArray(1000) { 6 }
        val offsets = mutableListOf<Long>()
        stubStream { sink, startOffset, _ ->
            offsets += startOffset
            if (startOffset > 0L) {
                throw RangeResumeDownloader.ResumeMismatchException("offset drifted")
            }
            sink.write(body)
            body.size.toLong()
        }

        repo.downloadTrack(bandcampTrack("t1"))

        assertThat(offsets).containsExactly(500L, 0L).inOrder()
        assertThat(File(dir, "t1.mp3").readBytes()).isEqualTo(body)
    }

    // --- M10: media cache cleared via the live cache --------------------

    @Test fun `clearAll clears the media cache through the clearer, not the filesystem`() = runBlocking {
        val mediaCache = File(cacheRoot, "media_cache").also { it.mkdirs() }
        val cachedSpan = File(mediaCache, "span.v3.exo").also { it.writeBytes(ByteArray(8)) }
        coEvery { downloadDao.getAllSync() } returns emptyList()
        coEvery { mediaCacheClearer.clearAll() } just Runs

        repo.clearAll()

        coVerify(exactly = 1) { mediaCacheClearer.clearAll() }
        // The open cache's directory must never be deleted underneath it.
        assertThat(cachedSpan.exists()).isTrue()
    }

    @Test fun `clearAll survives a failing media cache clearer`() = runBlocking {
        coEvery { downloadDao.getAllSync() } returns emptyList()
        coEvery { mediaCacheClearer.clearAll() } throws IllegalStateException("cache busy")

        repo.clearAll() // must not throw

        coVerify(exactly = 1) { mediaCacheClearer.clearAll() }
    }
}
