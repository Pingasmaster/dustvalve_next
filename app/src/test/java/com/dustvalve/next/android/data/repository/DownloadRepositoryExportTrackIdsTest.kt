package com.dustvalve.next.android.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.dustvalve.next.android.cache.StorageTracker
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.AlbumDao
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.AlbumEntity
import com.dustvalve.next.android.data.local.db.entity.DownloadEntity
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.dustvalve.next.android.data.remote.DustvalveDownloadScraper
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Verifies that [DownloadRepositoryImpl.exportDownloads] honours the
 * `trackIds` filter — only the matching downloads are written to the SAF
 * destination.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DownloadRepositoryExportTrackIdsTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var downloadDao: DownloadDao
    private lateinit var trackDao: TrackDao
    private lateinit var albumDao: AlbumDao
    private lateinit var favoriteDao: FavoriteDao
    private lateinit var database: DustvalveNextDatabase
    private lateinit var storageTracker: StorageTracker
    private lateinit var downloadScraper: DustvalveDownloadScraper
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var youtubeRepository: YouTubeRepository
    private lateinit var client: OkHttpClient
    private lateinit var rootDoc: DocumentFile
    private lateinit var albumDoc: DocumentFile
    private val createdFileNames = mutableListOf<String>()
    private val capturedStreams = mutableMapOf<String, ByteArrayOutputStream>()

    private lateinit var repo: DownloadRepositoryImpl

    @Before fun setUp() {
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        every { context.contentResolver } returns contentResolver

        downloadDao = mockk(relaxed = true)
        trackDao = mockk(relaxed = true)
        albumDao = mockk(relaxed = true)
        favoriteDao = mockk(relaxed = true)
        database = mockk(relaxed = true)
        storageTracker = mockk(relaxed = true)
        downloadScraper = mockk(relaxed = true)
        settingsDataStore = mockk(relaxed = true)
        youtubeRepository = mockk(relaxed = true)
        client = OkHttpClient()

        repo = DownloadRepositoryImpl(
            database = database,
            downloadDao = downloadDao,
            trackDao = trackDao,
            favoriteDao = favoriteDao,
            albumDao = albumDao,
            client = client,
            storageTracker = storageTracker,
            downloadScraper = downloadScraper,
            settingsDataStore = settingsDataStore,
            youtubeRepository = youtubeRepository,
            context = context,
        )

        // Set up DocumentFile to write to in-memory ByteArrayOutputStream and
        // capture the written file names for assertions.
        albumDoc = mockk(relaxed = true) {
            every { findFile(any()) } returns null
            every { createFile(any(), any()) } answers {
                val name = secondArg<String>()
                createdFileNames.add(name)
                val fileMock = mockk<DocumentFile>(relaxed = true)
                val streamUri = mockk<Uri>(relaxed = true)
                every { fileMock.uri } returns streamUri
                val baos = ByteArrayOutputStream()
                capturedStreams[name] = baos
                every { contentResolver.openOutputStream(streamUri) } returns baos
                fileMock
            }
        }
        rootDoc = mockk(relaxed = true) {
            every { findFile(any()) } returns null
            every { createDirectory(any()) } returns albumDoc
        }

        mockkStatic(DocumentFile::class)
        every { DocumentFile.fromTreeUri(any(), any()) } returns rootDoc

        // Real StorageTracker is irrelevant because we're not modifying files
        // through the repo.
    }

    @After fun tearDown() {
        unmockkStatic(DocumentFile::class)
    }

    @Test fun `exportDownloads filters by trackIds when provided`() = runTest {
        val sourceA = makeSourceFile("a.mp3", "AAA")
        val sourceB = makeSourceFile("b.mp3", "BBB")
        val sourceC = makeSourceFile("c.mp3", "CCC")

        coEvery { downloadDao.getAllSync() } returns listOf(
            DownloadEntity(trackId = "ta", albumId = "alb", filePath = sourceA.absolutePath, sizeBytes = 3, format = "mp3-128"),
            DownloadEntity(trackId = "tb", albumId = "alb", filePath = sourceB.absolutePath, sizeBytes = 3, format = "mp3-128"),
            DownloadEntity(trackId = "tc", albumId = "alb", filePath = sourceC.absolutePath, sizeBytes = 3, format = "mp3-128"),
        )
        coEvery { trackDao.getByIdsChunk(any()) } answers {
            firstArg<List<String>>().map { id ->
                trackEntity(id, title = "Title $id")
            }
        }
        coEvery { albumDao.getByIdsChunk(any()) } returns listOf(
            albumEntity("alb", title = "Album", artist = "Artist"),
        )

        val written = repo.exportDownloads(
            destinationUri = "content://tree/whatever",
            trackIds = setOf("ta", "tc"),
            onProgress = { _, _ -> },
        )

        assertThat(written).isEqualTo(2)
        assertThat(createdFileNames).hasSize(2)
        // Only ta + tc should be exported; tb's content must not appear in any
        // stream we captured.
        val allBytes = capturedStreams.values.joinToString("|") { it.toString(Charsets.UTF_8) }
        assertThat(allBytes).contains("AAA")
        assertThat(allBytes).contains("CCC")
        assertThat(allBytes).doesNotContain("BBB")
    }

    @Test fun `exportDownloads with null trackIds exports everything`() = runTest {
        val sourceA = makeSourceFile("a.mp3", "AAA")
        val sourceB = makeSourceFile("b.mp3", "BBB")

        coEvery { downloadDao.getAllSync() } returns listOf(
            DownloadEntity(trackId = "ta", albumId = "alb", filePath = sourceA.absolutePath, sizeBytes = 3, format = "mp3-128"),
            DownloadEntity(trackId = "tb", albumId = "alb", filePath = sourceB.absolutePath, sizeBytes = 3, format = "mp3-128"),
        )
        coEvery { trackDao.getByIdsChunk(any()) } answers {
            firstArg<List<String>>().map { id -> trackEntity(id, title = "Title $id") }
        }
        coEvery { albumDao.getByIdsChunk(any()) } returns listOf(
            albumEntity("alb", title = "Album", artist = "Artist"),
        )

        val written = repo.exportDownloads(
            destinationUri = "content://tree/whatever",
            trackIds = null,
            onProgress = { _, _ -> },
        )

        assertThat(written).isEqualTo(2)
        assertThat(createdFileNames).hasSize(2)
    }

    private fun makeSourceFile(name: String, contents: String): File {
        val f = tempFolder.newFile(name)
        f.writeText(contents)
        return f
    }

    private fun trackEntity(id: String, title: String) = TrackEntity(
        id = id,
        albumId = "alb",
        title = title,
        artist = "Artist",
        trackNumber = 1,
        duration = 200f,
        streamUrl = "https://example.com/$id.mp3",
        artUrl = "https://example.com/$id.jpg",
        albumTitle = "Album",
        source = "bandcamp",
        folderUri = "",
        dateAdded = 0L,
        year = 0,
    )

    private fun albumEntity(id: String, title: String, artist: String) = AlbumEntity(
        id = id,
        url = "https://example.com/$id",
        title = title,
        artist = artist,
        artistUrl = "",
        artUrl = "",
        releaseDate = null,
        about = null,
        tags = "[]",
        saleItemId = null,
        saleItemType = null,
    )

    @Suppress("UNUSED")
    private fun touchRuntime() = RuntimeEnvironment.getApplication()
}
