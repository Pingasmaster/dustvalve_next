package com.dustvalve.next.android.player

import com.dustvalve.next.android.domain.model.Track
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueManagerTest {

    private lateinit var qm: QueueManager

    @Before fun setUp() {
        // QueueManager uses Dispatchers.Main.immediate for its derived flow scope.
        // Use Unconfined so stateIn updates happen synchronously.
        Dispatchers.setMain(kotlinx.coroutines.Dispatchers.Unconfined)
        qm = QueueManager()
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun track(id: String) = Track(
        id = id,
        albumId = "al",
        title = "Title $id",
        artist = "Artist",
        trackNumber = 1,
        duration = 100f,
        streamUrl = "https://x/$id",
        artUrl = "",
        albumTitle = "Album",
    )

    private fun tracks(vararg ids: String) = ids.map(::track)

    // --- setQueue ---

    @Test fun `setQueue empty yields index -1`() {
        qm.setQueue(emptyList())
        assertThat(qm.queue.value).isEmpty()
        assertThat(qm.currentIndex.value).isEqualTo(-1)
        assertThat(qm.currentTrack.value).isNull()
    }

    @Test fun `setQueue clamps startIndex above last`() {
        qm.setQueue(tracks("a", "b", "c"), startIndex = 99)
        assertThat(qm.currentIndex.value).isEqualTo(2)
    }

    @Test fun `setQueue clamps negative startIndex`() {
        qm.setQueue(tracks("a", "b", "c"), startIndex = -5)
        assertThat(qm.currentIndex.value).isEqualTo(0)
    }

    @Test fun `setQueue with single track`() {
        qm.setQueue(tracks("a"), startIndex = 0)
        assertThat(qm.currentTrack.value?.id).isEqualTo("a")
    }

    // --- addToQueue ---

    @Test fun `addToQueue on empty queue sets index to 0`() {
        qm.addToQueue(track("a"))
        assertThat(qm.currentIndex.value).isEqualTo(0)
        assertThat(qm.queue.value).hasSize(1)
    }

    @Test fun `addToQueue on populated keeps current index`() {
        qm.setQueue(tracks("a", "b"), startIndex = 1)
        qm.addToQueue(track("c"))
        assertThat(qm.currentIndex.value).isEqualTo(1)
        assertThat(qm.queue.value.map { it.id }).containsExactly("a", "b", "c").inOrder()
    }

    // --- playNext ---

    @Test fun `playNext on empty becomes single-track queue`() {
        qm.playNext(track("a"))
        assertThat(qm.queue.value).hasSize(1)
        assertThat(qm.currentIndex.value).isEqualTo(0)
    }

    @Test fun `playNext inserts after current`() {
        qm.setQueue(tracks("a", "b", "c"), startIndex = 1) // current = b
        qm.playNext(track("z"))
        assertThat(qm.queue.value.map { it.id }).containsExactly("a", "b", "z", "c").inOrder()
        assertThat(qm.currentIndex.value).isEqualTo(1)
    }

    @Test fun `playNext at end of queue`() {
        qm.setQueue(tracks("a", "b"), startIndex = 1)
        qm.playNext(track("z"))
        assertThat(qm.queue.value.map { it.id }).containsExactly("a", "b", "z").inOrder()
        assertThat(qm.currentIndex.value).isEqualTo(1)
    }

    // --- removeFromQueue ---

    @Test fun `removeFromQueue out of range no-op`() {
        qm.setQueue(tracks("a", "b"), startIndex = 0)
        qm.removeFromQueue(5)
        assertThat(qm.queue.value).hasSize(2)
        qm.removeFromQueue(-1)
        assertThat(qm.queue.value).hasSize(2)
    }

    @Test fun `removeFromQueue before current decrements index`() {
        qm.setQueue(tracks("a", "b", "c"), startIndex = 2)
        qm.removeFromQueue(0)
        assertThat(qm.currentIndex.value).isEqualTo(1)
        assertThat(qm.currentTrack.value?.id).isEqualTo("c")
    }

    @Test fun `removeFromQueue at current keeps index pointing to next`() {
        qm.setQueue(tracks("a", "b", "c"), startIndex = 1)
        qm.removeFromQueue(1)
        assertThat(qm.currentIndex.value).isEqualTo(1)
        assertThat(qm.currentTrack.value?.id).isEqualTo("c")
    }

    @Test fun `removeFromQueue last element when current is last`() {
        qm.setQueue(tracks("a", "b", "c"), startIndex = 2)
        qm.removeFromQueue(2)
        assertThat(qm.currentIndex.value).isEqualTo(1)
        assertThat(qm.currentTrack.value?.id).isEqualTo("b")
    }

    @Test fun `removeFromQueue after current keeps index`() {
        qm.setQueue(tracks("a", "b", "c"), startIndex = 0)
        qm.removeFromQueue(2)
        assertThat(qm.currentIndex.value).isEqualTo(0)
    }

    @Test fun `removeFromQueue last remaining yields -1`() {
        qm.setQueue(tracks("a"), startIndex = 0)
        qm.removeFromQueue(0)
        assertThat(qm.currentIndex.value).isEqualTo(-1)
        assertThat(qm.queue.value).isEmpty()
    }

    // --- moveItem ---

    @Test fun `moveItem from equal to current`() {
        qm.setQueue(tracks("a", "b", "c", "d"), startIndex = 1) // current b
        qm.moveItem(1, 3)
        assertThat(qm.queue.value.map { it.id }).containsExactly("a", "c", "d", "b").inOrder()
        assertThat(qm.currentIndex.value).isEqualTo(3)
    }

    @Test fun `moveItem with current in range from less than to`() {
        qm.setQueue(tracks("a", "b", "c", "d"), startIndex = 2) // current c
        qm.moveItem(0, 3)
        assertThat(qm.queue.value.map { it.id }).containsExactly("b", "c", "d", "a").inOrder()
        assertThat(qm.currentIndex.value).isEqualTo(1)
    }

    @Test fun `moveItem with current in range from greater than to`() {
        qm.setQueue(tracks("a", "b", "c", "d"), startIndex = 1) // current b
        qm.moveItem(3, 0)
        assertThat(qm.queue.value.map { it.id }).containsExactly("d", "a", "b", "c").inOrder()
        assertThat(qm.currentIndex.value).isEqualTo(2)
    }

    @Test fun `moveItem current outside range`() {
        qm.setQueue(tracks("a", "b", "c", "d"), startIndex = 0) // current a
        qm.moveItem(2, 3)
        assertThat(qm.currentIndex.value).isEqualTo(0)
    }

    @Test fun `moveItem out of range no-op`() {
        qm.setQueue(tracks("a", "b"), startIndex = 0)
        qm.moveItem(0, 5)
        assertThat(qm.queue.value.map { it.id }).containsExactly("a", "b").inOrder()
    }

    // --- next / previous / hasNext / hasPrevious ---

    @Test fun `next advances`() {
        qm.setQueue(tracks("a", "b", "c"), startIndex = 0)
        assertThat(qm.hasNext()).isTrue()
        val n = qm.next()
        assertThat(n?.id).isEqualTo("b")
        assertThat(qm.currentIndex.value).isEqualTo(1)
    }

    @Test fun `next returns null at end`() {
        qm.setQueue(tracks("a"), startIndex = 0)
        assertThat(qm.hasNext()).isFalse()
        assertThat(qm.next()).isNull()
    }

    @Test fun `previous moves back`() {
        qm.setQueue(tracks("a", "b"), startIndex = 1)
        assertThat(qm.hasPrevious()).isTrue()
        assertThat(qm.previous()?.id).isEqualTo("a")
        assertThat(qm.currentIndex.value).isEqualTo(0)
    }

    @Test fun `previous returns null at start`() {
        qm.setQueue(tracks("a"), startIndex = 0)
        assertThat(qm.hasPrevious()).isFalse()
        assertThat(qm.previous()).isNull()
    }

    @Test fun `next and previous on empty queue`() {
        assertThat(qm.next()).isNull()
        assertThat(qm.previous()).isNull()
        assertThat(qm.hasNext()).isFalse()
        assertThat(qm.hasPrevious()).isFalse()
    }

    @Test fun `skipToIndex valid`() {
        qm.setQueue(tracks("a", "b", "c"), startIndex = 0)
        val t = qm.skipToIndex(2)
        assertThat(t?.id).isEqualTo("c")
        assertThat(qm.currentIndex.value).isEqualTo(2)
    }

    @Test fun `skipToIndex invalid no change`() {
        qm.setQueue(tracks("a", "b"), startIndex = 0)
        assertThat(qm.skipToIndex(5)).isNull()
        assertThat(qm.currentIndex.value).isEqualTo(0)
    }

    // --- shuffle / unshuffle ---

    @Test fun `shuffle keeps current track at index 0`() {
        qm.setQueue(tracks("a", "b", "c", "d", "e"), startIndex = 2) // current c
        qm.shuffle()
        assertThat(qm.currentIndex.value).isEqualTo(0)
        assertThat(qm.currentTrack.value?.id).isEqualTo("c")
        assertThat(qm.queue.value.map { it.id }).containsExactly("a", "b", "c", "d", "e")
    }

    @Test fun `shuffle no-op on empty or single`() {
        qm.shuffle()
        assertThat(qm.queue.value).isEmpty()

        qm.setQueue(tracks("a"), startIndex = 0)
        qm.shuffle()
        assertThat(qm.queue.value.map { it.id }).containsExactly("a")
    }

    @Test fun `unshuffle restores original and finds current`() {
        qm.setQueue(tracks("a", "b", "c", "d", "e"), startIndex = 2)
        qm.shuffle()
        qm.unshuffle()
        assertThat(qm.queue.value.map { it.id }).containsExactly("a", "b", "c", "d", "e").inOrder()
        assertThat(qm.currentTrack.value?.id).isEqualTo("c")
    }

    @Test fun `unshuffle without shuffle is no-op`() {
        qm.setQueue(tracks("a", "b"), startIndex = 0)
        qm.unshuffle()
        assertThat(qm.queue.value.map { it.id }).containsExactly("a", "b").inOrder()
    }

    // --- bug-fix regressions ---

    @Test fun `addToQueue after shuffle preserves added track on unshuffle`() {
        qm.setQueue(tracks("a", "b", "c"), startIndex = 0)
        qm.shuffle()
        qm.addToQueue(track("z"))
        val beforeUnshuffle = qm.queue.value.map { it.id }
        qm.unshuffle()
        // Unshuffle must be a no-op after mutation. The new track stays in the queue.
        assertThat(qm.queue.value.map { it.id }).isEqualTo(beforeUnshuffle)
        assertThat(qm.queue.value.map { it.id }).contains("z")
    }

    @Test fun `removeFromQueue after shuffle keeps removal on unshuffle`() {
        qm.setQueue(tracks("a", "b", "c"), startIndex = 0)
        qm.shuffle()
        // Remove the last item of the shuffled queue (always safe regardless of shuffle result).
        qm.removeFromQueue(qm.queue.value.lastIndex)
        val beforeUnshuffle = qm.queue.value.map { it.id }
        qm.unshuffle()
        assertThat(qm.queue.value.map { it.id }).isEqualTo(beforeUnshuffle)
        assertThat(qm.queue.value).hasSize(2)
    }

    @Test fun `moveItem after shuffle keeps move on unshuffle`() {
        qm.setQueue(tracks("a", "b", "c", "d"), startIndex = 0)
        qm.shuffle()
        qm.moveItem(0, qm.queue.value.lastIndex)
        val beforeUnshuffle = qm.queue.value.map { it.id }
        qm.unshuffle()
        assertThat(qm.queue.value.map { it.id }).isEqualTo(beforeUnshuffle)
    }

    @Test fun `setQueue after shuffle discards original`() {
        qm.setQueue(tracks("a", "b", "c"), startIndex = 0)
        qm.shuffle()
        qm.setQueue(tracks("x", "y"), startIndex = 0)
        qm.unshuffle()
        assertThat(qm.queue.value.map { it.id }).containsExactly("x", "y").inOrder()
    }

    @Test fun `playNext after shuffle keeps inserted track on unshuffle`() {
        qm.setQueue(tracks("a", "b", "c"), startIndex = 0)
        qm.shuffle()
        qm.playNext(track("inserted"))
        val beforeUnshuffle = qm.queue.value.map { it.id }
        qm.unshuffle()
        assertThat(qm.queue.value.map { it.id }).isEqualTo(beforeUnshuffle)
        assertThat(qm.queue.value.map { it.id }).contains("inserted")
    }

    // --- clear / release / reinitialize ---

    @Test fun `clear resets state`() {
        qm.setQueue(tracks("a", "b"), startIndex = 1)
        qm.shuffle()
        qm.clear()
        assertThat(qm.queue.value).isEmpty()
        assertThat(qm.currentIndex.value).isEqualTo(-1)
        // After clear, unshuffle is safe no-op
        qm.unshuffle()
        assertThat(qm.queue.value).isEmpty()
    }

    @Test fun `release preserves queue across service teardown`() {
        // H1 regression: PlaybackService.onDestroy also runs for the 5-minute
        // idle-stop timer; release() must NOT erase the queue, or a long pause
        // silently wipes the session and hides the mini player.
        qm.setQueue(tracks("a", "b"), startIndex = 1)
        qm.release()
        assertThat(qm.queue.value.map { it.id }).containsExactly("a", "b").inOrder()
        assertThat(qm.currentIndex.value).isEqualTo(1)
        assertThat(qm.currentTrack.value?.id).isEqualTo("b")
        // Reinitialize (service restart) keeps the preserved state.
        qm.reinitialize()
        assertThat(qm.currentTrack.value?.id).isEqualTo("b")
        // Explicit clear (user dismiss) still works.
        qm.clear()
        assertThat(qm.queue.value).isEmpty()
        qm.setQueue(tracks("c"), startIndex = 0)
        assertThat(qm.currentTrack.value?.id).isEqualTo("c")
    }

    // --- queue entries (stable uid identity) ---

    @Test fun `entries have unique stable uids even for duplicate track ids`() {
        qm.setQueue(tracks("a", "a", "b"), startIndex = 0)
        val entries = qm.entries.value
        assertThat(entries.map { it.track.id }).containsExactly("a", "a", "b").inOrder()
        assertThat(entries.map { it.uid }.toSet()).hasSize(3)
    }

    @Test fun `addToQueue duplicate track gets a fresh uid`() {
        qm.setQueue(tracks("a"), startIndex = 0)
        val firstUid = qm.entries.value[0].uid
        qm.addToQueue(track("a"))
        val uids = qm.entries.value.map { it.uid }
        assertThat(uids).hasSize(2)
        assertThat(uids[0]).isEqualTo(firstUid)
        assertThat(uids[1]).isNotEqualTo(firstUid)
    }

    @Test fun `moveItem carries uids with the entries`() {
        qm.setQueue(tracks("a", "b", "c"), startIndex = 0)
        val uidC = qm.entries.value[2].uid
        qm.moveItem(2, 0)
        assertThat(qm.entries.value[0].uid).isEqualTo(uidC)
        assertThat(qm.entries.value[0].track.id).isEqualTo("c")
    }

    @Test fun `removeEntry resolves by uid against the live queue`() {
        qm.setQueue(tracks("a", "b", "c"), startIndex = 0)
        val uidB = qm.entries.value[1].uid
        // Queue shifts after the uid was captured (the stale-index race).
        qm.moveItem(1, 2) // a, c, b
        qm.removeEntry(uidB)
        assertThat(qm.queue.value.map { it.id }).containsExactly("a", "c").inOrder()
    }

    @Test fun `removeEntry with duplicate ids removes exactly the targeted slot`() {
        qm.setQueue(tracks("a", "a", "b"), startIndex = 2)
        val uidSecondA = qm.entries.value[1].uid
        qm.removeEntry(uidSecondA)
        assertThat(qm.entries.value.map { it.track.id }).containsExactly("a", "b").inOrder()
        assertThat(qm.currentTrack.value?.id).isEqualTo("b")
    }

    @Test fun `removeEntry unknown uid is a no-op`() {
        qm.setQueue(tracks("a", "b"), startIndex = 0)
        qm.removeEntry(Long.MAX_VALUE)
        assertThat(qm.queue.value).hasSize(2)
    }

    @Test fun `moveEntry resolves by uid at commit time`() {
        qm.setQueue(tracks("a", "b", "c", "d"), startIndex = 0)
        val uidD = qm.entries.value[3].uid
        val uidB = qm.entries.value[1].uid
        // Queue shifts between gesture start and commit.
        qm.removeFromQueue(2) // a, b, d
        qm.moveEntry(uidD, uidB)
        assertThat(qm.queue.value.map { it.id }).containsExactly("a", "d", "b").inOrder()
    }

    @Test fun `moveEntry with missing uid is a no-op`() {
        qm.setQueue(tracks("a", "b"), startIndex = 0)
        val uidA = qm.entries.value[0].uid
        qm.moveEntry(uidA, Long.MAX_VALUE)
        assertThat(qm.queue.value.map { it.id }).containsExactly("a", "b").inOrder()
    }

    @Test fun `unshuffle with duplicate ids restores the exact playing slot`() {
        qm.setQueue(tracks("a", "b", "a", "c"), startIndex = 2) // second "a"
        val playingUid = qm.entries.value[2].uid
        qm.shuffle()
        qm.unshuffle()
        assertThat(qm.queue.value.map { it.id }).containsExactly("a", "b", "a", "c").inOrder()
        assertThat(qm.currentIndex.value).isEqualTo(2)
        assertThat(qm.entries.value[qm.currentIndex.value].uid).isEqualTo(playingUid)
    }

    // --- applyResolvedTracks ---

    @Test fun `applyResolvedTracks patches in place preserving order index and uids`() {
        qm.setQueue(tracks("a", "b", "c"), startIndex = 1)
        val uids = qm.entries.value.map { it.uid }
        qm.applyResolvedTracks(mapOf("c" to track("c").copy(streamUrl = "https://fresh/c")))
        assertThat(qm.queue.value.map { it.id }).containsExactly("a", "b", "c").inOrder()
        assertThat(qm.queue.value[2].streamUrl).isEqualTo("https://fresh/c")
        assertThat(qm.currentIndex.value).isEqualTo(1)
        assertThat(qm.entries.value.map { it.uid }).isEqualTo(uids)
    }

    @Test fun `applyResolvedTracks preserves queue edits made during resolution`() {
        qm.setQueue(tracks("a", "b"), startIndex = 0)
        qm.playNext(track("inserted"))
        qm.applyResolvedTracks(mapOf("b" to track("b").copy(streamUrl = "https://fresh/b")))
        assertThat(qm.queue.value.map { it.id }).containsExactly("a", "inserted", "b").inOrder()
        assertThat(qm.queue.value[2].streamUrl).isEqualTo("https://fresh/b")
    }

    @Test fun `applyResolvedTracks preserves shuffle snapshot`() {
        qm.setQueue(tracks("a", "b", "c"), startIndex = 0)
        qm.shuffle()
        qm.applyResolvedTracks(mapOf("b" to track("b").copy(streamUrl = "https://fresh/b")))
        qm.unshuffle()
        assertThat(qm.queue.value.map { it.id }).containsExactly("a", "b", "c").inOrder()
        // The snapshot itself was patched too.
        assertThat(qm.queue.value[1].streamUrl).isEqualTo("https://fresh/b")
    }

    @Test fun `applyResolvedTracks keeps live favorite state over the stale copy`() {
        qm.setQueue(tracks("a"), startIndex = 0)
        qm.applyFavoriteIds(setOf("a"))
        // The resolver worked from a pre-favorite copy of the track.
        qm.applyResolvedTracks(mapOf("a" to track("a").copy(streamUrl = "https://fresh/a")))
        assertThat(qm.queue.value[0].isFavorite).isTrue()
        assertThat(qm.queue.value[0].streamUrl).isEqualTo("https://fresh/a")
    }

    // --- resetToStart ---

    @Test fun `resetToStart moves index without touching queue or shuffle snapshot`() {
        qm.setQueue(tracks("a", "b", "c", "d", "e"), startIndex = 0)
        qm.shuffle()
        // Make sure the shuffled order actually differs from the original so
        // the unshuffle assertion below is meaningful.
        var guard = 0
        while (qm.queue.value.map { it.id } == listOf("a", "b", "c", "d", "e") && guard++ < 100) {
            qm.shuffle()
        }
        qm.skipToIndex(qm.queue.value.lastIndex)
        val shuffledOrder = qm.queue.value.map { it.id }

        val first = qm.resetToStart()

        assertThat(first?.id).isEqualTo(shuffledOrder.first())
        assertThat(qm.currentIndex.value).isEqualTo(0)
        assertThat(qm.queue.value.map { it.id }).isEqualTo(shuffledOrder)
        // L18 regression: the wraparound must not destroy the pre-shuffle
        // snapshot - shuffle-off after a full repeat-all pass still restores.
        qm.unshuffle()
        assertThat(qm.queue.value.map { it.id }).containsExactly("a", "b", "c", "d", "e").inOrder()
    }

    @Test fun `resetToStart on empty queue returns null`() {
        assertThat(qm.resetToStart()).isNull()
        assertThat(qm.currentIndex.value).isEqualTo(-1)
    }

    // --- remove-of-current callback ---

    @Test fun `removing current mid-queue notifies with removed track and successor`() {
        var removed: Track? = null
        var successor: Track? = null
        qm.onCurrentTrackRemoved = { r, n ->
            removed = r
            successor = n
        }
        qm.setQueue(tracks("a", "b", "c"), startIndex = 1)
        qm.removeFromQueue(1)
        assertThat(removed?.id).isEqualTo("b")
        assertThat(successor?.id).isEqualTo("c")
    }

    @Test fun `removing non-current does not notify`() {
        var notified = false
        qm.onCurrentTrackRemoved = { _, _ -> notified = true }
        qm.setQueue(tracks("a", "b", "c"), startIndex = 0)
        qm.removeFromQueue(2)
        assertThat(notified).isFalse()
    }

    @Test fun `removing last remaining current notifies with null successor`() {
        var removed: Track? = null
        var successor: Track? = track("sentinel")
        qm.onCurrentTrackRemoved = { r, n ->
            removed = r
            successor = n
        }
        qm.setQueue(tracks("a"), startIndex = 0)
        qm.removeFromQueue(0)
        assertThat(removed?.id).isEqualTo("a")
        assertThat(successor).isNull()
    }
}
