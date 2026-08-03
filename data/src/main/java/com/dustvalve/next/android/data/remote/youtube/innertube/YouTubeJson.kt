package com.dustvalve.next.android.data.remote.youtube.innertube

import com.dustvalve.next.android.util.ThumbnailUrls
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

internal fun JsonElement.path(key: String): JsonElement? = (this as? JsonObject)?.get(key)

internal fun JsonElement.arr(): JsonArray? = this as? JsonArray

internal fun JsonElement.str(): String? = (this as? JsonPrimitive)?.let { if (it.isString) it.content else null }

internal fun JsonElement.str(key: String): String? = path(key)?.str()

internal fun JsonElement.int(key: String): Int? = (path(key) as? JsonPrimitive)?.content?.toIntOrNull()

/**
 * Boolean at [key]. Innertube emits real JSON booleans (e.g. tabRenderer's
 * `selected`), which [str] deliberately rejects (isString == false) - use
 * this instead. Accepts "true"/"false" strings too, for defensiveness.
 */
internal fun JsonElement.bool(key: String): Boolean? = (path(key) as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

internal fun JsonElement.long(key: String): Long? = (path(key) as? JsonPrimitive)?.content?.toLongOrNull()

internal fun JsonElement.runsText(key: String): String? {
    val runs = path(key)?.path("runs")?.arr()
    val fromRuns = runs?.firstOrNull()?.str("text")
    if (fromRuns != null) return fromRuns
    return path(key)?.path("simpleText")?.str()
        ?: path(key)?.path("content")?.str()
}

/**
 * First `browseEndpoint.browseId` found inside the `runs` array at [key]. YT
 * bylines (shortBylineText, longBylineText) carry the channel's UC... id here.
 */
internal fun JsonElement.runsBrowseId(key: String): String? {
    val runs = path(key)?.path("runs")?.arr() ?: return null
    for (run in runs) {
        val id = run.path("navigationEndpoint")?.path("browseEndpoint")?.str("browseId")
        if (id != null) return id
    }
    return null
}

/**
 * YouTube thumbnail extraction. Picks the largest by width and then rewrites
 * the URL to the canonical full-quality variant - see
 * [com.dustvalve.next.android.util.ThumbnailUrls.canonicalize].
 */
internal fun JsonElement.extractThumbnail(): String? {
    val thumbnails = path("thumbnail")?.path("thumbnails")?.arr()
        ?: path("thumbnails")?.arr()
        ?: return null
    val raw = thumbnails.maxByOrNull {
        (it.path("width")?.jsonPrimitive?.content?.toIntOrNull() ?: 0)
    }?.str("url") ?: return null
    return bumpYtThumbnailResolution(raw)
}

/**
 * Page-header "image.sources" arrays (channel avatar, playlist hero). Same
 * max-by-width + canonicalize policy as [extractThumbnail].
 */
internal fun JsonElement.extractImageSources(): String? {
    val sources = path("image")?.path("sources")?.arr()
        ?: path("sources")?.arr()
        ?: return null
    val raw = sources.maxByOrNull {
        (it.path("width")?.jsonPrimitive?.content?.toIntOrNull() ?: 0)
    }?.str("url") ?: return null
    return bumpYtThumbnailResolution(raw)
}

/**
 * Canonical full-quality YouTube / YT Music / Bandcamp thumbnail URL.
 * Delegates to [com.dustvalve.next.android.util.ThumbnailUrls] so parsers and
 * the Coil interceptor share one policy (one download, one disk-cache key).
 */
internal fun bumpYtThumbnailResolution(url: String): String = ThumbnailUrls.canonicalize(url)

/**
 * Continuation token from either the legacy continuationItemRenderer or the
 * modern continuationItemViewModel shape used by lockup-based feeds.
 */
internal fun JsonElement.extractContinuationToken(): String? {
    path("continuationItemRenderer")
        ?.path("continuationEndpoint")
        ?.path("continuationCommand")
        ?.str("token")
        ?.let { return it }
    return path("continuationItemViewModel")
        ?.path("continuationCommand")
        ?.path("innertubeCommand")
        ?.path("continuationCommand")
        ?.str("token")
}

/**
 * Parsed fields from a LOCKUP_CONTENT_TYPE_VIDEO lockupViewModel (modern
 * playlist rows and channel Videos-tab richItem content). Returns null for
 * non-video lockups (albums, playlists, etc.).
 */
internal data class LockupVideo(
    val videoId: String,
    val title: String,
    val artist: String,
    val artistChannelId: String?,
    val artUrl: String,
    val durationSec: Float,
)

internal fun JsonElement.parseLockupVideo(): LockupVideo? {
    if (str("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return null
    val videoId = str("contentId")
        ?: path("rendererContext")
            ?.path("commandContext")
            ?.path("onTap")
            ?.path("innertubeCommand")
            ?.path("watchEndpoint")
            ?.str("videoId")
        ?: return null
    val meta = path("metadata")?.path("lockupMetadataViewModel") ?: return null
    val title = meta.path("title")?.str("content") ?: return null
    val artist = meta.path("metadata")
        ?.path("contentMetadataViewModel")
        ?.path("metadataRows")?.arr()?.firstOrNull()
        ?.path("metadataParts")?.arr()?.firstOrNull()
        ?.path("text")?.str("content")
        .orEmpty()
    val artistChannelId = meta.path("image")
        ?.path("decoratedAvatarViewModel")
        ?.path("rendererContext")
        ?.path("commandContext")
        ?.path("onTap")
        ?.path("innertubeCommand")
        ?.path("browseEndpoint")
        ?.str("browseId")
    val artUrl = path("contentImage")
        ?.path("thumbnailViewModel")
        ?.extractImageSources()
        ?: path("contentImage")
            ?.path("collectionThumbnailViewModel")
            ?.path("primaryThumbnail")
            ?.path("thumbnailViewModel")
            ?.extractImageSources()
        ?: ""
    val durationSec = path("contentImage")
        ?.path("thumbnailViewModel")
        ?.path("overlays")?.arr().orEmpty()
        .asSequence()
        .mapNotNull { overlay ->
            overlay.path("thumbnailBottomOverlayViewModel")
                ?.path("badges")?.arr()?.firstOrNull()
                ?.path("thumbnailBadgeViewModel")
                ?.str("text")
        }
        .firstOrNull()
        ?.let { parseDurationText(it) }
        ?: 0f
    return LockupVideo(
        videoId = videoId,
        title = title,
        artist = artist,
        artistChannelId = artistChannelId,
        artUrl = artUrl,
        durationSec = durationSec,
    )
}

/** Parses "h:mm:ss" / "m:ss" / "ss" duration badge text into total seconds. */
internal fun parseDurationText(text: String): Float {
    val parts = text.split(":").mapNotNull { it.toIntOrNull() }
    if (parts.isEmpty()) return 0f
    var total = 0
    for (p in parts) total = total * SECONDS_PER_MINUTE + p
    return total.toFloat()
}

private const val SECONDS_PER_MINUTE = 60
