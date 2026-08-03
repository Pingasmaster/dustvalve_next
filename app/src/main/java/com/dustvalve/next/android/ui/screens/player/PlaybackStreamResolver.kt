package com.dustvalve.next.android.ui.screens.player

import com.dustvalve.next.android.data.remote.DustvalveStreamResolver
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.SoundCloudRepository
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import com.dustvalve.next.android.domain.usecase.ResolveTrackForPlaybackUseCase
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Remote-stream TTL tracking + forced Bandcamp/YouTube/SoundCloud re-resolve.
 * Keeps [PlayerViewModel] free of the expiry / auto-recovery bookkeeping.
 */
class PlaybackStreamResolver @Inject constructor(
    private val youtubeRepository: YouTubeRepository,
    private val soundCloudRepository: SoundCloudRepository,
    private val dustvalveStreamResolver: DustvalveStreamResolver,
    private val downloadRepository: DownloadRepository,
    private val resolveTrackForPlayback: ResolveTrackForPlaybackUseCase,
) {
    private val streamResolvedAtMs = HashMap<String, Long>()

    fun recordResolved(trackId: String) {
        streamResolvedAtMs[trackId] = System.currentTimeMillis()
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
        val pageUrl = track.albumUrl.takeIf { it.isNotBlank() } ?: track.bandcampTrackUrl
        if (pageUrl.isNullOrBlank()) return null
        // The resolver returns track.streamUrl untouched when it is set, so
        // blank it to force a fresh album-page fetch.
        val freshUrl = dustvalveStreamResolver.resolveStreamUrl(track.copy(streamUrl = null), pageUrl)
            ?: return null
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
            val staleBandcampStream = track.source == TrackSource.BANDCAMP &&
                !track.streamUrl.isNullOrBlank() &&
                isResolutionStale(track)
            if (staleBandcampStream && downloadRepository.getDownloadInfo(track.id) == null) {
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
