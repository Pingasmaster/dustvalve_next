package com.dustvalve.next.android.ui.screens.player

import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.BandcampStreamUrlResolver
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.SoundCloudRepository
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import com.dustvalve.next.android.domain.usecase.ResolveTrackForPlaybackUseCase
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Remote-stream TTL tracking + forced Bandcamp/YouTube/SoundCloud re-resolve.
 * Keeps [PlayerViewModel] free of the expiry / auto-recovery bookkeeping.
 *
 * @Singleton so the TTL map survives Activity recreation: the @Singleton
 * PlaybackManager keeps whatever hook instance was installed last, and an
 * unscoped resolver used to mean a fresh (empty) TTL map per recreated
 * PlayerViewModel while the manager still consulted the previous instance -
 * two disagreeing sources of truth for stream freshness.
 */
@Singleton
class PlaybackStreamResolver @Inject constructor(
    private val youtubeRepository: YouTubeRepository,
    private val soundCloudRepository: SoundCloudRepository,
    private val bandcampStreamUrlResolver: BandcampStreamUrlResolver,
    private val downloadRepository: DownloadRepository,
    private val resolveTrackForPlayback: ResolveTrackForPlaybackUseCase,
) {
    // Concurrent: PlaybackManager callbacks and PlayerViewModel resolve paths
    // may stamp/read TTL from different threads on the singleton resolver.
    private val streamResolvedAtMs = ConcurrentHashMap<String, Long>()

    /** One-shot auto-recovery guard; cleared on STATE_READY or user play-after-error. */
    private val autoRetriedTrackIds = mutableSetOf<String>()

    fun recordResolved(trackId: String) {
        streamResolvedAtMs[trackId] = System.currentTimeMillis()
    }

    /**
     * Marks [trackId] as older than the TTL so the next play path re-resolves
     * instead of handing ExoPlayer the same dead URL.
     */
    fun invalidateResolution(trackId: String) {
        streamResolvedAtMs[trackId] = 0L
    }

    /** Claims the one-shot auto-retry slot for [trackId]. False if already spent. */
    fun tryClaimAutoRetry(trackId: String): Boolean = autoRetriedTrackIds.add(trackId)

    fun clearAutoRetry(trackId: String? = null) {
        if (trackId == null) {
            autoRetriedTrackIds.clear()
        } else {
            autoRetriedTrackIds.remove(trackId)
        }
    }

    /**
     * Installed as [com.dustvalve.next.android.player.PlaybackManager.streamIsStale].
     * Only tracks whose remote resolution timestamp we know can be stale;
     * scrape-time Bandcamp URLs without a timestamp are played optimistically
     * and covered by the error-path auto-recovery instead.
     */
    fun isResolutionStale(track: Track): Boolean {
        val streamUrl = track.streamUrl
        if (track.isLocal || streamUrl == null || !streamUrl.startsWith("http")) return false
        val resolvedAt = streamResolvedAtMs[track.id] ?: return false
        return System.currentTimeMillis() - resolvedAt > STREAM_URL_TTL_MS
    }

    /** Fetches a genuinely fresh stream URL, bypassing any cached/stale one. */
    suspend fun reResolve(track: Track): Track? = when (track.source) {
        TrackSource.YOUTUBE -> {
            val videoId = track.id.removePrefix("yt_").takeIf { it.isNotBlank() && it != track.id }
            videoId?.let {
                val freshUrl = youtubeRepository.getStreamUrl("https://www.youtube.com/watch?v=$it")
                recordResolved(track.id)
                track.copy(streamUrl = freshUrl)
            }
        }

        TrackSource.SOUNDCLOUD -> {
            val freshUrl = soundCloudRepository.getStreamUrl(track)
            recordResolved(track.id)
            track.copy(streamUrl = freshUrl)
        }

        TrackSource.BANDCAMP -> reResolveBandcamp(track)

        else -> null
    }

    suspend fun reResolveBandcamp(track: Track): Track? {
        val freshUrl = bandcampStreamUrlResolver.resolveStreamUrl(track) ?: return null
        recordResolved(track.id)
        return track.copy(streamUrl = freshUrl)
    }

    /**
     * Installed as PlaybackManager.streamResolver: runs the same resolution
     * as the direct-tap path for entries that skip/auto-advance found
     * unresolved or stale. Returns null when the track cannot be made
     * playable.
     */
    suspend fun resolveOnDemand(track: Track): Track? {
        val resolved = try {
            val needsBandcampRefresh = track.source == TrackSource.BANDCAMP &&
                downloadRepository.getDownloadInfo(track.id) == null &&
                (track.streamUrl.isNullOrBlank() || isResolutionStale(track))
            if (needsBandcampRefresh) {
                reResolveBandcamp(track)
            } else {
                val result = resolveTrackForPlayback(track, reportFailure = false)
                if (result.recordedRemoteResolution) {
                    recordResolved(result.track.id)
                }
                result.track
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        return resolved?.takeIf { !it.streamUrl.isNullOrBlank() }
    }

    companion object {
        /**
         * Freshness window for resolved remote stream URLs. YouTube googlevideo
         * links expire after ~6 h and Bandcamp CDN tokens after a few hours;
         * one hour is comfortably inside both.
         */
        const val STREAM_URL_TTL_MS = 60L * 60L * 1000L
    }
}
