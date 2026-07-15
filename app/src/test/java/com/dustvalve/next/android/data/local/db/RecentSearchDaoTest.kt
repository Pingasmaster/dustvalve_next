package com.dustvalve.next.android.data.local.db

import com.dustvalve.next.android.data.local.db.entity.RecentSearchEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecentSearchDaoTest : DbTestBase() {

    private fun s(query: String, source: String = "bandcamp", at: Long = 0L) = RecentSearchEntity(query, source, at)

    @Test fun `re-searching a query replaces the row instead of duplicating`() = runTest {
        val dao = db.recentSearchDao()
        dao.insert(s("beatles", at = 1))
        dao.insert(s("beatles", at = 5))
        val all = dao.getAllSync()
        assertThat(all).hasSize(1)
        assertThat(all.single().searchedAt).isEqualTo(5)
    }

    @Test fun `same query in different sources are separate rows`() = runTest {
        val dao = db.recentSearchDao()
        dao.insert(s("beatles", source = "bandcamp"))
        dao.insert(s("beatles", source = "youtube"))
        assertThat(dao.getAllSync()).hasSize(2)
    }

    @Test fun `getRecent returns newest first limited and source-scoped`() = runTest {
        val dao = db.recentSearchDao()
        (1..10).forEach { dao.insert(s("q$it", at = it.toLong())) }
        dao.insert(s("other-source", source = "youtube", at = 99))

        val recent = dao.getRecent("bandcamp", limit = 3).first()
        assertThat(recent.map { it.query }).containsExactly("q10", "q9", "q8").inOrder()
    }

    @Test fun `delete removes one query in one source`() = runTest {
        val dao = db.recentSearchDao()
        dao.insert(s("beatles", source = "bandcamp"))
        dao.insert(s("beatles", source = "youtube"))
        dao.delete("beatles", "bandcamp")
        assertThat(dao.getAllSync().single().source).isEqualTo("youtube")
    }

    @Test fun `deleteOld keeps only the newest keepCount per source`() = runTest {
        val dao = db.recentSearchDao()
        (1..30).forEach { dao.insert(s("q$it", at = it.toLong())) }
        dao.insert(s("yt", source = "youtube", at = 1))

        dao.deleteOld("bandcamp", keepCount = 20)

        val bandcamp = dao.getAllSync().filter { it.source == "bandcamp" }
        assertThat(bandcamp).hasSize(20)
        assertThat(bandcamp.minOf { it.searchedAt }).isEqualTo(11)
        // Other sources are untouched.
        assertThat(dao.getAllSync().any { it.source == "youtube" }).isTrue()
    }

    @Test fun `clearAll only clears the given source`() = runTest {
        val dao = db.recentSearchDao()
        dao.insert(s("a", source = "bandcamp"))
        dao.insert(s("b", source = "youtube"))
        dao.clearAll("bandcamp")
        assertThat(dao.getAllSync().single().source).isEqualTo("youtube")
    }
}
