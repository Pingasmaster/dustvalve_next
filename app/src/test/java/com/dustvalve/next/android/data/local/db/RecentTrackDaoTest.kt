package com.dustvalve.next.android.data.local.db

import com.dustvalve.next.android.data.local.db.entity.RecentTrackEntity
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecentTrackDaoTest : DbTestBase() {

    private fun track(id: String) = TrackEntity(
        id = id, albumId = "a", title = id, artist = "x",
        trackNumber = 1, duration = 1f, streamUrl = null, artUrl = "",
        albumTitle = "", source = "bandcamp",
    )

    @Test fun `getRecent returns newest first`() = runTest {
        db.trackDao().insertAll((1..5).map { track("t$it") })
        val recent = db.recentTrackDao()
        recent.insert(RecentTrackEntity("t1", 1L))
        recent.insert(RecentTrackEntity("t2", 2L))
        recent.insert(RecentTrackEntity("t3", 3L))
        val got = recent.getRecent(10).map { it.trackId }
        assertThat(got).containsExactly("t3", "t2", "t1").inOrder()
    }

    @Test fun `deleteOld keeps newest N`() = runTest {
        db.trackDao().insertAll((1..5).map { track("t$it") })
        val recent = db.recentTrackDao()
        (1..5).forEach { recent.insert(RecentTrackEntity("t$it", it.toLong())) }
        recent.deleteOld(keepCount = 3)
        val got = recent.getRecent(10).map { it.trackId }
        assertThat(got).containsExactly("t5", "t4", "t3").inOrder()
    }

    @Test fun `insert replaces same track id`() = runTest {
        db.trackDao().insertAll(listOf(track("t1")))
        val recent = db.recentTrackDao()
        recent.insert(RecentTrackEntity("t1", 100L))
        recent.insert(RecentTrackEntity("t1", 500L))
        val got = recent.getRecent(10)
        assertThat(got).hasSize(1)
        assertThat(got[0].playedAt).isEqualTo(500L)
    }
}
