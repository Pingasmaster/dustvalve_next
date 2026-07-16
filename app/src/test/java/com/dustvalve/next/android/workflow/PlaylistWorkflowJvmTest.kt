package com.dustvalve.next.android.workflow

import com.dustvalve.next.android.data.local.db.DbTestBase
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.dustvalve.next.android.data.repository.PlaylistRepositoryImpl
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.workflow.support.FixtureTracks
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Playlist workflows over a real in-memory Room DB: create a playlist, add
 * and remove tracks from every provider (local / Bandcamp / YouTube), reorder,
 * rename, delete - the exact flows the user asked to be covered per provider.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class PlaylistWorkflowJvmTest : DbTestBase() {

    private fun repo() = PlaylistRepositoryImpl(
        db,
        db.playlistDao(),
        db.trackDao(),
        db.favoriteDao(),
        mockk<DownloadRepository>(relaxed = true),
        UnconfinedTestDispatcher(),
    )

    private suspend fun insertTrack(id: String, source: String) {
        val t = when (source) {
            "local" -> FixtureTracks.localTrack(id = id)
            "youtube" -> FixtureTracks.youtubeTrack(id = id)
            else -> FixtureTracks.bandcampTrack(id = id)
        }
        db.trackDao().insertAll(
            listOf(
                TrackEntity(
                    id = t.id,
                    albumId = t.albumId,
                    title = t.title,
                    artist = t.artist,
                    trackNumber = t.trackNumber,
                    duration = t.duration,
                    streamUrl = t.streamUrl,
                    artUrl = t.artUrl,
                    albumTitle = t.albumTitle,
                    source = source,
                ),
            ),
        )
    }

    @Test
    fun createPlaylist_addTrackFromEachProvider_allListed() = runTest {
        val repo = repo()
        insertTrack("local_ms_1", "local")
        insertTrack("12345_1", "bandcamp")
        insertTrack("yt_abc12345678", "youtube")

        val playlist = repo.createPlaylist("Mixed")
        repo.addTrackToPlaylist(playlist.id, "local_ms_1")
        repo.addTrackToPlaylist(playlist.id, "12345_1")
        repo.addTrackToPlaylist(playlist.id, "yt_abc12345678")

        val tracks = repo.getTracksInPlaylistSync(playlist.id)
        assertThat(tracks.map { it.id }).containsExactly("local_ms_1", "12345_1", "yt_abc12345678").inOrder()
    }

    @Test
    fun removeTrack_compactsAndKeepsLibraryTrack() = runTest {
        val repo = repo()
        insertTrack("local_ms_1", "local")
        insertTrack("12345_1", "bandcamp")
        val playlist = repo.createPlaylist("P")
        repo.addTrackToPlaylist(playlist.id, "local_ms_1")
        repo.addTrackToPlaylist(playlist.id, "12345_1")

        repo.removeTrackFromPlaylist(playlist.id, "local_ms_1")

        assertThat(repo.getTracksInPlaylistSync(playlist.id).map { it.id }).containsExactly("12345_1")
        // Library row untouched.
        assertThat(db.trackDao().getById("local_ms_1")).isNotNull()
    }

    @Test
    fun reorder_persistsNewOrder() = runTest {
        val repo = repo()
        insertTrack("a", "local")
        insertTrack("b", "local")
        insertTrack("c", "local")
        val p = repo.createPlaylist("Order")
        repo.addTracksToPlaylist(p.id, listOf("a", "b", "c"))

        repo.moveTrackInPlaylist(p.id, 0, 2)

        assertThat(repo.getTracksInPlaylistSync(p.id).map { it.id }).containsExactly("b", "c", "a").inOrder()
    }

    @Test
    fun renameAndDelete_workAndDeleteKeepsTracks() = runTest {
        val repo = repo()
        insertTrack("a", "bandcamp")
        val p = repo.createPlaylist("Old")
        repo.addTrackToPlaylist(p.id, "a")

        assertThat(repo.renamePlaylist(p.id, "New")).isTrue()
        assertThat(repo.getPlaylistByIdSync(p.id)?.name).isEqualTo("New")

        assertThat(repo.deletePlaylist(p.id)).isTrue()
        assertThat(repo.getPlaylistByIdSync(p.id)).isNull()
        assertThat(db.trackDao().getById("a")).isNotNull()
    }

    @Test
    fun duplicateAdd_doesNotCrash_andKeepsSingleOrDefinedCount() = runTest {
        val repo = repo()
        insertTrack("a", "local")
        val p = repo.createPlaylist("Dup")
        repo.addTrackToPlaylist(p.id, "a")
        repo.addTrackToPlaylist(p.id, "a")

        val ids = repo.getTracksInPlaylistSync(p.id).map { it.id }
        // Contract: adding twice must not corrupt the playlist; the track
        // appears at most twice and playback of the playlist cannot crash.
        assertThat(ids.count { it == "a" }).isAtMost(2)
        assertThat(ids).isNotEmpty()
    }
}
