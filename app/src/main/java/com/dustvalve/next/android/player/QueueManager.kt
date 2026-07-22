package com.dustvalve.next.android.player

import com.dustvalve.next.android.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    /**
     * Permanent scope for derived StateFlows - never cancelled, since QueueManager is a singleton.
     *
     * The derived flows use [SharingStarted.Eagerly] so their `.value` always reflects
     * the latest [_state]. [PlaybackManager] reads `queue.value` / `currentIndex.value`
     * synchronously from ExoPlayer event callbacks; with [SharingStarted.WhileSubscribed],
     * a cold flow would expose a stale value when no UI is observing.
     */
    // Main is intentionally absent from AppDispatchers (see Dispatcher.kt):
    // tests substitute it globally via Dispatchers.setMain, so qualifying
    // it would only add ceremony.
    @Suppress("RawDispatchersUse")
    private val flowScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
                android.util.Log.e("QueueManager", "Unhandled coroutine error", throwable)
            },
    )

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

    val entries: StateFlow<List<QueueEntry>> = createEntriesFlow()

    val queue: StateFlow<List<Track>> = createQueueFlow()

    val currentIndex: StateFlow<Int> = createCurrentIndexFlow()

    val currentTrack: StateFlow<Track?> = createCurrentTrackFlow()

    private fun createEntriesFlow(): StateFlow<List<QueueEntry>> = _state.map { it.entries }
        .stateIn(flowScope, SharingStarted.Eagerly, emptyList())

    private fun createQueueFlow(): StateFlow<List<Track>> = _state.map { s -> s.entries.map { it.track } }
        .stateIn(flowScope, SharingStarted.Eagerly, emptyList())

    private fun createCurrentIndexFlow(): StateFlow<Int> = _state.map { it.currentIndex }
        .stateIn(flowScope, SharingStarted.Eagerly, -1)

    private fun createCurrentTrackFlow(): StateFlow<Track?> = _state.map { it.entries.getOrNull(it.currentIndex)?.track }
        .stateIn(flowScope, SharingStarted.Eagerly, null)

    private fun entryOf(track: Track) = QueueEntry(uid = nextUid.getAndIncrement(), track = track)

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        originalQueue = null
        val newIndex = if (tracks.isNotEmpty()) startIndex.coerceIn(0, tracks.lastIndex) else -1
        _state.value = QueueState(entries = tracks.map(::entryOf), currentIndex = newIndex)
    }

    /**
     * Patches every queue entry whose id is in [trackFavoriteIds] (or absent)
     * with the new isFavorite value, in-place. Preserves the originalQueue
     * shuffle snapshot - unlike [setQueue] - so it's safe to call from a
     * favorite-state observer without breaking shuffle restore.
     */
    fun applyFavoriteIds(trackFavoriteIds: Set<String>) {
        _state.update { s ->
            if (s.entries.isEmpty()) return@update s
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
        _state.update { s ->
            if (s.entries.isEmpty()) return@update s
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
        _state.update { s ->
            val newEntries = s.entries + entryOf(track)
            val newIndex = if (s.currentIndex == -1 && newEntries.isNotEmpty()) 0 else s.currentIndex
            QueueState(entries = newEntries, currentIndex = newIndex)
        }
    }

    fun playNext(track: Track) {
        originalQueue = null
        _state.update { s ->
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
        _state.update { s ->
            // Reset on each attempt: update{} may retry its lambda.
            removedCurrent = null
            successor = null
            if (index !in s.entries.indices) return@update s

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
        _state.update { s ->
            if (from !in s.entries.indices || to !in s.entries.indices) return@update s

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
        _state.update { s ->
            if (s.entries.isEmpty()) {
                result = null
                return@update s
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
        _state.update { s ->
            if (s.entries.isEmpty()) {
                result = null
                return@update s
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
        _state.update { s ->
            if (index !in s.entries.indices) {
                result = null
                return@update s
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
        _state.update { s ->
            if (s.entries.isEmpty()) {
                result = null
                return@update s
            }
            result = s.entries.first().track
            s.copy(currentIndex = 0)
        }
        return result
    }

    fun shuffle() {
        _state.update { s ->
            if (s.entries.size <= 1) return@update s

            val currentEntry = s.entries.getOrNull(s.currentIndex) ?: return@update s

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

        _state.update { s ->
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
        _state.value = QueueState()
        originalQueue = null
    }

    fun release() {
        // Intentionally preserves ALL queue state. PlaybackService calls this
        // from onDestroy, which also runs for the 5-minute idle-stop timer and
        // system service kills - clearing here silently erased the whole queue
        // and hid the mini player after any long pause. Explicit user intent to
        // drop the queue goes through [clear] (mini player swipe-down).
        // flowScope is also NOT cancelled - derived StateFlows must remain
        // alive so existing collectors (ViewModels, UI) continue receiving
        // updates.
    }

    fun reinitialize() {
        // No-op: flowScope is permanent, derived flows are stable references and
        // release() preserves the queue for exactly this restart path.
    }
}
