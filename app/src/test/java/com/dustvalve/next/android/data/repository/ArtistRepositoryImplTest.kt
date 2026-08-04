@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.local.db.DbTestBase
import com.dustvalve.next.android.data.local.db.dao.ArtistDao
import com.dustvalve.next.android.data.local.db.entity.ArtistEntity
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.remote.DustvalveArtistScraper
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.repository.AlbumRepository
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the remote-source paths (cacheRemoteArtist / favoriteRemoteArtist /
 * unfavoriteArtist) against real DAOs; scraper and cross-repo deps mocked.
 */
@RunWith(RobolectricTestRunner::class)
class ArtistRepositoryImplTest : DbTestBase() {

    private fun TestScope.repo(artistDao: ArtistDao = db.artistDao()) = ArtistRepositoryImpl(
        database = db,
        artistDao = artistDao,
        albumDao = db.albumDao(),
        favoriteDao = db.favoriteDao(),
        trackDao = db.trackDao(),
        artistScraper = mockk<DustvalveArtistScraper>(relaxed = true),
        downloadRepository = mockk<DownloadRepository>(relaxed = true),
        albumRepository = mockk<AlbumRepository>(relaxed = true),
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private fun remoteArtist(url: String) = Artist(
        id = url,
        name = "Channel",
        url = url,
        imageUrl = "https://yt.example/img.jpg",
        bio = "bio",
        location = null,
        albums = emptyList(),
    )

    @Test fun `cacheRemoteArtist refreshes the artist row without touching favorites`() = runTest {
        val url = "https://www.youtube.com/channel/UC0"
        db.artistDao().insert(
            ArtistEntity(id = url, name = "Old Name", url = url, imageUrl = null, bio = null, location = null, source = "youtube"),
        )

        repo().cacheRemoteArtist(remoteArtist(url), source = "youtube")

        // The insert is a REPLACE: repeat visits refresh cached metadata.
        val row = db.artistDao().getByUrl(url)
        assertThat(row?.name).isEqualTo("Channel")
        assertThat(row?.imageUrl).isEqualTo("https://yt.example/img.jpg")
        // No favorites side effect - this is the load-path cache, not a favorite.
        assertThat(db.favoriteDao().getAllSync()).isEmpty()
    }

    @Test fun `cacheRemoteArtist swallows artist-row insert failures - best-effort contract`() = runTest {
        val failingArtistDao = mockk<ArtistDao>()
        coEvery { failingArtistDao.insert(any()) } throws IllegalStateException("row insert broke")

        repo(artistDao = failingArtistDao)
            .cacheRemoteArtist(remoteArtist("https://www.youtube.com/channel/UC0"), source = "youtube")

        assertThat(db.favoriteDao().getAllSync()).isEmpty()
    }

    @Test fun `favoriteRemoteArtist persists the artist row with its source and an artist favorite`() = runTest {
        val url = "https://www.youtube.com/channel/UC1"

        repo().favoriteRemoteArtist(remoteArtist(url), source = "youtube")

        val row = db.artistDao().getByUrl(url)
        assertThat(row).isNotNull()
        assertThat(row?.id).isEqualTo(url)
        assertThat(row?.name).isEqualTo("Channel")
        assertThat(row?.imageUrl).isEqualTo("https://yt.example/img.jpg")
        assertThat(row?.source).isEqualTo("youtube")

        val favorite = db.favoriteDao().getAllSync().single()
        assertThat(favorite.id).isEqualTo(url)
        assertThat(favorite.type).isEqualTo("artist")
    }

    @Test fun `favoriteRemoteArtist still favorites when the artist-row insert fails - best-effort contract`() = runTest {
        val url = "https://www.youtube.com/channel/UC2"
        val failingArtistDao = mockk<ArtistDao>()
        coEvery { failingArtistDao.insert(any()) } throws IllegalStateException("row insert broke")

        repo(artistDao = failingArtistDao).favoriteRemoteArtist(remoteArtist(url), source = "youtube")

        val favorite = db.favoriteDao().getAllSync().single()
        assertThat(favorite.id).isEqualTo(url)
        assertThat(favorite.type).isEqualTo("artist")
    }

    @Test fun `unfavoriteArtist removes only the favorites row`() = runTest {
        val url = "https://www.youtube.com/channel/UC3"
        db.artistDao().insert(
            ArtistEntity(id = url, name = "Channel", url = url, imageUrl = null, bio = null, location = null, source = "youtube"),
        )
        db.favoriteDao().insert(FavoriteEntity(id = url, type = "artist"))
        db.favoriteDao().insert(FavoriteEntity(id = "other", type = "artist"))

        repo().unfavoriteArtist(url)

        // The cached artist row survives; only this artist's favorite is gone.
        assertThat(db.artistDao().getByUrl(url)).isNotNull()
        assertThat(db.favoriteDao().getAllSync().map { it.id }).containsExactly("other")
    }
}
