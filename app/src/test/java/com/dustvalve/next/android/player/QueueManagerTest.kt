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

    @Test fun `release clears queue but derived flows stay alive`() {
        qm.setQueue(tracks("a"), startIndex = 0)
        qm.release()
        assertThat(qm.queue.value).isEmpty()
        // Reinitialize just doesn't throw and we can setQueue again
        qm.reinitialize()
        qm.setQueue(tracks("b"), startIndex = 0)
        assertThat(qm.currentTrack.value?.id).isEqualTo("b")
    }
}
