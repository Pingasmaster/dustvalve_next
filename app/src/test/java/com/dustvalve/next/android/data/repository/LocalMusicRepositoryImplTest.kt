package com.dustvalve.next.android.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.DbTestBase
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.dustvalve.next.android.data.local.scanner.LocalMusicScanner
import com.dustvalve.next.android.data.local.scanner.MediaStoreScanner
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the local-track read surface (getLocalTracks / getLocalTrack /
 * searchLocalTracks / deleteTrackRows) against real DAOs. The scanner/
 * WorkManager sides are exercised elsewhere and stay mocked here.
 */
@RunWith(RobolectricTestRunner::class)
class LocalMusicRepositoryImplTest : DbTestBase() {

    private fun repo() = LocalMusicRepositoryImpl(
        context = ApplicationProvider.getApplicationContext<Context>(),
        scanner = mockk<LocalMusicScanner>(relaxed = true),
        mediaStoreScanner = mockk<MediaStoreScanner>(relaxed = true),
        settingsDataStore = mockk<SettingsDataStore>(relaxed = true),
        trackDao = db.trackDao(),
        favoriteDao = db.favoriteDao(),
    )

    private fun localTrack(id: String, title: String = id) = TrackEntity(
        id = id, albumId = "local_album", title = title, artist = "Artist",
        trackNumber = 1, duration = 1f, streamUrl = null, artUrl = "",
        albumTitle = "Album", source = "local",
    )

    @Test fun `getLocalTracks re-emits when a favorite toggles - the combine contract`() = runTest {
        db.trackDao().insertAll(listOf(localTrack("t1")))

        repo().getLocalTracks().test {
            assertThat(awaitItem().single().isFavorite).isFalse()

            db.favoriteDao().insert(FavoriteEntity(id = "t1", type = "track"))
            assertThat(awaitItem().single().isFavorite).isTrue()

            db.favoriteDao().delete("t1")
            assertThat(awaitItem().single().isFavorite).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `getLocalTracks only returns local-source rows`() = runTest {
        db.trackDao().insertAll(
            listOf(
                localTrack("t1"),
                localTrack("t2").copy(source = "bandcamp"),
            ),
        )

        repo().getLocalTracks().test {
            assertThat(awaitItem().map { it.id }).containsExactly("t1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `searchLocalTracks matches title artist and album and decorates favorites`() = runTest {
        db.trackDao().insertAll(
            listOf(
                localTrack("t1", title = "Sunrise"),
                localTrack("t2", title = "Moonset"),
            ),
        )
        db.favoriteDao().insert(FavoriteEntity(id = "t1", type = "track"))

        val results = repo().searchLocalTracks("Sun")

        assertThat(results.map { it.id }).containsExactly("t1")
        assertThat(results.single().isFavorite).isTrue()
    }

    @Test fun `getLocalTrack returns null for missing ids and the decorated track otherwise`() = runTest {
        val repo = repo()
        assertThat(repo.getLocalTrack("missing")).isNull()

        db.trackDao().insertAll(listOf(localTrack("t1")))
        assertThat(repo.getLocalTrack("t1")?.isFavorite).isFalse()

        db.favoriteDao().insert(FavoriteEntity(id = "t1", type = "track"))
        assertThat(repo.getLocalTrack("t1")?.isFavorite).isTrue()
    }

    @Test fun `deleteTrackRows deletes only the given ids`() = runTest {
        db.trackDao().insertAll(listOf(localTrack("t1"), localTrack("t2"), localTrack("t3")))

        repo().deleteTrackRows(listOf("t1", "t3"))

        assertThat(db.trackDao().getAllSync().map { it.id }).containsExactly("t2")
    }
}
