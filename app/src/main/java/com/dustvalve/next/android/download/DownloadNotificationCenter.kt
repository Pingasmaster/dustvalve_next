package com.dustvalve.next.android.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dustvalve.next.android.MainActivity
import com.dustvalve.next.android.R
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the single user-visible "downloads in progress" notification.
 *
 * **Legacy branch:** master targets Android 16.1+ and uses Live Updates
 * unconditionally. This branch supports Android 8.0 through Android 16,
 * so the notification is rendered two ways:
 *  - On API 36+ (Android 16+) we try the Live Update chip
 *    (`Notification.ProgressStyle` + `setRequestPromotedOngoing(true)` +
 *    `setShortCriticalText(...)`). Devices on stock Android 16 without
 *    QPR1 will silently fall back to the regular progress notification.
 *  - On API < 36 we render a normal ongoing progress notification via
 *    `NotificationCompat.Builder.setProgress(...)`.
 *
 * The center supports nested batches (e.g. an artist download internally
 * wraps album downloads); the outer-most batch wins so the chip stays on
 * the artist label rather than flickering between albums. Individual
 * `downloadTrack` calls outside any batch show a single-track notification.
 */
@OptIn(FlowPreview::class)
@Singleton
class DownloadNotificationCenter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
) {
    enum class BatchKind { ALBUM, ARTIST, PLAYLIST }

    internal data class Batch(val id: Long, val label: String, val totalTracks: Int, val kind: BatchKind)

    internal data class TrackProgress(val trackId: String, val title: String, val bytesWritten: Long, val expectedTotal: Long?)

    internal data class State(
        val batchStack: List<Batch> = emptyList(),
        val completedInBatch: Int = 0,
        val activeTracks: Map<String, TrackProgress> = emptyMap(),
    )

    private val mutex = Mutex()
    private val state = MutableStateFlow(State())

    /** Test-only window into the internal state. */
    @androidx.annotation.VisibleForTesting
    internal val currentState: State get() = state.value

    /** Test-only: cancel the background collect so JVM tests don't leak coroutines. */
    @androidx.annotation.VisibleForTesting
    internal fun shutdownForTest() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val batchSeq = AtomicLong(0L)
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        scope.launch {
            // Debounce so a flurry of byte-write callbacks coalesces into a
            // single rebuild — NotificationManager bins ~10 posts/sec/app
            // and we don't want to compete with our own updates.
            combine(state, settingsDataStore.downloadNotificationsEnabled) { s, enabled -> s to enabled }
                .debounce(NOTIFICATION_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { (snapshot, enabled) -> refresh(snapshot, enabled) }
        }
    }

    /**
     * Wraps a logical batch (album/artist/playlist). While `block` runs,
     * the notification shows aggregated progress against `totalTracks`.
     */
    suspend fun <T> withBatch(label: String, totalTracks: Int, kind: BatchKind, block: suspend () -> T): T {
        val id = batchSeq.incrementAndGet()
        mutex.withLock {
            state.update { s ->
                val isFirstBatch = s.batchStack.isEmpty()
                s.copy(
                    batchStack = s.batchStack + Batch(id, label, totalTracks, kind),
                    completedInBatch = if (isFirstBatch) 0 else s.completedInBatch,
                )
            }
        }
        try {
            return block()
        } finally {
            mutex.withLock {
                state.update { s ->
                    val newStack = s.batchStack.filterNot { it.id == id }
                    s.copy(
                        batchStack = newStack,
                        completedInBatch = if (newStack.isEmpty()) 0 else s.completedInBatch,
                    )
                }
            }
        }
    }

    fun trackStarted(trackId: String, title: String) {
        state.update { s ->
            s.copy(
                activeTracks = s.activeTracks + (trackId to TrackProgress(trackId, title, 0L, null)),
            )
        }
    }

    fun trackProgress(trackId: String, bytesWritten: Long, expectedTotal: Long?) {
        state.update { s ->
            val existing = s.activeTracks[trackId] ?: return@update s
            val updated = existing.copy(bytesWritten = bytesWritten, expectedTotal = expectedTotal)
            s.copy(activeTracks = s.activeTracks + (trackId to updated))
        }
    }

    fun trackFinished(trackId: String, @Suppress("UNUSED_PARAMETER") success: Boolean) {
        state.update { s ->
            val newActive = s.activeTracks - trackId
            val inBatch = s.batchStack.isNotEmpty()
            s.copy(
                activeTracks = newActive,
                completedInBatch = if (inBatch) s.completedInBatch + 1 else s.completedInBatch,
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun refresh(snapshot: State, enabled: Boolean) {
        try {
            if (!enabled || !hasPostPermission()) {
                notificationManager.cancel(NOTIFICATION_ID)
                return
            }
            if (snapshot.activeTracks.isEmpty() && snapshot.batchStack.isEmpty()) {
                notificationManager.cancel(NOTIFICATION_ID)
                return
            }
            val notification = buildNotification(snapshot) ?: run {
                notificationManager.cancel(NOTIFICATION_ID)
                return
            }
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between check and post.
        } catch (_: NullPointerException) {
            // Robolectric tear-down race: Context's ActivityThread can be
            // null when the singleton's debounced collect fires after the
            // test scope has exited. Harmless in prod (Application never
            // dies mid-process); silently drop the update.
        }
    }

    private fun hasPostPermission(): Boolean {
        // POST_NOTIFICATIONS only became a runtime permission on API 33
        // (Android 13). On older devices notifications are auto-granted; if
        // we ran the permission check we'd see DENIED and silently suppress.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildNotification(snapshot: State): Notification? {
        val outer = snapshot.batchStack.firstOrNull()
        val currentTrack = snapshot.activeTracks.values.firstOrNull()

        val title: String
        val text: String
        val progressMax: Int
        val progressCurrent: Int
        val chipText: String

        if (outer != null) {
            title = when (outer.kind) {
                BatchKind.ALBUM -> context.getString(R.string.notification_downloading_batch_album, outer.label)
                BatchKind.ARTIST -> context.getString(R.string.notification_downloading_batch_artist, outer.label)
                BatchKind.PLAYLIST -> context.getString(R.string.notification_downloading_batch_playlist, outer.label)
            }
            val currentIndex = (snapshot.completedInBatch + 1).coerceAtMost(outer.totalTracks)
            text = if (currentTrack != null) {
                context.getString(
                    R.string.notification_downloading_batch_progress,
                    currentIndex,
                    outer.totalTracks,
                    currentTrack.title,
                )
            } else {
                context.getString(
                    R.string.notification_downloading_batch_progress_idle,
                    currentIndex,
                    outer.totalTracks,
                )
            }
            progressMax = (outer.totalTracks * UNIT_PER_TRACK).coerceAtLeast(UNIT_PER_TRACK)
            val currentTrackFrac = currentTrack?.let { tp ->
                val total = tp.expectedTotal
                if (total != null && total > 0L) {
                    ((tp.bytesWritten * UNIT_PER_TRACK) / total).toInt().coerceIn(0, UNIT_PER_TRACK)
                } else {
                    0
                }
            } ?: 0
            progressCurrent =
                (snapshot.completedInBatch * UNIT_PER_TRACK + currentTrackFrac).coerceAtMost(progressMax)
            chipText = "$currentIndex/${outer.totalTracks}"
        } else {
            if (currentTrack == null) return null
            title = context.getString(R.string.notification_downloading_single, currentTrack.title)
            text = ""
            val total = currentTrack.expectedTotal
            if (total != null && total > 0L) {
                progressMax = UNIT_PER_TRACK
                progressCurrent =
                    ((currentTrack.bytesWritten * UNIT_PER_TRACK) / total).toInt().coerceIn(0, UNIT_PER_TRACK)
                chipText = "${(progressCurrent / (UNIT_PER_TRACK / 100))}%"
            } else {
                progressMax = UNIT_PER_TRACK
                progressCurrent = 0
                chipText = "…"
            }
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        if (Build.VERSION.SDK_INT >= ANDROID_16_API) {
            try {
                return buildLiveUpdate(title, text, progressMax, progressCurrent, chipText, contentIntent)
            } catch (_: NoSuchMethodError) {
                // Android 16 base without QPR1: Live Update APIs are unresolved.
                // Fall through to the compat path below.
            } catch (_: LinkageError) {
                // Belt-and-suspenders for class-loading edge cases on OEM ROMs.
            }
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(text.ifEmpty { null })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(progressMax, progressCurrent, false)
            .setContentIntent(contentIntent)
            .build()
    }

    // Live Update path — gated by the SDK_INT check above. Surface area lives
    // in API 36.1 (QPR1) on top of API 36; on plain Android 16 pre-QPR1 the
    // call throws NoSuchMethodError, which we catch and downgrade to the
    // NotificationCompat path inside refresh().
    @android.annotation.SuppressLint("NewApi")
    private fun buildLiveUpdate(
        title: String,
        text: String,
        progressMax: Int,
        progressCurrent: Int,
        chipText: String,
        contentIntent: PendingIntent,
    ): Notification {
        val style = Notification.ProgressStyle()
            .setProgress(progressCurrent)
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(progressMax)))
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(style)
            .setShortCriticalText(chipText)
            .setRequestPromotedOngoing(true)
            .setContentIntent(contentIntent)
        if (text.isNotEmpty()) builder.setContentText(text)
        return builder.build()
    }

    fun ensureChannel() {
        // NotificationChannel only exists on API 26+; legacy supports
        // Android 8 (API 26) and up, so the class is always available, but
        // older OEM ROMs sometimes throw — wrap defensively.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_downloads),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_downloads_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 4242

        // ProgressStyle is determinate; 1000 units per track lets us show
        // sub-track byte-level progress within an album/playlist bar.
        private const val UNIT_PER_TRACK = 1000

        private const val NOTIFICATION_DEBOUNCE_MS = 250L

        // Android 16 = API 36; Live Updates promotion APIs only exist at/above
        // (formally API 36.1 / Android 16 QPR1 — we try/catch the QPR delta).
        private const val ANDROID_16_API = 36
    }
}
