@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.data.transfer

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.DownloadEntity
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.repository.TrackDownloadGateway
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class PlaylistTransferRepositoryTest {

    private lateinit var database: DustvalveNextDatabase

    @Before fun setUp() {
        database = mockk()
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @After fun tearDown() = unmockkAll()

    private fun track(id: String, title: String) = Track(
        id = id,
        albumId = "album_$id",
        title = title,
        artist = "Artist",
        trackNumber = 1,
        duration = 100f,
        streamUrl = "https://example.com/$id.mp3",
        artUrl = "https://example.com/$id.jpg",
        albumTitle = "Album",
    )

    private fun repo(
        context: Context,
        playlistRepo: PlaylistRepository,
        trackDao: TrackDao = mockk<TrackDao>(relaxed = true).also {
            coEvery { it.insertAll(any()) } just Runs
            coEvery { it.getByIdsChunk(any()) } returns emptyList()
        },
        downloadDao: DownloadDao = mockk(relaxed = true),
        downloadRepository: DownloadRepository = mockk(relaxed = true),
        trackDownloadGateway: TrackDownloadGateway? = null,
    ) = PlaylistTransferRepository(
        context = context,
        playlistRepository = playlistRepo,
        downloadRepository = downloadRepository,
        trackDownloadGateway = trackDownloadGateway ?: object : TrackDownloadGateway {
            override suspend fun downloadTrack(
                track: Track,
                formatOverride: com.dustvalve.next.android.domain.model.AudioFormat?,
            ) {
                downloadRepository.downloadTrack(track, formatOverride)
            }
        },
        database = database,
        trackDao = trackDao,
        downloadDao = downloadDao,
        client = mockk<OkHttpClient>(relaxed = true),
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    @Test fun `lightweight export then import round-trips playlist and tracks`() = runBlocking {
        val playlistRepo = mockk<PlaylistRepository>(relaxed = true)
        val repo = repo(mockk<Context>(relaxed = true), playlistRepo)

        coEvery { playlistRepo.getPlaylistByIdSync("p1") } returns Playlist(id = "p1", name = "My Mix")
        coEvery { playlistRepo.getTracksInPlaylistSync("p1") } returns listOf(track("t1", "One"), track("t2", "Two"))

        val baos = ByteArrayOutputStream()
        repo.export("p1", offline = false, out = baos)

        // Re-import the produced bundle.
        coEvery { playlistRepo.createPlaylist(any(), any(), any()) } returns Playlist(id = "p2", name = "My Mix")
        val ids = slot<List<String>>()
        coEvery { playlistRepo.addTracksToPlaylist(any(), capture(ids)) } just Runs

        val result = repo.import(ByteArrayInputStream(baos.toByteArray()))

        assertThat(result.name).isEqualTo("My Mix")
        coVerify { playlistRepo.createPlaylist("My Mix", any(), any()) }
        assertThat(ids.captured).containsExactly("t1", "t2").inOrder()
    }

    @Test fun `offline import streams audio to disk, registers the download, and cleans its temp dir`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val playlistRepo = mockk<PlaylistRepository>(relaxed = true)
        val downloadDao = mockk<DownloadDao>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        val trackRows = slot<List<TrackEntity>>()
        coEvery { trackDao.insertAll(capture(trackRows)) } just Runs
        val repo = repo(ctx, playlistRepo, trackDao = trackDao, downloadDao = downloadDao)

        // Large enough to prove multi-buffer streaming, small enough for CI.
        val audioBytes = ByteArray(96 * 1024) { (it % 251).toByte() }
        val coverBytes = ByteArray(2048) { 7 }
        val manifest = PlaylistBundleManifest(
            offline = true,
            playlist = PlaylistSnapshot(id = "p1", name = "Road Trip"),
            entries = listOf(
                BundleEntry(
                    track = TrackSnapshot(
                        id = "t1",
                        albumId = "a1",
                        title = "One",
                        artist = "Artist",
                        trackNumber = 1,
                        duration = 100f,
                        artUrl = "https://example.com/a.jpg",
                        albumTitle = "Alb",
                    ),
                    audioFile = "audio/t1.mp3",
                    coverFile = "covers/a1.jpg",
                    format = "mp3-128",
                ),
            ),
        )
        // Audio + cover BEFORE the manifest: import must handle entries
        // arriving ahead of the metadata that references them.
        val zipBytes = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { zip ->
                zip.putNextEntry(ZipEntry("audio/t1.mp3"))
                zip.write(audioBytes)
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("covers/a1.jpg"))
                zip.write(coverBytes)
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(PlaylistBundleSerializer.json.encodeToString(manifest).toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        coEvery { playlistRepo.createPlaylist(any(), any(), any()) } returns Playlist(id = "p2", name = "Road Trip")
        val inserted = slot<DownloadEntity>()
        coEvery { downloadDao.insert(capture(inserted)) } just Runs

        repo.import(ByteArrayInputStream(zipBytes))

        // Audio landed byte-identical at the registered path.
        assertThat(inserted.captured.trackId).isEqualTo("t1")
        val audioFile = File(inserted.captured.filePath)
        assertThat(audioFile.readBytes()).isEqualTo(audioBytes)
        assertThat(inserted.captured.sizeBytes).isEqualTo(audioBytes.size.toLong())
        // Cover persisted locally and the track row points at it.
        assertThat(trackRows.captured.single().artUrl).startsWith("file:")
        // The spill directory is removed after import.
        val leftovers = ctx.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("playlist_import_") }
        assertThat(leftovers).isEmpty()
    }

    @Test fun `import rejects bundles with a newer format version`() {
        val playlistRepo = mockk<PlaylistRepository>(relaxed = true)
        val repo = repo(mockk<Context>(relaxed = true), playlistRepo)
        val manifest = PlaylistBundleManifest(
            version = PlaylistBundleManifest.SUPPORTED_VERSION + 1,
            offline = false,
            playlist = PlaylistSnapshot(id = "p1", name = "Future Mix"),
        )
        val zipBytes = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(PlaylistBundleSerializer.json.encodeToString(manifest).toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val ex = assertThrows(IllegalStateException::class.java) {
            runBlocking { repo.import(ByteArrayInputStream(zipBytes)) }
        }
        assertThat(ex.message).contains("version ${PlaylistBundleManifest.SUPPORTED_VERSION + 1}")
        coVerify(exactly = 0) { playlistRepo.createPlaylist(any(), any(), any()) }
    }

    @Test fun `offline export clears offline flag when downloadTrack fails`() = runBlocking {
        val playlistRepo = mockk<PlaylistRepository>(relaxed = true)
        val downloadRepo = mockk<DownloadRepository>()
        coEvery { playlistRepo.getPlaylistByIdSync("p1") } returns Playlist(id = "p1", name = "Mix")
        coEvery { playlistRepo.getTracksInPlaylistSync("p1") } returns listOf(track("t1", "One"))
        coEvery { downloadRepo.getDownloadInfo("t1") } returns null
        // Export routes downloads through TrackDownloadGateway, which the
        // repo() helper wires to DownloadRepository.downloadTrack.
        coEvery { downloadRepo.downloadTrack(any(), any()) } throws java.io.IOException("nope")

        val repo = repo(
            context = mockk(relaxed = true),
            playlistRepo = playlistRepo,
            downloadRepository = downloadRepo,
        )
        val baos = ByteArrayOutputStream()
        repo.export("p1", offline = true, out = baos)

        val manifestJson = java.util.zip.ZipInputStream(ByteArrayInputStream(baos.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "manifest.json") {
                    return@use zip.readBytes().decodeToString()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            error("no manifest")
        }
        val manifest = PlaylistBundleSerializer.json.decodeFromString<PlaylistBundleManifest>(manifestJson)
        assertThat(manifest.offline).isFalse()
        assertThat(manifest.entries.single().audioFile).isNull()
    }

    @Test fun `mergeTrackEntityForImport keeps richer metadata over blank bundle fields`() {
        val existing = TrackEntity(
            id = "t1",
            albumId = "a1",
            title = "Rich Title",
            artist = "Rich Artist",
            artistUrl = "https://artist",
            trackNumber = 3,
            duration = 120f,
            streamUrl = "https://stream",
            artUrl = "https://art",
            albumTitle = "Rich Album",
            albumUrl = "https://album",
            year = 2020,
        )
        val thin = existing.copy(
            title = "",
            artist = "",
            artistUrl = "",
            albumTitle = "",
            albumUrl = "",
            streamUrl = null,
            artUrl = "",
            duration = 0f,
            year = 0,
            trackNumber = 0,
        )
        val merged = mergeTrackEntityForImport(thin, existing)
        assertThat(merged.title).isEqualTo("Rich Title")
        assertThat(merged.artist).isEqualTo("Rich Artist")
        assertThat(merged.artistUrl).isEqualTo("https://artist")
        assertThat(merged.albumTitle).isEqualTo("Rich Album")
        assertThat(merged.albumUrl).isEqualTo("https://album")
        assertThat(merged.streamUrl).isEqualTo("https://stream")
        assertThat(merged.artUrl).isEqualTo("https://art")
        assertThat(merged.duration).isEqualTo(120f)
        assertThat(merged.year).isEqualTo(2020)
        assertThat(merged.trackNumber).isEqualTo(3)
    }
}
