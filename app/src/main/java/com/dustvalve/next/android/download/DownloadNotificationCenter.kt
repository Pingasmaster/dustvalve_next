package com.dustvalve.next.android.download

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.dustvalve.next.android.BuildConfig
import com.dustvalve.next.android.MainActivity
import com.dustvalve.next.android.R
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.di.qualifiers.ApplicationScope
import com.dustvalve.next.android.domain.repository.DownloadProgressReporter
import com.dustvalve.next.android.domain.repository.DownloadProgressReporter.BatchKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
 * Two rendering pipelines, selected once via [liveUpdateCapable]:
 * - Android 16 QPR1+ (API 36.1): the status-bar **Live Update** chip via
 *   `Notification.ProgressStyle` + `setRequestPromotedOngoing(true)` +
 *   `setShortCriticalText(...)`.
 * - Everything below (this branch's minSdk is 26): a classic
 *   NotificationCompat determinate-progress card. This path MUST exist -
 *   ProgressStyle is an API 36 class and the promoted-ongoing setters are
 *   API 36.1, and calling them below that crashed every download in
 *   v0.5.0-v0.5.2 (NoClassDefFoundError inside DownloadService's
 *   startForeground).
 *
 * The center supports nested batches (e.g. an artist download internally
 * wraps album downloads); the outer-most batch wins so the chip stays on
 * the artist label rather than flickering between albums. Individual
 * `downloadTrack` calls outside any batch show a single-track notification.
 */
// TooManyFunctions: the count comes from carrying BOTH notification
// pipelines (Live Update platform builders + the pre-API-36 compat
// builders); splitting them into separate classes would spread one
// notification's state over two files for a style metric.
@Suppress("TooManyFunctions")
@OptIn(FlowPreview::class)
@Singleton
class DownloadNotificationCenter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    @param:ApplicationScope private val scope: CoroutineScope,
) : DownloadProgressReporter {

    internal data class Batch(val id: Long, val label: String, val totalTracks: Int, val kind: BatchKind)

    internal data class TrackProgress(val trackId: String, val title: String, val bytesWritten: Long, val expectedTotal: Long?)

    internal data class State(
        val batchStack: List<Batch> = emptyList(),
        val completedInBatch: Int = 0,
        val activeTracks: Map<String, TrackProgress> = emptyMap(),
        val paused: Boolean = false,
    )

    private val mutex = Mutex()
    private val state = MutableStateFlow(State())

    /** Platform manager for the API 36.1 promotion-diagnostics calls. */
    private val platformNotificationManager =
        context.getSystemService(NotificationManager::class.java)

    /**
     * True while [DownloadService] holds [NOTIFICATION_ID] as its foreground
     * notification. While owned, this center must NOT `cancel()` the id on an
     * empty/disabled state - the service tears it down via `stopForeground`.
     */
    @Volatile
    private var foregroundOwned = false

    fun setForegroundOwned(owned: Boolean) {
        foregroundOwned = owned
    }

    /** Reflects [DownloadController]'s global pause state into the notification. */
    fun setPaused(paused: Boolean) {
        state.update { it.copy(paused = paused) }
    }

    /**
     * Re-posts the current snapshot as a regular (non-foreground)
     * notification. Used by [DownloadService.onTimeout]: `stopForeground`
     * removes the shared notification, and when the state was already paused
     * beforehand no StateFlow change fires to bring it back - this does.
     */
    fun repostAfterForegroundTimeout() {
        scope.launch {
            refresh(state.value, settingsDataStore.downloadNotificationsEnabled.first())
        }
    }

    /** Test-only window into the internal state. */
    @androidx.annotation.VisibleForTesting
    internal val currentState: State get() = state.value

    /** Test-only: cancel the background collect so JVM tests don't leak coroutines. */
    @androidx.annotation.VisibleForTesting
    internal fun shutdownForTest() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
    private val batchSeq = AtomicLong(0L)
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        scope.launch {
            // Debounce so a flurry of byte-write callbacks coalesces into a
            // single rebuild - NotificationManager bins ~10 posts/sec/app
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
    override suspend fun <T> withBatch(label: String, totalTracks: Int, kind: BatchKind, block: suspend () -> T): T {
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

    override fun trackStarted(trackId: String, title: String) {
        state.update { s ->
            s.copy(
                activeTracks = s.activeTracks + (trackId to TrackProgress(trackId, title, 0L, null)),
            )
        }
    }

    override fun trackProgress(trackId: String, bytesWritten: Long, expectedTotal: Long?) {
        state.update { s ->
            val existing = s.activeTracks[trackId] ?: return@update s
            val updated = existing.copy(bytesWritten = bytesWritten, expectedTotal = expectedTotal)
            s.copy(activeTracks = s.activeTracks + (trackId to updated))
        }
    }

    override fun trackFinished(trackId: String, @Suppress("UNUSED_PARAMETER") success: Boolean) {
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
            val hasWork = snapshot.activeTracks.isNotEmpty() ||
                snapshot.batchStack.isNotEmpty() ||
                snapshot.paused
            if (!enabled || !hasPostPermission() || !hasWork) {
                // When the service owns the foreground notification it drives
                // teardown via stopForeground - cancelling here would fight it
                // (and an FGS notification can't be cancelled while foregrounded).
                if (!foregroundOwned) notificationManager.cancel(NOTIFICATION_ID)
                return
            }
            val notification = buildNotification(snapshot) ?: run {
                if (!foregroundOwned) notificationManager.cancel(NOTIFICATION_ID)
                return
            }
            notificationManager.notify(NOTIFICATION_ID, notification)
            logPromotionDiagnostics(notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between check and post.
        } catch (_: NullPointerException) {
            // Robolectric tear-down race: Context's ActivityThread can be
            // null when the singleton's debounced collect fires after the
            // test scope has exited. Harmless in prod (Application never
            // dies mid-process); silently drop the update.
        }
    }

    // POST_NOTIFICATIONS only exists from API 33; below that, respect the
    // system-level per-app notifications toggle. (The previous legacy gate
    // returned false below Android 16, which suppressed all download-progress
    // notifications on the very devices this branch serves.)
    private fun hasPostPermission(): Boolean = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        notificationManager.areNotificationsEnabled()
    }

    /**
     * True when the Android 16 QPR1+ Live Update pipeline
     * (Notification.ProgressStyle + setShortCriticalText +
     * setRequestPromotedOngoing) is safe to call. ProgressStyle is an API 36
     * class; the two promoted-ongoing calls are API 36.1 (BAKLAVA_1). Below
     * that - i.e. on virtually every device this legacy branch serves - the
     * NotificationCompat builders are used instead.
     *
     * v0.5.0-v0.5.2 REGRESSION: this gate did not exist. The foreground
     * notification handed to DownloadService ALWAYS went through
     * ProgressStyle, so starting any download instantly crashed the app with
     * NoClassDefFoundError on Android 8-15 (NoSuchMethodError on Android
     * 16.0). The SDK_INT_FULL read is wrapped defensively: if the field is
     * missing at runtime the answer is "not capable", never a crash.
     */
    private val liveUpdateCapable: Boolean by lazy { computeLiveUpdateCapable() }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun computeLiveUpdateCapable(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.BAKLAVA) return false
        return try {
            android.os.Build.VERSION.SDK_INT_FULL >= android.os.Build.VERSION_CODES_FULL.BAKLAVA_1
        } catch (e: Throwable) {
            false
        }
    }

    /** Everything both notification pipelines need to render progress. */
    private data class ProgressContent(
        val title: String,
        val text: String,
        val progressMax: Int,
        val progressCurrent: Int,
        val chipText: String,
    )

    private fun buildNotification(snapshot: State): Notification? {
        if (snapshot.paused) return buildPausedNotification()
        val content = progressContent(snapshot) ?: return null
        return if (liveUpdateCapable) {
            buildLiveUpdateNotification(content)
        } else {
            buildCompatNotification(content)
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun progressContent(snapshot: State): ProgressContent? {
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

                BatchKind.PLAYLIST -> context.getString(
                    R.string.notification_downloading_batch_playlist,
                    outer.label.ifBlank { context.getString(R.string.playlist_default_title) },
                )
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
                chipText = "..."
            }
        }

        return ProgressContent(title, text, progressMax, progressCurrent, chipText)
    }

    /** Android 16 QPR1+ Live Update chip. Only reachable when [liveUpdateCapable]. */
    @android.annotation.SuppressLint("NewApi")
    private fun buildLiveUpdateNotification(content: ProgressContent): Notification {
        val style = Notification.ProgressStyle()
            .setProgress(content.progressCurrent)
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(content.progressMax)))
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(content.title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(style)
            .setShortCriticalText(content.chipText)
            .setRequestPromotedOngoing(true)
            .setContentIntent(contentIntent())
        if (content.text.isNotEmpty()) builder.setContentText(content.text)
        builder.addAction(pauseResumeAction(isPaused = false))
        builder.addAction(cancelAction())
        return builder.build()
    }

    /** Classic determinate-progress notification for API 26..35 (and 36.0). */
    private fun buildCompatNotification(content: ProgressContent): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(content.title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(content.progressMax, content.progressCurrent, false)
            .setContentIntent(contentIntent())
        if (content.text.isNotEmpty()) builder.setContentText(content.text)
        builder.addAction(compatPauseResumeAction(isPaused = false))
        builder.addAction(compatCancelAction())
        return builder.build()
    }

    /** Static "downloads paused" card with Resume + Cancel actions. */
    private fun buildPausedNotification(): Notification {
        if (!liveUpdateCapable) {
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(context.getString(R.string.notification_download_paused))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(UNIT_PER_TRACK, 0, false)
                .setContentIntent(contentIntent())
                .addAction(compatPauseResumeAction(isPaused = true))
                .addAction(compatCancelAction())
                .build()
        }
        return buildLiveUpdatePausedNotification()
    }

    @android.annotation.SuppressLint("NewApi")
    private fun buildLiveUpdatePausedNotification(): Notification {
        val style = Notification.ProgressStyle()
            .setProgress(0)
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(UNIT_PER_TRACK)))
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(context.getString(R.string.notification_download_paused))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(style)
            .setShortCriticalText(context.getString(R.string.notification_download_paused_chip))
            .setRequestPromotedOngoing(true)
            .setContentIntent(contentIntent())
            .addAction(pauseResumeAction(isPaused = true))
            .addAction(cancelAction())
            .build()
    }

    /**
     * Notification handed to [DownloadService.startForeground]. The service may
     * call this before any track has started (state still empty), so fall back
     * to a "preparing" placeholder rather than returning null.
     *
     * This is the exact call that crashed every download in v0.5.0-v0.5.2 on
     * devices below Android 16 QPR1: it unconditionally built a
     * Notification.ProgressStyle (API 36) with 36.1-only promoted-ongoing
     * calls. Both paths are now gated on [liveUpdateCapable].
     */
    fun currentForegroundNotification(): Notification = buildNotification(state.value) ?: buildPlaceholderNotification()

    private fun buildPlaceholderNotification(): Notification {
        if (!liveUpdateCapable) {
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(context.getString(R.string.notification_download_preparing))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(UNIT_PER_TRACK, 0, true)
                .setContentIntent(contentIntent())
                .addAction(compatCancelAction())
                .build()
        }
        return buildLiveUpdatePlaceholderNotification()
    }

    @android.annotation.SuppressLint("NewApi")
    private fun buildLiveUpdatePlaceholderNotification(): Notification {
        val style = Notification.ProgressStyle()
            .setProgress(0)
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(UNIT_PER_TRACK)))
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(context.getString(R.string.notification_download_preparing))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(style)
            .setShortCriticalText(context.getString(R.string.notification_download_preparing_chip))
            .setRequestPromotedOngoing(true)
            .setContentIntent(contentIntent())
            .addAction(cancelAction())
            .build()
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("NewApi")
    private fun pauseResumeAction(isPaused: Boolean): Notification.Action {
        val intent = PendingIntent.getBroadcast(
            context,
            DownloadActionReceiver.RC_PAUSE_RESUME,
            Intent(context, DownloadActionReceiver::class.java)
                .setAction(DownloadActionReceiver.ACTION_PAUSE_RESUME),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val iconRes = if (isPaused) R.drawable.ic_play_arrow else R.drawable.ic_pause
        val label = context.getString(
            if (isPaused) R.string.notification_download_resume else R.string.notification_download_pause,
        )
        return Notification.Action.Builder(Icon.createWithResource(context, iconRes), label, intent).build()
    }

    @SuppressLint("NewApi")
    private fun cancelAction(): Notification.Action {
        val intent = PendingIntent.getBroadcast(
            context,
            DownloadActionReceiver.RC_CANCEL,
            Intent(context, DownloadActionReceiver::class.java)
                .setAction(DownloadActionReceiver.ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_close),
            context.getString(R.string.notification_download_cancel),
            intent,
        ).build()
    }

    private fun compatPauseResumeAction(isPaused: Boolean): NotificationCompat.Action {
        val intent = PendingIntent.getBroadcast(
            context,
            DownloadActionReceiver.RC_PAUSE_RESUME,
            Intent(context, DownloadActionReceiver::class.java)
                .setAction(DownloadActionReceiver.ACTION_PAUSE_RESUME),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val iconRes = if (isPaused) R.drawable.ic_play_arrow else R.drawable.ic_pause
        val label = context.getString(
            if (isPaused) R.string.notification_download_resume else R.string.notification_download_pause,
        )
        return NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, iconRes),
            label,
            intent,
        ).build()
    }

    private fun compatCancelAction(): NotificationCompat.Action {
        val intent = PendingIntent.getBroadcast(
            context,
            DownloadActionReceiver.RC_CANCEL,
            Intent(context, DownloadActionReceiver::class.java)
                .setAction(DownloadActionReceiver.ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, R.drawable.ic_close),
            context.getString(R.string.notification_download_cancel),
            intent,
        ).build()
    }

    @SuppressLint("NewApi")
    @Suppress("TooGenericExceptionCaught")
    private fun logPromotionDiagnostics(built: Notification) {
        if (!BuildConfig.DEBUG) return
        // hasPromotableCharacteristics / canPostPromotedNotifications are
        // API 36.1 - never touch them on the compat path.
        if (!liveUpdateCapable) return
        try {
            val promotable = built.hasPromotableCharacteristics()
            val canPost = platformNotificationManager.canPostPromotedNotifications()
            val postedFlag = platformNotificationManager.activeNotifications
                .firstOrNull { it.id == NOTIFICATION_ID }
                ?.notification
                ?.let { (it.flags and Notification.FLAG_PROMOTED_ONGOING) != 0 }
            Log.d(
                TAG,
                "Live Update chip: hasPromotableCharacteristics=$promotable, " +
                    "canPostPromotedNotifications=$canPost, postedFlagPromotedOngoing=$postedFlag",
            )
        } catch (e: Throwable) {
            Log.d(TAG, "promotion diagnostics unavailable", e)
        }
    }

    fun ensureChannel() {
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
        private const val TAG = "DownloadNotif"
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 4242

        // ProgressStyle is determinate; 1000 units per track lets us show
        // sub-track byte-level progress within an album/playlist bar.
        private const val UNIT_PER_TRACK = 1000

        private const val NOTIFICATION_DEBOUNCE_MS = 250L
    }
}
