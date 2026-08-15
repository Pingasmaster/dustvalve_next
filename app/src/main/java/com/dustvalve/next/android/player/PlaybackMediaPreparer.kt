package com.dustvalve.next.android.player

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.util.ThumbnailUrls
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Resolves queue tracks and prepares ExoPlayer media items for [PlaybackManager].
 */
internal class PlaybackMediaFlows(
    val isPlaying: MutableStateFlow<Boolean>,
    val playbackState: MutableStateFlow<Int>,
    val playbackError: MutableStateFlow<PlaybackException?>,
    val currentPosition: MutableStateFlow<Long>,
)

internal class PlaybackMediaHooks(
    val onSeekFlag: (Boolean) -> Unit,
    val consumeResume: (Track) -> Long,
    val streamResolver: () -> (suspend (Track) -> Track?)?,
    val streamIsStale: () -> ((Track) -> Boolean)?,
)

internal class PlaybackMediaPreparer(
    private val player: ExoPlayer,
    private val queueManager: QueueManager,
    private val context: Context,
    private val flows: PlaybackMediaFlows,
    private val hooks: PlaybackMediaHooks,
) {
    private val isPlaying get() = flows.isPlaying
    private val playbackState get() = flows.playbackState
    private val playbackError get() = flows.playbackError
    private val currentPosition get() = flows.currentPosition
    private val onSeekFlag get() = hooks.onSeekFlag
    private val consumeResume get() = hooks.consumeResume
    private val streamResolver get() = hooks.streamResolver
    private val streamIsStale get() = hooks.streamIsStale

    private var serviceStarted = false

    fun markServiceStopped() {
        serviceStarted = false
    }

    /** YouTube watch-page / short-link URLs are HTML pages, not media streams. */
    fun isWatchPageUrl(url: String): Boolean = // Also matches music.youtube.com/watch and m.youtube.com/watch.
        url.contains("youtube.com/watch") || url.contains("youtu.be/")

    /**
     * True when [track] cannot be handed to ExoPlayer as-is. Defense in depth:
     * during the background-resolution window every not-yet-resolved YouTube
     * entry still carries its watch-page URL in streamUrl, and the old
     * isNullOrBlank-only guard let skip/jump/auto-advance feed ExoPlayer HTML.
     */
    fun trackNeedsResolution(track: Track): Boolean {
        val url = track.streamUrl
        if (url.isNullOrBlank()) return true
        if (isWatchPageUrl(url)) return true
        if (!track.isLocal && streamIsStale()?.invoke(track) == true) return true
        return false
    }

    fun ensureServiceStarted() {
        if (serviceStarted) return
        val intent = Intent(context, PlaybackService::class.java)
        // Mirror DownloadController.startServiceIfPossible: Android 12+ can
        // refuse a background FGS start. Playback still proceeds on the
        // in-process ExoPlayer; the notification/service just may not stick.
        try {
            ContextCompat.startForegroundService(context, intent)
            serviceStarted = true
        } catch (e: IllegalStateException) {
            // Includes ForegroundServiceStartNotAllowedException (API 31+).
            android.util.Log.w("PlaybackManager", "Could not start PlaybackService", e)
        } catch (e: SecurityException) {
            android.util.Log.w("PlaybackManager", "Could not start PlaybackService", e)
        }
    }

    /**
     * Resolves [first] (and, if it stays unplayable, its successors) before
     * playback. Preserves the long-standing bounded skip-unplayable behavior.
     */
    @OptIn(UnstableApi::class)
    suspend fun resolveAndPlay(first: Track) {
        val startIndex = queueManager.currentIndex.value
        var candidate = first
        var advanced = 0
        val queueSize = queueManager.queue.value.size
        while (true) {
            val playable = resolveCandidate(candidate)
            if (playable != null) {
                playResolvedTrack(playable)
                return
            }
            android.util.Log.w("PlaybackManager", "Cannot play track '${candidate.title}': no playable stream URL")
            val nextCandidate = if (advanced >= queueSize) null else queueManager.next()
            if (nextCandidate == null) break
            candidate = nextCandidate
            advanced++
        }
        if (startIndex >= 0 && startIndex != queueManager.currentIndex.value) {
            queueManager.skipToIndex(startIndex)
        }
        playbackError.value = PlaybackException(
            "No playable stream URL for '${first.title}'",
            null,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )
        isPlaying.value = player.isPlaying
        playbackState.value = player.playbackState
    }

    private suspend fun resolveCandidate(track: Track): Track? {
        if (!trackNeedsResolution(track)) return track
        val resolver = streamResolver() ?: return null
        val resolved = try {
            resolver(track)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            android.util.Log.w("PlaybackManager", "Stream resolution failed for '${track.title}'", e)
            null
        } catch (e: IllegalArgumentException) {
            android.util.Log.w("PlaybackManager", "Stream resolution failed for '${track.title}'", e)
            null
        } catch (e: IllegalStateException) {
            android.util.Log.w("PlaybackManager", "Stream resolution failed for '${track.title}'", e)
            null
        }
        val url = resolved?.streamUrl
        if (resolved == null || url.isNullOrBlank() || isWatchPageUrl(url)) return null
        queueManager.applyResolvedTracks(mapOf(resolved.id to resolved))
        return resolved
    }

    @OptIn(UnstableApi::class)
    fun playResolvedTrack(track: Track) {
        val url = track.streamUrl ?: return
        ensureServiceStarted()

        onSeekFlag(false)
        playbackError.value = null

        val currentIndex = queueManager.currentIndex.value
        val queueSize = queueManager.queue.value.size

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.albumTitle)
            .setTrackNumber(if (currentIndex >= 0) currentIndex + 1 else null)
            .setTotalTrackCount(if (queueSize > 0) queueSize else null)

        if (track.artUrl.isNotBlank()) {
            try {
                val art = ThumbnailUrls.canonicalize(track.artUrl)
                metadataBuilder.setArtworkUri(art.toUri())
            } catch (_: IllegalArgumentException) {
                // Ignore malformed artwork URIs
            }
        }

        val resolvedUri = if (url.startsWith("/")) File(url).toUri().toString() else url

        val mediaItem = MediaItem.Builder()
            .setUri(resolvedUri)
            .setMediaId(track.id)
            .setPlaybackCacheKey(track.id, resolvedUri)
            .setMediaMetadata(metadataBuilder.build())
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        val resumeAt = consumeResume(track)
        if (resumeAt > 0L) {
            player.seekTo(resumeAt)
            currentPosition.value = resumeAt
        }
        player.play()
    }
}

/**
 * Stable SimpleCache key for remote streams. YouTube/Bandcamp/SoundCloud
 * CDN URLs rotate host/expiry/signature on every resolve; keying by track
 * id means the second play hits bytes already on disk. Local file:// and
 * content:// URIs skip this so we do not re-cache a file the downloads
 * tree already holds.
 */
@OptIn(UnstableApi::class)
internal fun MediaItem.Builder.setPlaybackCacheKey(trackId: String, uri: String): MediaItem.Builder {
    if (uri.startsWith("https://") || uri.startsWith("http://")) {
        setCustomCacheKey(trackId)
    }
    return this
}
