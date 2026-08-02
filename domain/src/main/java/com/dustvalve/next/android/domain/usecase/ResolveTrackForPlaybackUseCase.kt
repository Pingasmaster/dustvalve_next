package com.dustvalve.next.android.domain.usecase

import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Picks the best playable URI for a track:
 * 1. Local tracks already carry a content:// URI - returned as-is
 * 2. Downloaded same-or-higher quality file wins over the remote stream
 * 3. YouTube resolves a live googlevideo URL (or null on failure)
 * 4. Otherwise the track's existing stream URL (Bandcamp mp3-128) is used
 */
data class PlaybackResolveResult(
    val track: Track,
    val playbackFormat: AudioFormat?,
    val sourcePath: String?,
    /** YouTube resolution failed; surface a snackbar only when [reportFailure] was true. */
    val streamFailed: Boolean = false,
    /** A remote stream URL was freshly resolved; caller should stamp TTL. */
    val recordedRemoteResolution: Boolean = false,
)

class ResolveTrackForPlaybackUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val youtubeRepository: YouTubeRepository,
) {
    suspend operator fun invoke(track: Track, reportFailure: Boolean = true): PlaybackResolveResult {
        if (track.isLocal) {
            return PlaybackResolveResult(
                track = track,
                playbackFormat = null,
                sourcePath = track.streamUrl,
            )
        }

        if (track.source == TrackSource.YOUTUBE) {
            return resolveYouTube(track, reportFailure)
        }

        val downloadInfo = downloadRepository.getDownloadInfo(track.id)
        if (downloadInfo != null && downloadInfo.format.qualityRank >= AudioFormat.MP3_128.qualityRank) {
            return PlaybackResolveResult(
                track = track.copy(streamUrl = downloadInfo.streamUri),
                playbackFormat = downloadInfo.format,
                sourcePath = downloadInfo.filePath,
            )
        }

        return PlaybackResolveResult(
            track = track,
            playbackFormat = AudioFormat.MP3_128,
            sourcePath = null,
        )
    }

    private suspend fun resolveYouTube(track: Track, reportFailure: Boolean): PlaybackResolveResult {
        val ytDownloadInfo = downloadRepository.getDownloadInfo(track.id)
        if (ytDownloadInfo != null) {
            return PlaybackResolveResult(
                track = track.copy(streamUrl = ytDownloadInfo.streamUri),
                playbackFormat = ytDownloadInfo.format,
                sourcePath = ytDownloadInfo.filePath,
            )
        }

        val watchUrl = youtubeWatchUrl(track) ?: return PlaybackResolveResult(
            track = track.copy(streamUrl = null),
            playbackFormat = null,
            sourcePath = null,
        )

        return try {
            val streamUrl = youtubeRepository.getStreamUrl(watchUrl)
            PlaybackResolveResult(
                track = track.copy(streamUrl = streamUrl),
                playbackFormat = null,
                sourcePath = null,
                recordedRemoteResolution = true,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            youtubeStreamFailed(track, reportFailure)
        } catch (_: IllegalStateException) {
            youtubeStreamFailed(track, reportFailure)
        } catch (_: IllegalArgumentException) {
            youtubeStreamFailed(track, reportFailure)
        }
    }

    private fun youtubeStreamFailed(track: Track, reportFailure: Boolean) = PlaybackResolveResult(
        track = track.copy(streamUrl = null),
        playbackFormat = null,
        sourcePath = null,
        streamFailed = reportFailure,
    )

    companion object {
        /**
         * Prefer an explicit watch URL on the track; otherwise rebuild from the
         * `yt_<videoId>` id (same reconstruction DownloadRepositoryImpl uses).
         */
        fun youtubeWatchUrl(track: Track): String? {
            track.streamUrl
                ?.takeIf { it.contains("youtube.com/watch") || it.contains("youtu.be/") }
                ?.let { return it }
            return track.id.removePrefix("yt_")
                .takeIf { it.isNotBlank() && it != track.id }
                ?.let { "https://www.youtube.com/watch?v=$it" }
        }
    }
}
