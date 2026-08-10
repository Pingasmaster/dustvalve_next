package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.remote.DustvalveStreamResolver
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.StreamPolicy
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.SoundCloudRepository
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import java.io.IOException

/**
 * Resolves a track to a concrete HTTPS download URL + container format.
 * Extracted from [DownloadRepositoryImpl] so that class stays under the
 * TooManyFunctions ceiling.
 */
internal class DownloadSourceResolver(
    private val youtubeRepository: YouTubeRepository,
    private val soundCloudRepository: SoundCloudRepository,
    private val dustvalveStreamResolver: DustvalveStreamResolver,
) {
    /**
     * YouTube watch-page -> resolved audio stream; SoundCloud resolves a
     * progressive CDN URL on demand (parsed tracks ship streamUrl=null);
     * Bandcamp always re-resolves mp3-128 (CDN tokens expire ~24h);
     * otherwise the raw streamUrl as mp3-128.
     */
    suspend fun resolve(track: Track): Pair<String?, AudioFormat> = when (track.source) {
        TrackSource.YOUTUBE -> resolveYouTube(track)
        TrackSource.SOUNDCLOUD -> resolveSoundCloud(track)
        TrackSource.BANDCAMP -> resolveBandcamp(track)
        else -> track.streamUrl to AudioFormat.MP3_128
    }

    private suspend fun resolveYouTube(track: Track): Pair<String, AudioFormat> {
        // YouTube tracks store watch page URL in streamUrl; resolve actual audio stream.
        // Queue tracks may have resolved googlevideo.com URLs - reconstruct the watch URL.
        val streamUrl = track.streamUrl
            ?: throw IOException("Track '${track.title}' has no video URL")
        val videoUrl = if (streamUrl.contains("youtube.com") || streamUrl.contains("youtu.be")) {
            streamUrl
        } else {
            val videoId = track.id.removePrefix("yt_")
            "https://www.youtube.com/watch?v=$videoId"
        }
        return youtubeRepository.getDownloadableStream(videoUrl)
    }

    private suspend fun resolveSoundCloud(track: Track): Pair<String, AudioFormat> {
        if (track.isStreamOnlyOrBlocked) {
            throw IOException(
                when (track.streamPolicy) {
                    StreamPolicy.STREAM_ONLY -> "HLS-only, play only"

                    else ->
                        "This SoundCloud track is DRM-protected or requires Go+ and cannot be downloaded"
                },
            )
        }
        return soundCloudRepository.getDownloadableStream(track)
    }

    private suspend fun resolveBandcamp(track: Track): Pair<String, AudioFormat> {
        val pageUrl = track.albumUrl.takeIf { it.isNotBlank() }
            ?: track.bandcampTrackUrl
            ?: throw IOException("Track '${track.title}' has no Bandcamp page URL to re-resolve")
        // DustvalveStreamResolver returns an existing streamUrl untouched,
        // so blank it first - same as PlaybackStreamResolver.reResolveBandcamp.
        val freshUrl = dustvalveStreamResolver.resolveStreamUrl(
            track.copy(streamUrl = null),
            pageUrl,
        ) ?: throw IOException("Track '${track.title}' has no downloadable stream after re-resolve")
        return freshUrl to AudioFormat.MP3_128
    }
}
