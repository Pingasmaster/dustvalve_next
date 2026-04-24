package com.dustvalve.next.android.data.remote.youtube.innertube

import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses /player Innertube responses produced by the no-auth clients
 * (ANDROID_VR / IOS). All audio adaptiveFormats here carry direct
 * ("url"-keyed) URLs; we never see signatureCipher because we never call
 * with a Premium-tier or web client.
 */
@Singleton
class YouTubePlayerParser @Inject constructor() {

    /**
     * Picks the best audio adaptive format from a /player response.
     * "Best" = highest bitrate, with Opus tie-broken above AAC.
     */
    fun parsePlayerStreamInfo(playerJson: JsonElement): PlayerStreamInfo {
        val formats = playerJson.path("streamingData")?.path("adaptiveFormats")?.arr()
            ?: throw IllegalStateException(
                "YouTube /player response has no streamingData.adaptiveFormats: " +
                    "playabilityStatus=${playerJson.path("playabilityStatus")?.toString()?.take(200)}"
            )

        val audio = formats.filter { it.str("mimeType")?.startsWith("audio/") == true }
        if (audio.isEmpty()) {
            throw IllegalStateException(
                "YouTube /player response has no audio formats (had ${formats.size} adaptiveFormats); " +
                    "playabilityStatus=${playerJson.path("playabilityStatus")?.toString()?.take(200)}"
            )
        }

        val best = audio.maxWithOrNull(
            compareBy<JsonElement> { it.int("bitrate") ?: 0 }
                .thenBy { if (it.str("mimeType")?.contains("opus") == true) 1 else 0 }
        ) ?: audio.first()

        val mime = best.str("mimeType") ?: ""
        val url = best.str("url")
            ?: throw IllegalStateException(
                "YouTube /player best audio format missing url field; mime=$mime"
            )
        val bitrate = best.int("bitrate") ?: 0

        return PlayerStreamInfo(
            streamUrl = url,
            format = mimeToFormat(mime),
            bitrate = bitrate,
            mimeType = mime,
        )
    }

    /**
     * Builds a domain Track from a /player response. The streamUrl is set to
     * the watch?v=<id> form so the existing PlayerViewModel resolution path
     * keeps working: it re-resolves at play time via getStreamUrl(videoUrl).
     */
    fun parseTrack(playerJson: JsonElement, videoId: String): Track {
        val details = playerJson.path("videoDetails")
            ?: throw IllegalStateException("YouTube /player response missing videoDetails")
        val title = details.str("title") ?: "Unknown"
        val artist = details.str("author") ?: "Unknown"
        val duration = (details.str("lengthSeconds")?.toFloatOrNull()) ?: 0f
        val thumbs = details.path("thumbnail")?.path("thumbnails")?.arr() ?: emptyList<JsonElement>()
        val art = thumbs.maxByOrNull { it.int("width") ?: 0 }?.str("url") ?: ""
        val channelId = details.str("channelId")
        val artistUrl = if (!channelId.isNullOrBlank()) {
            "https://www.youtube.com/channel/$channelId"
        } else ""
        return Track(
            id = "yt_$videoId",
            albumId = "yt_album_$videoId",
            title = title,
            artist = artist,
            artistUrl = artistUrl,
            trackNumber = 0,
            duration = duration,
            streamUrl = "https://www.youtube.com/watch?v=$videoId",
            artUrl = art,
            albumTitle = "",
            source = TrackSource.YOUTUBE,
        )
    }

    private fun mimeToFormat(mime: String): AudioFormat = when {
        mime.contains("opus") -> AudioFormat.OPUS
        mime.contains("mp4a") || mime.startsWith("audio/mp4") -> AudioFormat.AAC
        else -> AudioFormat.OPUS
    }
}

data class PlayerStreamInfo(
    val streamUrl: String,
    val format: AudioFormat,
    val bitrate: Int,
    val mimeType: String,
)
