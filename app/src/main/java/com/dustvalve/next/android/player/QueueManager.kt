package com.dustvalve.next.android.player

import com.dustvalve.next.android.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One slot in the play queue. [uid] is unique per insertion and stable across
 * moves and in-place patches ([QueueManager.applyFavoriteIds] /
 * [QueueManager.applyResolvedTracks]), so the queue can legally contain the
 * same [Track.id] twice (playlist with a repeated track, addToQueue of the
 * playing track, ...) while the queue sheet still has a unique, stable
 * LazyColumn key per row.
 */
data class QueueEntry(val uid: Long, val track: Track)

private data class QueueState(val entries: List<QueueEntry> = emptyList(), val currentIndex: Int = -1)

@Singleton
class QueueManager @Inject constructor() {

    private val _state = MutableStateFlow(QueueState())

    /** Stores the original queue order before shuffle so it can be restored */
    private var originalQueue: List<QueueEntry>? = null

    /** Monotonic source for [QueueEntry.uid]; atomic so an interleaved [MutableStateFlow.update] retry can't mint duplicates. */
    private val nextUid = AtomicLong(1L)

    /**
     * Invoked after [removeFromQueue] removed the entry at the current index,
     * with the removed track and the new current track (null when the queue
     * became empty). [PlaybackManager] installs this to advance the actual
     * player off the removed track instead of silently keeping its audio
     * while the flows point at the successor.
     */
    var onCurrentTrackRemoved: ((removed: Track, newCurrent: Track?) -> Unit)? = null

    // Derived views of [_state], republished SYNCHRONOUSLY by [publish] inside
    // every mutator. They used to be stateIn(flowScope, Eagerly) projections,
    // but a shared flow only forwards a new value once its collector coroutine
    // is resumed by the dispatcher - so any caller that mutated the queue and
    // read `.value` back inside the same main-thread block saw the PREVIOUS
    // value. PlaybackManager does exactly that (skip-unplayable give-up branch
    // reads currentIndex.value right after next()), so the projections must be
    // plain MutableStateFlows written in the same call.
    private val _entries = MutableStateFlow<List<QueueEntry>>(emptyList())
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    private val _currentIndex = MutableStateFlow(-1)
    private val _currentTrack = MutableStateFlow<Track?>(null)

    val entries: StateFlow<List<QueueEntry>> = _entries.asStateFlow()

    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    /** Mirrors [state] into the derived flows; call after every [_state] write. */
    private fun publish(state: QueueState) {
        _entries.value = state.entries
        _queue.value = state.entries.map { it.track }
        _currentIndex.value = state.currentIndex
        _currentTrack.value = state.entries.getOrNull(state.currentIndex)?.track
    }

    /** [MutableStateFlow.update] plus the synchronous [publish] of the result. */
    private fun updateState(block: (QueueState) -> QueueState) {
        publish(_state.updateAndGet(block))
    }

    /** [MutableStateFlow.value] assignment plus the synchronous [publish]. */
    private fun setState(state: QueueState) {
        _state.value = state
        publish(state)
    }

