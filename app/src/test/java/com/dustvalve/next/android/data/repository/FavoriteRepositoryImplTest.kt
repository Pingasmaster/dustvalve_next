package com.dustvalve.next.android.data.repository

import app.cash.turbine.test
import com.dustvalve.next.android.data.local.db.DbTestBase
import com.dustvalve.next.android.domain.model.FavoriteType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FavoriteRepositoryImplTest : DbTestBase() {

    private fun repo() = FavoriteRepositoryImpl(favoriteDao = db.favoriteDao())

    @Test fun `favoriteIds emits on insert and delete, only for the matching type`() = runTest {
        val repo = repo()
        repo.favoriteIds(FavoriteType.TRACK).test {
            assertThat(awaitItem()).isEmpty()

            repo.add("t1", FavoriteType.TRACK)
            assertThat(awaitItem()).containsExactly("t1")

            // A favorite of another type never enters the set (the favorites
            // table invalidation still re-emits, with unchanged content).
            repo.add("a1", FavoriteType.ALBUM)
            assertThat(awaitItem()).containsExactly("t1")

            repo.remove("t1", FavoriteType.TRACK)
            assertThat(awaitItem()).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `add stores the exact persisted key strings - schema compat guard`() = runTest {
        val repo = repo()
        repo.add("t", FavoriteType.TRACK)
        repo.add("al", FavoriteType.ALBUM)
        repo.add("ar", FavoriteType.ARTIST)
        repo.add("yt", FavoriteType.YOUTUBE_PLAYLIST)
        repo.add("sc", FavoriteType.SOUNDCLOUD_PLAYLIST)
        repo.add("co", FavoriteType.COLLECTION)

        val byId = db.favoriteDao().getAllSync().associate { it.id to it.type }
        assertThat(byId).containsExactlyEntriesIn(
            mapOf(
                "t" to "track",
                "al" to "album",
                "ar" to "artist",
                "yt" to "youtube_playlist",
                "sc" to "soundcloud_playlist",
                "co" to "collection",
            ),
        )
    }

    @Test fun `isFavorite is typed - same id different types do not collide`() = runTest {
        val repo = repo()
        assertThat(repo.isFavorite("x", FavoriteType.COLLECTION)).isFalse()

        repo.add("x", FavoriteType.COLLECTION)
        assertThat(repo.isFavorite("x", FavoriteType.COLLECTION)).isTrue()
        assertThat(repo.isFavorite("x", FavoriteType.TRACK)).isFalse()

        repo.add("x", FavoriteType.TRACK)
        assertThat(repo.isFavorite("x", FavoriteType.COLLECTION)).isTrue()
        assertThat(repo.isFavorite("x", FavoriteType.TRACK)).isTrue()

        repo.remove("x", FavoriteType.COLLECTION)
        assertThat(repo.isFavorite("x", FavoriteType.COLLECTION)).isFalse()
        assertThat(repo.isFavorite("x", FavoriteType.TRACK)).isTrue()
    }

    @Test fun `setPinned and setShapeKey update the row in place`() = runTest {
        val repo = repo()
        repo.add("a1", FavoriteType.ALBUM)

        repo.setPinned("a1", FavoriteType.ALBUM, true)
        repo.setShapeKey("a1", FavoriteType.ALBUM, "hex")
        var row = db.favoriteDao().getAllSync().single()
        assertThat(row.isPinned).isTrue()
        assertThat(row.shapeKey).isEqualTo("hex")

        repo.setPinned("a1", FavoriteType.ALBUM, false)
        repo.setShapeKey("a1", FavoriteType.ALBUM, null)
        row = db.favoriteDao().getAllSync().single()
        assertThat(row.isPinned).isFalse()
        assertThat(row.shapeKey).isNull()
    }
}
