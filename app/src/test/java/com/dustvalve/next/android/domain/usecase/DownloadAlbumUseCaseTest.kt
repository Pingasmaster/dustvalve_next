package com.dustvalve.next.android.domain.usecase

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.AlbumRepository
import com.dustvalve.next.android.domain.repository.DownloadProgressReporter
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.IOException

class DownloadAlbumUseCaseTest {

    private lateinit var downloadRepo: DownloadRepository
    private lateinit var albumRepo: AlbumRepository
    private lateinit var notificationCenter: DownloadProgressReporter
    private lateinit var useCase: DownloadAlbumUseCase

    @Before fun setUp() {
        downloadRepo = mockk(relaxed = true)
        albumRepo = mockk(relaxed = true)
        notificationCenter = mockk(relaxed = true)
        // withBatch must actually run its block, otherwise nothing downloads.
        coEvery {
            notificationCenter.withBatch(any(), any(), any(), captureLambda<suspend () -> Unit>())
        } coAnswers {
            lambda<suspend () -> Unit>().captured.invoke()
        }
        useCase = DownloadAlbumUseCase(downloadRepo, albumRepo, notificationCenter)
    }

    private fun track(id: String, streamUrl: String? = "https://s/$id") = Track(
        id = id, albumId = "al", title = id, artist = "A", trackNumber = 1,
        duration = 60f, streamUrl = streamUrl, artUrl = "", albumTitle = "Alb",
    )

    private fun album(id: String, tracks: List<Track> = listOf(track("$id-t1"))) = Album(
        id = id, url = "https://x.bandcamp.com/album/$id", title = id, artist = "A",
        artistUrl = "https://x.bandcamp.com", artUrl = "", releaseDate = null,
        about = null, tracks = tracks, tags = emptyList(),
    )

    private fun artist(albums: List<Album>) = Artist(
        id = "ar",
        name = "Artist",
        url = "https://x.bandcamp.com",
        imageUrl = null,
        bio = null,
        location = null,
        albums = albums,
    )

    @Test fun `invoke delegates album download to the repository`() = runTest {
        val a = album("a1")
        useCase(a)
        coVerify(exactly = 1) { downloadRepo.downloadAlbum(a) }
    }

    @Test fun `downloadPlaylist with no tracks does nothing`() = runTest {
        useCase.downloadPlaylist("Mix", emptyList())
        coVerify(exactly = 0) { notificationCenter.withBatch(any(), any(), any(), any<suspend () -> Unit>()) }
        coVerify(exactly = 0) { downloadRepo.downloadTrack(any()) }
    }

    @Test fun `downloadPlaylist downloads every track inside one batch`() = runTest {
        val tracks = listOf(track("t1"), track("t2"), track("t3"))
        useCase.downloadPlaylist("Mix", tracks)
        coVerify(exactly = 1) {
            notificationCenter.withBatch(
                "Mix",
                3,
                DownloadProgressReporter.BatchKind.PLAYLIST,
                any<suspend () -> Unit>(),
            )
        }
        tracks.forEach { coVerify(exactly = 1) { downloadRepo.downloadTrack(it) } }
    }

    @Test fun `downloadPlaylist keeps going when one track fails then surfaces the failure`() {
        val tracks = listOf(track("t1"), track("t2"), track("t3"))
        coEvery { downloadRepo.downloadTrack(tracks[1]) } throws IOException("boom")
        val ex = assertThrows(IOException::class.java) {
            runTest { useCase.downloadPlaylist("Mix", tracks) }
        }
        // The remaining tracks still downloaded before the aggregate error.
        assertThat(ex.message).contains("1 of 3")
        assertThat(ex.message).contains("boom")
        coVerify(exactly = 1) { downloadRepo.downloadTrack(tracks[0]) }
        coVerify(exactly = 1) { downloadRepo.downloadTrack(tracks[2]) }
    }

    @Test fun `downloadPlaylist throws when every track fails`() {
        val tracks = listOf(track("t1"), track("t2"))
        coEvery { downloadRepo.downloadTrack(any()) } throws IOException("boom")
        val ex = assertThrows(IOException::class.java) {
            runTest { useCase.downloadPlaylist("Mix", tracks) }
        }
        assertThat(ex.message).contains("2 of 2")
    }

    @Test fun `downloadArtist with no albums throws`() {
        assertThrows(IOException::class.java) {
            runTest { useCase.downloadArtist(artist(emptyList())) }
        }
    }

    @Test fun `downloadArtist resolves details and downloads each album`() = runTest {
        val stub1 = album("a1")
        val stub2 = album("a2")
        val full1 = album("a1", tracks = listOf(track("t1"), track("t2")))
        val full2 = album("a2", tracks = listOf(track("t3"), track("t4", streamUrl = null)))
        coEvery { albumRepo.getAlbumDetail(stub1.url) } returns full1
        coEvery { albumRepo.getAlbumDetail(stub2.url) } returns full2

        useCase.downloadArtist(artist(listOf(stub1, stub2)))

        // Total counts only streamable tracks: 2 from full1 + 1 from full2.
        coVerify(exactly = 1) {
            notificationCenter.withBatch(
                "Artist",
                3,
                DownloadProgressReporter.BatchKind.ARTIST,
                any<suspend () -> Unit>(),
            )
        }
        coVerify(exactly = 1) { downloadRepo.downloadAlbum(full1) }
        coVerify(exactly = 1) { downloadRepo.downloadAlbum(full2) }
    }

    @Test fun `downloadArtist tolerates partial failures`() = runTest {
        val stub1 = album("a1")
        val stub2 = album("a2")
        coEvery { albumRepo.getAlbumDetail(stub1.url) } throws IOException("detail fail")
        coEvery { albumRepo.getAlbumDetail(stub2.url) } returns stub2

        useCase.downloadArtist(artist(listOf(stub1, stub2)))

        coVerify(exactly = 1) { downloadRepo.downloadAlbum(stub2) }
    }

    @Test fun `downloadArtist throws when every album fails`() {
        val stub1 = album("a1")
        val stub2 = album("a2")
        coEvery { albumRepo.getAlbumDetail(any()) } throws IOException("detail fail")

        assertThrows(IOException::class.java) {
            runTest { useCase.downloadArtist(artist(listOf(stub1, stub2))) }
        }
        coVerify(exactly = 0) { downloadRepo.downloadAlbum(any()) }
    }

    @Test fun `deleteArtistDownloads deletes every album`() = runTest {
        val a1 = album("a1")
        val a2 = album("a2")
        useCase.deleteArtistDownloads(artist(listOf(a1, a2)))
        coVerify(exactly = 1) { downloadRepo.deleteAlbumDownloads("a1") }
        coVerify(exactly = 1) { downloadRepo.deleteAlbumDownloads("a2") }
    }

    @Test fun `single-item deletes delegate to the repository`() = runTest {
        useCase.deleteAlbumDownloads("a1")
        coVerify { downloadRepo.deleteAlbumDownloads("a1") }
        useCase.deleteTrackDownload("t1")
        coVerify { downloadRepo.deleteDownload("t1") }
    }
}
