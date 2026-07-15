package com.dustvalve.next.android.data.local.db

import com.dustvalve.next.android.data.local.db.dao.getByIds
import com.dustvalve.next.android.data.local.db.entity.AlbumEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumDaoTest : DbTestBase() {

    private fun a(id: String, url: String = "https://x.bandcamp.com/album/$id", artistUrl: String = "https://x.bandcamp.com") = AlbumEntity(
        id = id, url = url, title = id, artist = "A", artistUrl = artistUrl,
        artUrl = "", releaseDate = null, about = null, tags = "[]",
    )

    @Test fun `insert replaces on conflict`() = runTest {
        val dao = db.albumDao()
        dao.insert(a("a1"))
        dao.insert(a("a1").copy(title = "updated"))
        assertThat(dao.getById("a1")?.title).isEqualTo("updated")
        assertThat(dao.getAll()).hasSize(1)
    }

    @Test fun `insertIfAbsent keeps the existing row`() = runTest {
        val dao = db.albumDao()
        dao.insert(a("a1"))
        dao.insertIfAbsent(a("a1").copy(title = "should not overwrite"))
        assertThat(dao.getById("a1")?.title).isEqualTo("a1")
    }

    @Test fun `getByUrl and getByArtistUrl filter correctly`() = runTest {
        val dao = db.albumDao()
        dao.insert(a("a1", artistUrl = "https://one.bandcamp.com"))
        dao.insert(a("a2", artistUrl = "https://one.bandcamp.com"))
        dao.insert(a("a3", artistUrl = "https://two.bandcamp.com"))

        assertThat(dao.getByUrl("https://x.bandcamp.com/album/a2")?.id).isEqualTo("a2")
        assertThat(dao.getByUrl("https://nope")).isNull()
        assertThat(dao.getByArtistUrl("https://one.bandcamp.com").map { it.id })
            .containsExactly("a1", "a2")
    }

    @Test fun `getByIds chunks across large id lists`() = runTest {
        val dao = db.albumDao()
        val ids = (1..1500).map { "id_$it" }
        ids.forEach { dao.insert(a(it)) }
        assertThat(dao.getByIds(ids)).hasSize(1500)
        assertThat(dao.getByIds(emptyList())).isEmpty()
    }

    @Test fun `setAutoDownload flips only the target album`() = runTest {
        val dao = db.albumDao()
        dao.insert(a("a1"))
        dao.insert(a("a2"))
        dao.setAutoDownload("a1", true)
        assertThat(dao.getById("a1")?.autoDownload).isTrue()
        assertThat(dao.getById("a2")?.autoDownload).isFalse()
    }

    @Test fun `updatePurchaseInfo persists sale item`() = runTest {
        val dao = db.albumDao()
        dao.insert(a("a1"))
        dao.updatePurchaseInfo("a1", saleItemId = 42L, saleItemType = "a")
        val row = dao.getById("a1")
        assertThat(row?.saleItemId).isEqualTo(42L)
        assertThat(row?.saleItemType).isEqualTo("a")
    }

    @Test fun `updateCachedAt bumps the timestamp`() = runTest {
        val dao = db.albumDao()
        dao.insert(a("a1").copy(cachedAt = 1L))
        dao.updateCachedAt("a1", cachedAt = 999L)
        assertThat(dao.getById("a1")?.cachedAt).isEqualTo(999L)
    }

    @Test fun `delete removes only the target`() = runTest {
        val dao = db.albumDao()
        dao.insert(a("a1"))
        dao.insert(a("a2"))
        dao.delete("a1")
        assertThat(dao.getById("a1")).isNull()
        assertThat(dao.getById("a2")).isNotNull()
    }

    @Test fun `deleteAll empties the table`() = runTest {
        val dao = db.albumDao()
        dao.insert(a("a1"))
        dao.insert(a("a2"))
        dao.deleteAll()
        assertThat(dao.getAll()).isEmpty()
    }
}
