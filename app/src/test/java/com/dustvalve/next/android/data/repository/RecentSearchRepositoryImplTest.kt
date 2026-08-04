package com.dustvalve.next.android.data.repository

import app.cash.turbine.test
import com.dustvalve.next.android.data.local.db.DbTestBase
import com.dustvalve.next.android.data.local.db.entity.RecentSearchEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecentSearchRepositoryImplTest : DbTestBase() {

    private fun repo() = RecentSearchRepositoryImpl(recentSearchDao = db.recentSearchDao())

    @Test fun `getRecent maps to newest-first query strings capped at 8`() = runTest {
        for (i in 1..10) {
            db.recentSearchDao().insert(RecentSearchEntity(query = "q$i", source = "bandcamp", searchedAt = i.toLong()))
        }
        // Another source's rows never leak in.
        db.recentSearchDao().insert(RecentSearchEntity(query = "other", source = "youtube", searchedAt = 99L))

        repo().getRecent("bandcamp").test {
            assertThat(awaitItem())
                .containsExactly("q10", "q9", "q8", "q7", "q6", "q5", "q4", "q3")
                .inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `add trims the source history to 20 entries`() = runTest {
        for (i in 1..25) {
            db.recentSearchDao().insert(RecentSearchEntity(query = "q$i", source = "local", searchedAt = i.toLong()))
        }

        repo().add("q26", "local")

        val remaining = db.recentSearchDao().getAllSync().filter { it.source == "local" }.map { it.query }
        assertThat(remaining).hasSize(20)
        assertThat(remaining).contains("q26")
        assertThat(remaining).containsNoneOf("q1", "q2", "q3", "q4", "q5", "q6")
    }

    @Test fun `add without trim keeps uncapped history - the soundcloud contract`() = runTest {
        for (i in 1..25) {
            db.recentSearchDao().insert(RecentSearchEntity(query = "q$i", source = "soundcloud", searchedAt = i.toLong()))
        }

        repo().add("q26", "soundcloud", trim = false)

        assertThat(db.recentSearchDao().getAllSync().filter { it.source == "soundcloud" }).hasSize(26)
    }

    @Test fun `remove and clear are scoped to the given source`() = runTest {
        val repo = repo()
        db.recentSearchDao().insert(RecentSearchEntity(query = "shared", source = "bandcamp"))
        db.recentSearchDao().insert(RecentSearchEntity(query = "shared", source = "youtube"))
        db.recentSearchDao().insert(RecentSearchEntity(query = "other", source = "bandcamp"))

        repo.remove("shared", "bandcamp")
        assertThat(db.recentSearchDao().getAllSync().map { it.source to it.query })
            .containsExactly("youtube" to "shared", "bandcamp" to "other")

        repo.clear("bandcamp")
        assertThat(db.recentSearchDao().getAllSync().map { it.source to it.query })
            .containsExactly("youtube" to "shared")
    }

    @Test fun `clearAllSources clears exactly the four known sources`() = runTest {
        for (source in listOf("bandcamp", "youtube", "soundcloud", "local", "unknown")) {
            db.recentSearchDao().insert(RecentSearchEntity(query = "q", source = source))
        }

        repo().clearAllSources()

        assertThat(db.recentSearchDao().getAllSync().map { it.source }).containsExactly("unknown")
    }
}
