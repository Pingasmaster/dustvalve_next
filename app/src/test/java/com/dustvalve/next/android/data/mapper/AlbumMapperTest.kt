package com.dustvalve.next.android.data.mapper

import com.dustvalve.next.android.data.local.db.entity.AlbumEntity
import com.dustvalve.next.android.data.local.db.entity.ArtistEntity
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.PurchaseInfo
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlbumMapperTest {

    private fun sampleAlbum(
        tags: List<String> = listOf("rock", "indie"),
        purchase: PurchaseInfo? = null,
    ) = Album(
        id = "a1",
        url = "https://x.bandcamp.com/album/foo",
        title = "Foo",
        artist = "Bar",
        artistUrl = "https://x.bandcamp.com",
        artUrl = "https://f4.bcbits.com/img/a1_10.jpg",
        releaseDate = "2020-01-01",
        about = "About text",
        tracks = emptyList(),
        tags = tags,
        autoDownload = true,
        purchaseInfo = purchase,
    )

    @Test fun `album to entity encodes tags as json`() {
        val e = sampleAlbum().toEntity()
        assertThat(e.tags).isEqualTo("""["rock","indie"]""")
        assertThat(e.autoDownload).isTrue()
        assertThat(e.saleItemId).isNull()
        assertThat(e.saleItemType).isNull()
    }

    @Test fun `album with purchase info serialized`() {
        val e = sampleAlbum(purchase = PurchaseInfo(saleItemId = 42L, saleItemType = "a")).toEntity()
        assertThat(e.saleItemId).isEqualTo(42L)
        assertThat(e.saleItemType).isEqualTo("a")
    }

    @Test fun `entity to domain parses json tags`() {
        val e = sampleAlbum(tags = listOf("a", "b")).toEntity()
        val a = e.toDomain(tracks = emptyList(), isFavorite = true)
        assertThat(a.tags).containsExactly("a", "b").inOrder()
        assertThat(a.isFavorite).isTrue()
    }

    @Test fun `entity to domain handles legacy csv tags`() {
        val e = sampleAlbum(tags = emptyList()).toEntity().copy(tags = "rock, indie, metal")
        val a = e.toDomain(tracks = emptyList(), isFavorite = false)
        assertThat(a.tags).containsExactly("rock", "indie", "metal").inOrder()
    }

    @Test fun `entity to domain handles blank tags`() {
        val e = sampleAlbum(tags = emptyList()).toEntity().copy(tags = "")
        val a = e.toDomain(tracks = emptyList(), isFavorite = false)
        assertThat(a.tags).isEmpty()
    }

    @Test fun `entity to domain caps tags at 50`() {
        val big = (1..75).map { "t$it" }
        val e = sampleAlbum(tags = big).toEntity()
        val a = e.toDomain(tracks = emptyList(), isFavorite = false)
        assertThat(a.tags).hasSize(50)
        assertThat(a.tags.first()).isEqualTo("t1")
        assertThat(a.tags.last()).isEqualTo("t50")
    }

    @Test fun `entity purchaseInfo set only when both fields present`() {
        val e = sampleAlbum().toEntity().copy(saleItemId = 7L, saleItemType = "a")
        val a = e.toDomain(emptyList(), isFavorite = false)
        assertThat(a.purchaseInfo).isEqualTo(PurchaseInfo(7L, "a"))
    }

    @Test fun `entity purchaseInfo null if one field missing`() {
        val e = sampleAlbum().toEntity().copy(saleItemId = 7L, saleItemType = null)
        val a = e.toDomain(emptyList(), isFavorite = false)
        assertThat(a.purchaseInfo).isNull()
    }

    @Test fun `album round trip preserves fields`() {
        val original = sampleAlbum(tags = listOf("a", "b"), purchase = PurchaseInfo(99L, "t"))
        val roundTripped = original.toEntity().toDomain(tracks = emptyList(), isFavorite = original.isFavorite)
        assertThat(roundTripped.id).isEqualTo(original.id)
        assertThat(roundTripped.url).isEqualTo(original.url)
        assertThat(roundTripped.title).isEqualTo(original.title)
        assertThat(roundTripped.artist).isEqualTo(original.artist)
        assertThat(roundTripped.artistUrl).isEqualTo(original.artistUrl)
        assertThat(roundTripped.artUrl).isEqualTo(original.artUrl)
        assertThat(roundTripped.releaseDate).isEqualTo(original.releaseDate)
        assertThat(roundTripped.about).isEqualTo(original.about)
        assertThat(roundTripped.tags).isEqualTo(original.tags)
        assertThat(roundTripped.autoDownload).isEqualTo(original.autoDownload)
        assertThat(roundTripped.purchaseInfo).isEqualTo(original.purchaseInfo)
    }

    private fun sampleTrack() = Track(
        id = "t1",
        albumId = "a1",
        title = "Song",
        artist = "Bar",
        artistUrl = "https://x.bandcamp.com",
        trackNumber = 3,
        duration = 225f,
        streamUrl = "https://stream/foo.mp3",
        artUrl = "https://art/foo.jpg",
        albumTitle = "Foo",
        isFavorite = true,
        source = TrackSource.YOUTUBE,
        folderUri = "content://folder",
        dateAdded = 1234L,
        year = 2020,
    )

    @Test fun `track round trip preserves source and fields`() {
        val original = sampleTrack()
        val roundTripped = original.toEntity().toDomain(isFavorite = original.isFavorite)
        assertThat(roundTripped).isEqualTo(original)
    }

    @Test fun `track entity toDomain unknown source falls back to bandcamp`() {
        val e = sampleTrack().toEntity().copy(source = "xyz")
        val t = e.toDomain(isFavorite = false)
        assertThat(t.source).isEqualTo(TrackSource.BANDCAMP)
    }

    private fun sampleArtist() = Artist(
        id = "ar1",
        name = "Artist",
        url = "https://x.bandcamp.com",
        imageUrl = "https://x/img",
        bio = "Bio",
        location = "Paris",
        albums = listOf(
            Album(
                id = "a1", url = "u", title = "t", artist = "Artist",
                artistUrl = "u", artUrl = "", releaseDate = null,
                about = null, tracks = emptyList(), tags = emptyList()
            ),
            Album(
                id = "a2", url = "u", title = "t", artist = "Artist",
                artistUrl = "u", artUrl = "", releaseDate = null,
                about = null, tracks = emptyList(), tags = emptyList()
            ),
        ),
        autoDownload = true,
    )

    @Test fun `artist to entity encodes album id order`() {
        val e = sampleArtist().toEntity()
        assertThat(e.albumIdOrder).isEqualTo("""["a1","a2"]""")
        assertThat(e.autoDownload).isTrue()
    }

    @Test fun `artist entity to domain with albums and favorite flag`() {
        val e = sampleArtist().toEntity()
        val albums = sampleArtist().albums
        val a = e.toDomain(albums, isFavorite = true)
        assertThat(a.isFavorite).isTrue()
        assertThat(a.albums).hasSize(2)
        assertThat(a.autoDownload).isTrue()
    }
}
