package com.dustvalve.next.android.data.local.db

import com.dustvalve.next.android.data.local.db.dao.getFavoriteIds
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FavoriteDaoTest : DbTestBase() {

    @Test fun `insert and isFavorite`() = runTest {
        val dao = db.favoriteDao()
        dao.insert(FavoriteEntity(id = "a1", type = "album"))
        assertThat(dao.isFavorite("a1")).isTrue()
        assertThat(dao.isFavorite("other")).isFalse()
    }

    @Test fun `delete removes favorite`() = runTest {
        val dao = db.favoriteDao()
        dao.insert(FavoriteEntity(id = "a1", type = "album"))
        dao.delete("a1")
        assertThat(dao.isFavorite("a1")).isFalse()
    }

    @Test fun `getFavoriteIds chunks across SQLite bind limit`() = runTest {
        val dao = db.favoriteDao()
        // Insert 2000 favorites — bigger than the 900 bind-param chunk size.
        val ids = (1..2000).map { "id_$it" }
        ids.forEach { dao.insert(FavoriteEntity(id = it, type = "track")) }

        val looked = dao.getFavoriteIds(ids)
        assertThat(looked).hasSize(2000)
        assertThat(looked.toSet()).isEqualTo(ids.toSet())
    }

    @Test fun `getFavoriteIds on empty list returns empty`() = runTest {
        val dao = db.favoriteDao()
        assertThat(dao.getFavoriteIds(emptyList())).isEmpty()
    }

    @Test fun `setPinned and setShapeKey update in place`() = runTest {
        val dao = db.favoriteDao()
        dao.insert(FavoriteEntity(id = "a1", type = "album"))
        dao.setPinned("a1", true)
        dao.setShapeKey("a1", "circle")

        val matching = dao.getFavoriteIds(listOf("a1"))
        assertThat(matching).containsExactly("a1")
    }
}