    private fun entryOf(track: Track) = QueueEntry(uid = nextUid.getAndIncrement(), track = track)

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        originalQueue = null
        val newIndex = if (tracks.isNotEmpty()) startIndex.coerceIn(0, tracks.lastIndex) else -1
        setState(QueueState(entries = tracks.map(::entryOf), currentIndex = newIndex))
    }

    /**
     * Patches every queue entry whose id is in [trackFavoriteIds] (or absent)
     * with the new isFavorite value, in-place. Preserves the originalQueue
     * shuffle snapshot - unlike [setQueue] - so it's safe to call from a
     * favorite-state observer without breaking shuffle restore.
     */
    fun applyFavoriteIds(trackFavoriteIds: Set<String>) {
        updateState { s ->
            if (s.entries.isEmpty()) return@updateState s
            var changed = false
            val patched = s.entries.map { e ->
                val newFav = e.track.id in trackFavoriteIds
                if (e.track.isFavorite == newFav) {
                    e
                } else {
                    changed = true
                    e.copy(track = e.track.copy(isFavorite = newFav))
                }
            }
            if (!changed) s else s.copy(entries = patched)
        }
        // Keep originalQueue (shuffle snapshot) in sync so a later "unshuffle"
        // restore reflects the new favorite state.
        originalQueue?.let { snap ->
            originalQueue = snap.map { e ->
                val newFav = e.track.id in trackFavoriteIds
                if (e.track.isFavorite == newFav) e else e.copy(track = e.track.copy(isFavorite = newFav))
            }
        }
    }

    /**
     * Patches queue entries in-place with freshly resolved tracks, keyed by
     * [Track.id]. Modeled on [applyFavoriteIds]: preserves order, currentIndex,
     * entry uids and the originalQueue shuffle snapshot - unlike [setQueue] -
     * so the background resolution loop can patch each track as it resolves
     * without reverting queue edits (playNext/add/remove/reorder/shuffle) made
     * while resolution was in flight. The live entry's isFavorite is kept, so
     * a favorite toggled mid-resolution isn't clobbered by the stale copy the
     * resolver worked from.
     */
    fun applyResolvedTracks(byId: Map<String, Track>) {
        if (byId.isEmpty()) return
        fun patch(e: QueueEntry): QueueEntry {
            val resolved = byId[e.track.id] ?: return e
            val merged = resolved.copy(isFavorite = e.track.isFavorite)
            return if (merged == e.track) e else e.copy(track = merged)
        }
        updateState { s ->
            if (s.entries.isEmpty()) return@updateState s
            var changed = false
            val patched = s.entries.map { e ->
                val p = patch(e)
                if (p !== e) changed = true
                p
            }
            if (!changed) s else s.copy(entries = patched)
        }
        originalQueue = originalQueue?.map(::patch)
    }

    fun addToQueue(track: Track) {
        originalQueue = null
        updateState { s ->
            val newEntries = s.entries + entryOf(track)
            val newIndex = if (s.currentIndex == -1 && newEntries.isNotEmpty()) 0 else s.currentIndex
            QueueState(entries = newEntries, currentIndex = newIndex)
        }
    }

    fun playNext(track: Track) {
        originalQueue = null
        updateState { s ->
            if (s.entries.isEmpty() || s.currentIndex < 0) {
                QueueState(entries = listOf(entryOf(track)), currentIndex = 0)
            } else {
                val insertIndex = s.currentIndex + 1
                val newEntries = s.entries.toMutableList().apply { add(insertIndex, entryOf(track)) }
                QueueState(entries = newEntries, currentIndex = s.currentIndex)
            }
        }
    }

    fun removeFromQueue(index: Int) {
        var removedCurrent: Track? = null
        var successor: Track? = null
        updateState { s ->
            // Reset on each attempt: update{} may retry its lambda.
            removedCurrent = null
            successor = null
            if (index !in s.entries.indices) return@updateState s

            // Clear the pre-shuffle snapshot: once the queue diverges from it,
            // restoring that order would discard the user's edits.
            originalQueue = null

            val ci = s.currentIndex
            val removed = s.entries[index]
            val newEntries = s.entries.toMutableList().apply { removeAt(index) }

            val newIndex = when {
                newEntries.isEmpty() -> -1
                index < ci -> ci - 1
                index == ci && ci >= newEntries.size -> newEntries.lastIndex
                else -> ci
            }

            if (index == ci) {
                removedCurrent = removed.track
                successor = newEntries.getOrNull(newIndex)?.track
            }

            QueueState(entries = newEntries, currentIndex = newIndex)
        }
        // The player may still be playing the removed track - let PlaybackManager
        // reconcile the audible state with the repointed queue.
        removedCurrent?.let { onCurrentTrackRemoved?.invoke(it, successor) }
    }

    /**
     * Removes the entry with [uid], resolving its index against the LIVE queue
     * at call time. The queue sheet commits swipe-removes through this instead
     * of a positional index captured at composition, which could be stale by
     * the time the gesture settles (and is ambiguous with duplicate track ids).
     */
    fun removeEntry(uid: Long) {
        val index = _state.value.entries.indexOfFirst { it.uid == uid }
        if (index >= 0) removeFromQueue(index)
    }

    fun moveItem(from: Int, to: Int) {
        updateState { s ->
            if (from !in s.entries.indices || to !in s.entries.indices) return@updateState s

            // Same rationale as removeFromQueue: the shuffled order has been edited,
            // so the pre-shuffle snapshot is no longer the right thing to restore.
            originalQueue = null

            val ci = s.currentIndex
            val newEntries = s.entries.toMutableList()
            val item = newEntries.removeAt(from)
            newEntries.add(to, item)

            val newIndex = when (ci) {
                from -> to

                in (minOf(from, to)..maxOf(from, to)) -> {
                    if (from < to) ci - 1 else ci + 1
                }

                else -> ci
            }

            QueueState(entries = newEntries, currentIndex = newIndex)
        }
    }

    /**
     * Moves the entry with [fromUid] to the position currently occupied by
     * [toUid], resolving both indices against the LIVE queue at call time.
     * Same stale-positional-index rationale as [removeEntry].
     */
    fun moveEntry(fromUid: Long, toUid: Long) {
        val s = _state.value
        val from = s.entries.indexOfFirst { it.uid == fromUid }
        val to = s.entries.indexOfFirst { it.uid == toUid }
        if (from >= 0 && to >= 0 && from != to) moveItem(from, to)
    }

    fun next(): Track? {
        var result: Track? = null
        updateState { s ->
            if (s.entries.isEmpty()) {
                result = null
                return@updateState s
            }
            val nextIndex = s.currentIndex + 1
            if (nextIndex in s.entries.indices) {
                result = s.entries[nextIndex].track
                s.copy(currentIndex = nextIndex)
            } else {
                result = null
                s
            }
        }
        return result
    }

    fun previous(): Track? {
        var result: Track? = null
        updateState { s ->
            if (s.entries.isEmpty()) {
                result = null
                return@updateState s
            }
            val prevIndex = s.currentIndex - 1
            if (prevIndex in s.entries.indices) {
                result = s.entries[prevIndex].track
                s.copy(currentIndex = prevIndex)
            } else {
                result = null
                s
            }
        }
        return result
    }

    fun hasNext(): Boolean {
        val s = _state.value
        return s.currentIndex + 1 in s.entries.indices
    }

    fun hasPrevious(): Boolean {
        val s = _state.value
        return s.currentIndex - 1 in s.entries.indices
    }

    fun skipToIndex(index: Int): Track? {
        var result: Track? = null
        updateState { s ->
            if (index !in s.entries.indices) {
                result = null
                return@updateState s
            }
            result = s.entries[index].track
            s.copy(currentIndex = index)
        }
        return result
    }

    /**
     * Moves currentIndex back to the start WITHOUT replacing the queue.
     * Unlike [setQueue] this preserves the originalQueue shuffle snapshot, so
     * repeat-all wraparound doesn't silently break a later shuffle-off restore.
     * Returns the new current track, or null when the queue is empty.
     */
    fun resetToStart(): Track? {
        var result: Track? = null
        updateState { s ->
            if (s.entries.isEmpty()) {
                result = null
                return@updateState s
            }
            result = s.entries.first().track
            s.copy(currentIndex = 0)
        }
        return result
    }

    fun shuffle() {
        updateState { s ->
            if (s.entries.size <= 1) return@updateState s

            val currentEntry = s.entries.getOrNull(s.currentIndex) ?: return@updateState s

            // Save original order for unshuffle
            if (originalQueue == null) {
                originalQueue = s.entries
            }

            val newEntries = s.entries.toMutableList()
            newEntries.removeAt(s.currentIndex)
            newEntries.shuffle()
            newEntries.add(0, currentEntry)

            QueueState(entries = newEntries, currentIndex = 0)
        }
    }

    fun unshuffle() {
        val saved = originalQueue ?: return
        originalQueue = null

        updateState { s ->
            val currentEntry = s.entries.getOrNull(s.currentIndex)

            // Match by uid, not track id: with duplicate ids in the queue only
            // the uid identifies the exact playing slot.
            val restoredIndex = if (currentEntry != null) {
                saved.indexOfFirst { it.uid == currentEntry.uid }.takeIf { it >= 0 } ?: 0
            } else {
                0
            }

            QueueState(
                entries = saved,
                currentIndex = if (saved.isNotEmpty()) restoredIndex else -1,
            )
        }
    }

    fun clear() {
        setState(QueueState())
        originalQueue = null
    }

    fun release() {
        // Intentionally preserves ALL queue state. PlaybackService calls this
        // from onDestroy, which also runs for the 5-minute idle-stop timer and
        // system service kills - clearing here silently erased the whole queue
        // and hid the mini player after any long pause. Explicit user intent to
        // drop the queue goes through [clear] (mini player swipe-down).
        // The derived StateFlows are plain MutableStateFlows with no backing
        // coroutine, so there is nothing to cancel and existing collectors
        // (ViewModels, UI) keep receiving updates across a service restart.
    }

    fun reinitialize() {
        // No-op: the derived flows are stable references with no scope to
        // restart, and release() preserves the queue for exactly this path.
    }
}
