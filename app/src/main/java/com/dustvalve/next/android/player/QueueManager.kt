package com.dustvalve.next.android.player

import com.dustvalve.next.android.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import javax.inject.Inject
import javax.inject.Singleton

private data class QueueState(
    val tracks: List<Track> = emptyList(),
    val currentIndex: Int = -1,
)

@Singleton
class QueueManager @Inject constructor() {

    /** Permanent scope for derived StateFlows — never cancelled, since QueueManager is a singleton. */
    private val flowScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
                android.util.Log.e("QueueManager", "Unhandled coroutine error", throwable)
            }
    )

    private val _state = MutableStateFlow(QueueState())

    /** Stores the original queue order before shuffle so it can be restored */
    private var originalQueue: List<Track>? = null

    val queue: StateFlow<List<Track>> = createQueueFlow()

    val currentIndex: StateFlow<Int> = createCurrentIndexFlow()

    val currentTrack: StateFlow<Track?> = createCurrentTrackFlow()

    private fun createQueueFlow(): StateFlow<List<Track>> {
        return _state.map { it.tracks }
            .stateIn(flowScope, SharingStarted.Eagerly, emptyList())
    }

    private fun createCurrentIndexFlow(): StateFlow<Int> {
        return _state.map { it.currentIndex }
            .stateIn(flowScope, SharingStarted.Eagerly, -1)
    }

    private fun createCurrentTrackFlow(): StateFlow<Track?> {
        return _state.map { it.tracks.getOrNull(it.currentIndex) }
            .stateIn(flowScope, SharingStarted.Eagerly, null)
    }

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        val newIndex = if (tracks.isNotEmpty()) startIndex.coerceIn(0, tracks.lastIndex) else -1
        _state.value = QueueState(tracks = tracks, currentIndex = newIndex)
    }

    fun addToQueue(track: Track) {
        _state.update { s ->
            val newTracks = s.tracks + track
            val newIndex = if (s.currentIndex == -1 && newTracks.isNotEmpty()) 0 else s.currentIndex
            QueueState(tracks = newTracks, currentIndex = newIndex)
        }
    }

    fun removeFromQueue(index: Int) {
        _state.update { s ->
            if (index !in s.tracks.indices) return@update s

            val ci = s.currentIndex
            val newTracks = s.tracks.toMutableList().apply { removeAt(index) }

            val newIndex = when {
                newTracks.isEmpty() -> -1
                index < ci -> ci - 1
                index == ci && ci >= newTracks.size -> newTracks.lastIndex
                else -> ci
            }

            QueueState(tracks = newTracks, currentIndex = newIndex)
        }
    }

    fun moveItem(from: Int, to: Int) {
        _state.update { s ->
            if (from !in s.tracks.indices || to !in s.tracks.indices) return@update s

            val ci = s.currentIndex
            val newTracks = s.tracks.toMutableList()
            val item = newTracks.removeAt(from)
            newTracks.add(to, item)

            val newIndex = when (ci) {
                from -> to
                in (minOf(from, to)..maxOf(from, to)) -> {
                    if (from < to) ci - 1 else ci + 1
                }
                else -> ci
            }

            QueueState(tracks = newTracks, currentIndex = newIndex)
        }
    }

    fun next(): Track? {
        var result: Track? = null
        _state.update { s ->
            if (s.tracks.isEmpty()) {
                result = null
                return@update s
            }
            val nextIndex = s.currentIndex + 1
            if (nextIndex in s.tracks.indices) {
                result = s.tracks[nextIndex]
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
            if (s.tracks.isEmpty()) {
                result = null
                return@update s
            }
            val prevIndex = s.currentIndex - 1
            if (prevIndex in s.tracks.indices) {
                result = s.tracks[prevIndex]
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
        return s.currentIndex + 1 in s.tracks.indices
    }

    fun hasPrevious(): Boolean {
        val s = _state.value
        return s.currentIndex - 1 in s.tracks.indices
    }

    fun skipToIndex(index: Int): Track? {
        var result: Track? = null
        _state.update { s ->
            if (index !in s.tracks.indices) {
                result = null
                return@update s
            }
            result = s.tracks[index]
            s.copy(currentIndex = index)
        }
        return result
    }

    fun shuffle() {
        _state.update { s ->
            if (s.tracks.size <= 1) return@update s

            val currentTrackItem = s.tracks.getOrNull(s.currentIndex) ?: return@update s

            // Save original order for unshuffle
            if (originalQueue == null) {
                originalQueue = s.tracks
            }

            val newTracks = s.tracks.toMutableList()
            newTracks.removeAt(s.currentIndex)
            newTracks.shuffle()
            newTracks.add(0, currentTrackItem)

            QueueState(tracks = newTracks, currentIndex = 0)
        }
    }

    fun unshuffle() {
        val saved = originalQueue ?: return
        originalQueue = null

        _state.update { s ->
            val currentTrackItem = s.tracks.getOrNull(s.currentIndex)

            val restoredIndex = if (currentTrackItem != null) {
                saved.indexOfFirst { it.id == currentTrackItem.id }.takeIf { it >= 0 } ?: 0
            } else {
                0
            }

            QueueState(
                tracks = saved,
                currentIndex = if (saved.isNotEmpty()) restoredIndex else -1,
            )
        }
    }

    fun clear() {
        _state.value = QueueState()
        originalQueue = null
    }

    fun release() {
        // flowScope is intentionally NOT cancelled — derived StateFlows must remain
        // alive so existing collectors (ViewModels, UI) continue receiving updates.
        // Only the queue state is cleared.
        clear()
    }

    fun reinitialize() {
        // No-op: flowScope is permanent and derived flows are stable references.
        // State was cleared in release(); new tracks will be set via setQueue/addToQueue.
    }
}
