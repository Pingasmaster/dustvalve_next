@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.data.transfer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.DownloadEntity
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.dustvalve.next.android.data.storage.folder.FolderSnapshotSerializer
import com.dustvalve.next.android.data.storage.folder.PlaylistSnapshot
import com.dustvalve.next.android.data.storage.folder.TrackSnapshot
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import org.junit.Assert.assertThrows
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
        trackDao: TrackDao = mockk<TrackDao>(relaxed = true).also { coEvery { it.insertAll(any()) } just Runs },
        downloadDao: DownloadDao = mockk(relaxed = true),
    ) = PlaylistTransferRepository(
        context = context,
        playlistRepository = playlistRepo,
        downloadRepository = mockk<DownloadRepository>(relaxed = true),
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
                zip.write(FolderSnapshotSerializer.json.encodeToString(manifest).toByteArray())
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
                zip.write(FolderSnapshotSerializer.json.encodeToString(manifest).toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val ex = assertThrows(IllegalStateException::class.java) {
            runBlocking { repo.import(ByteArrayInputStream(zipBytes)) }
        }
        assertThat(ex.message).contains("version ${PlaylistBundleManifest.SUPPORTED_VERSION + 1}")
        coVerify(exactly = 0) { playlistRepo.createPlaylist(any(), any(), any()) }
    }
}
