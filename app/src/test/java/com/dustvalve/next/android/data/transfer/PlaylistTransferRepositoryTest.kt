@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.data.transfer

import android.content.Context
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
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
import okhttp3.OkHttpClient
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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

    @Test fun `lightweight export then import round-trips playlist and tracks`() = runBlocking {
        val playlistRepo = mockk<PlaylistRepository>(relaxed = true)
        val repo = PlaylistTransferRepository(
            context = mockk<Context>(relaxed = true),
            playlistRepository = playlistRepo,
            downloadRepository = mockk<DownloadRepository>(relaxed = true),
            trackDao = mockk<TrackDao>(relaxed = true).also { coEvery { it.insertAll(any()) } just Runs },
            downloadDao = mockk<DownloadDao>(relaxed = true),
            client = mockk<OkHttpClient>(relaxed = true),
            ioDispatcher = UnconfinedTestDispatcher(),
        )

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
}
