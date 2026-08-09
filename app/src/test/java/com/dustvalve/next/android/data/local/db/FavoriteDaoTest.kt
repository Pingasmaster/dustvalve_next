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

    @Test fun `insert and isFavorite are typed`() = runTest {
        val dao = db.favoriteDao()
        dao.insert(FavoriteEntity(id = "a1", type = "album"))
        assertThat(dao.isFavorite("a1", "album")).isTrue()
        assertThat(dao.isFavorite("a1", "track")).isFalse()
        assertThat(dao.isFavorite("other", "album")).isFalse()
    }

    @Test fun `same id can exist for album and track without clobber`() = runTest {
        val dao = db.favoriteDao()
        dao.insert(FavoriteEntity(id = "shared", type = "album"))
        dao.insert(FavoriteEntity(id = "shared", type = "track"))
        assertThat(dao.isFavorite("shared", "album")).isTrue()
        assertThat(dao.isFavorite("shared", "track")).isTrue()
        dao.delete("shared", "album")
        assertThat(dao.isFavorite("shared", "album")).isFalse()
        assertThat(dao.isFavorite("shared", "track")).isTrue()
    }

    @Test fun `delete removes favorite of that type only`() = runTest {
        val dao = db.favoriteDao()
        dao.insert(FavoriteEntity(id = "a1", type = "album"))
        dao.delete("a1", "album")
        assertThat(dao.isFavorite("a1", "album")).isFalse()
    }

    @Test fun `getFavoriteIds chunks across SQLite bind limit`() = runTest {
        val dao = db.favoriteDao()
        // Insert 2000 favorites - bigger than the 900 bind-param chunk size.
        val ids = (1..2000).map { "id_$it" }
        ids.forEach { dao.insert(FavoriteEntity(id = it, type = "track")) }

        val looked = dao.getFavoriteIds("track", ids)
        assertThat(looked).hasSize(2000)
        assertThat(looked.toSet()).isEqualTo(ids.toSet())
    }

    @Test fun `getFavoriteIds on empty list returns empty`() = runTest {
        val dao = db.favoriteDao()
        assertThat(dao.getFavoriteIds("track", emptyList())).isEmpty()
    }

    @Test fun `setPinned and setShapeKey update in place`() = runTest {
        val dao = db.favoriteDao()
        dao.insert(FavoriteEntity(id = "a1", type = "album"))
        dao.setPinned("a1", "album", true)
        dao.setShapeKey("a1", "album", "circle")

        val matching = dao.getFavoriteIds("album", listOf("a1"))
        assertThat(matching).containsExactly("a1")
        val row = dao.getAllSync().single()
        assertThat(row.isPinned).isTrue()
        assertThat(row.shapeKey).isEqualTo("circle")
    }
}
