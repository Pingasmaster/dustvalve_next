package com.dustvalve.next.android.data.local.db

import com.dustvalve.next.android.data.local.db.entity.ArtistEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArtistDaoTest : DbTestBase() {

    private fun ar(id: String, url: String = "https://$id.bandcamp.com") = ArtistEntity(
        id = id,
        name = id,
        url = url,
        imageUrl = null,
        bio = null,
        location = null,
    )

    @Test fun `insert replaces on conflict`() = runTest {
        val dao = db.artistDao()
        dao.insert(ar("x"))
        dao.insert(ar("x").copy(name = "renamed"))
        assertThat(dao.getById("x")?.name).isEqualTo("renamed")
        assertThat(dao.getAllSync()).hasSize(1)
    }

    @Test fun `getByUrl finds the matching artist`() = runTest {
        val dao = db.artistDao()
        dao.insert(ar("one"))
        dao.insert(ar("two"))
        assertThat(dao.getByUrl("https://two.bandcamp.com")?.id).isEqualTo("two")
        assertThat(dao.getByUrl("https://missing.bandcamp.com")).isNull()
    }

    @Test fun `setAutoDownload flips only the target artist`() = runTest {
        val dao = db.artistDao()
        dao.insert(ar("one"))
        dao.insert(ar("two"))
        dao.setAutoDownload("one", true)
        assertThat(dao.getById("one")?.autoDownload).isTrue()
        assertThat(dao.getById("two")?.autoDownload).isFalse()
    }

    @Test fun `updateCachedAt bumps the timestamp`() = runTest {
        val dao = db.artistDao()
        dao.insert(ar("one").copy(cachedAt = 1L))
        dao.updateCachedAt("one", cachedAt = 777L)
        assertThat(dao.getById("one")?.cachedAt).isEqualTo(777L)
    }

    @Test fun `deleteAll empties the table`() = runTest {
        val dao = db.artistDao()
        dao.insert(ar("one"))
        dao.insert(ar("two"))
        dao.deleteAll()
        assertThat(dao.getAllSync()).isEmpty()
    }
}
