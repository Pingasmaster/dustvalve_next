@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.local.db.DbTestBase
import com.dustvalve.next.android.data.local.db.dao.ArtistDao
import com.dustvalve.next.android.data.local.db.entity.AlbumEntity
import com.dustvalve.next.android.data.local.db.entity.ArtistEntity
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.network.OpportunisticRefreshGate
import com.dustvalve.next.android.data.remote.DustvalveArtistScraper
import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.repository.AlbumRepository
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers Bandcamp artist cache SWR (unmetered revalidate on open) and the
 * remote-source paths (cacheRemoteArtist / favoriteRemoteArtist /
 * unfavoriteArtist) against real DAOs; scraper and cross-repo deps mocked.
 */
@RunWith(RobolectricTestRunner::class)
class ArtistRepositoryImplTest : DbTestBase() {

    private fun TestScope.repo(
        artistDao: ArtistDao = db.artistDao(),
        scraper: DustvalveArtistScraper = mockk(relaxed = true),
        refreshGate: OpportunisticRefreshGate = OpportunisticRefreshGate.ALWAYS,
    ) = ArtistRepositoryImpl(
        database = db,
        artistDao = artistDao,
        albumDao = db.albumDao(),
        favoriteDao = db.favoriteDao(),
        trackDao = db.trackDao(),
        artistScraper = scraper,
        downloadRepository = mockk<DownloadRepository>(relaxed = true),
        albumRepository = mockk<AlbumRepository>(relaxed = true),
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        refreshGate = refreshGate,
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

    private fun stubAlbum(id: String, artistUrl: String) = Album(
        id = id,
        url = "https://example.bandcamp.com/album/$id",
        title = id,
        artist = "Artist",
        artistUrl = artistUrl,
        artUrl = "",
        releaseDate = null,
        about = null,
        tracks = emptyList(),
        tags = emptyList(),
    )

    private fun albumEntity(id: String, artistUrl: String) = AlbumEntity(
        id = id,
        url = "https://example.bandcamp.com/album/$id",
        title = id,
        artist = "Artist",
        artistUrl = artistUrl,
        artUrl = "",
        releaseDate = null,
        about = null,
        tags = "[]",
    )

    @Test fun `getArtistDetailFlow emits cache then re-emits when a new album appears`() = runTest {
        val url = "https://moeshop.bandcamp.com"
        val artistId = "bc_moe"
        db.artistDao().insert(
            ArtistEntity(
                id = artistId,
                name = "moe shop",
                url = url,
                imageUrl = "https://img/old",
                bio = null,
                location = null,
                albumIdOrder = """["old"]""",
                // Fresh within the old 24h TTL - must still revalidate.
                cachedAt = System.currentTimeMillis(),
            ),
        )
        db.albumDao().insert(albumEntity("old", url))
        // Orphan stub left behind after a delisting; must not appear in UI.
        db.albumDao().insert(albumEntity("removed", url))

        val scraper = mockk<DustvalveArtistScraper>()
        coEvery { scraper.scrapeArtist(url) } returns Artist(
            id = artistId,
            name = "moe shop",
            url = url,
            imageUrl = "https://img/new",
            bio = null,
            location = null,
            albums = listOf(stubAlbum("new", url), stubAlbum("old", url)),
        )

        val emissions = repo(scraper = scraper).getArtistDetailFlow(url).toList()

        assertThat(emissions).hasSize(2)
        assertThat(emissions[0].albums.map { it.id }).containsExactly("old").inOrder()
        assertThat(emissions[1].albums.map { it.id }).containsExactly("new", "old").inOrder()
        assertThat(emissions[1].imageUrl).isEqualTo("https://img/new")
        coVerify(exactly = 1) { scraper.scrapeArtist(url) }
    }

    @Test fun `getArtistDetailFlow does not re-emit when discography is unchanged`() = runTest {
        val url = "https://artist.bandcamp.com"
        val artistId = "bc_a"
        db.artistDao().insert(
            ArtistEntity(
                id = artistId,
                name = "A",
                url = url,
                imageUrl = "https://img",
                bio = null,
                location = null,
                albumIdOrder = """["a1"]""",
                cachedAt = System.currentTimeMillis(),
            ),
        )
        db.albumDao().insert(albumEntity("a1", url))

        val scraper = mockk<DustvalveArtistScraper>()
        coEvery { scraper.scrapeArtist(url) } returns Artist(
            id = artistId,
            name = "A",
            url = url,
            imageUrl = "https://img",
            bio = null,
            location = null,
            albums = listOf(stubAlbum("a1", url)),
        )

        val emissions = repo(scraper = scraper).getArtistDetailFlow(url).toList()

        assertThat(emissions).hasSize(1)
        assertThat(emissions[0].albums.map { it.id }).containsExactly("a1")
        coVerify(exactly = 1) { scraper.scrapeArtist(url) }
    }

    @Test fun `getArtistDetail always revalidates even with a fresh cache`() = runTest {
        val url = "https://artist.bandcamp.com"
        val artistId = "bc_a"
        db.artistDao().insert(
            ArtistEntity(
                id = artistId,
                name = "A",
                url = url,
                imageUrl = "https://img",
                bio = null,
                location = null,
                albumIdOrder = """["a1"]""",
                cachedAt = System.currentTimeMillis(),
            ),
        )
        db.albumDao().insert(albumEntity("a1", url))

        val scraper = mockk<DustvalveArtistScraper>()
        coEvery { scraper.scrapeArtist(url) } returns Artist(
            id = artistId,
            name = "A",
            url = url,
            imageUrl = "https://img",
            bio = null,
            location = null,
            albums = listOf(stubAlbum("a1", url), stubAlbum("a2", url)),
        )

        val artist = repo(scraper = scraper).getArtistDetail(url)

        assertThat(artist.albums.map { it.id }).containsExactly("a1", "a2").inOrder()
        coVerify(exactly = 1) { scraper.scrapeArtist(url) }
    }

    @Test fun `getArtistDetail skips revalidate on metered when cache exists`() = runTest {
        val url = "https://artist.bandcamp.com"
        val artistId = "bc_a"
        db.artistDao().insert(
            ArtistEntity(
                id = artistId,
                name = "A",
                url = url,
                imageUrl = "https://img",
                bio = null,
                location = null,
                albumIdOrder = """["a1"]""",
                cachedAt = System.currentTimeMillis(),
            ),
        )
        db.albumDao().insert(albumEntity("a1", url))

        val scraper = mockk<DustvalveArtistScraper>(relaxed = true)
        val artist = repo(scraper = scraper, refreshGate = OpportunisticRefreshGate.NEVER)
            .getArtistDetail(url)

        assertThat(artist.albums.map { it.id }).containsExactly("a1")
        coVerify(exactly = 0) { scraper.scrapeArtist(any()) }
    }

    @Test fun `getArtistDetailFlow skips scrape on metered after emitting cache`() = runTest {
        val url = "https://artist.bandcamp.com"
        val artistId = "bc_a"
        db.artistDao().insert(
            ArtistEntity(
                id = artistId,
                name = "A",
                url = url,
                imageUrl = "https://img",
                bio = null,
                location = null,
                albumIdOrder = """["a1"]""",
                cachedAt = System.currentTimeMillis(),
            ),
        )
        db.albumDao().insert(albumEntity("a1", url))

        val scraper = mockk<DustvalveArtistScraper>(relaxed = true)
        val emissions = repo(scraper = scraper, refreshGate = OpportunisticRefreshGate.NEVER)
            .getArtistDetailFlow(url)
            .toList()

        assertThat(emissions).hasSize(1)
        assertThat(emissions[0].albums.map { it.id }).containsExactly("a1")
        coVerify(exactly = 0) { scraper.scrapeArtist(any()) }
    }

    @Test fun `empty scrape does not wipe a cached discography`() = runTest {
        val url = "https://artist.bandcamp.com"
        val artistId = "bc_a"
        db.artistDao().insert(
            ArtistEntity(
                id = artistId,
                name = "A",
                url = url,
                imageUrl = "https://img",
                bio = null,
                location = null,
                albumIdOrder = """["a1"]""",
                cachedAt = System.currentTimeMillis(),
            ),
        )
        db.albumDao().insert(albumEntity("a1", url))

        val scraper = mockk<DustvalveArtistScraper>()
        coEvery { scraper.scrapeArtist(url) } returns Artist(
            id = artistId,
            name = "A",
            url = url,
            imageUrl = "https://img",
            bio = null,
            location = null,
            albums = emptyList(),
        )

        val artist = repo(scraper = scraper).getArtistDetail(url)

        assertThat(artist.albums.map { it.id }).containsExactly("a1")
        val row = db.artistDao().getByUrl(url)
        assertThat(row?.albumIdOrder).isEqualTo("""["a1"]""")
    }

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
