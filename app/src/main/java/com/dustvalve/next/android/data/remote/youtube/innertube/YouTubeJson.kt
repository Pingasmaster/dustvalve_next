package com.dustvalve.next.android.data.remote.youtube.innertube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

internal fun JsonElement.path(key: String): JsonElement? =
    (this as? JsonObject)?.get(key)

internal fun JsonElement.arr(): JsonArray? = this as? JsonArray

internal fun JsonElement.str(): String? =
    (this as? JsonPrimitive)?.let { if (it.isString) it.content else null }

internal fun JsonElement.str(key: String): String? = path(key)?.str()

internal fun JsonElement.int(key: String): Int? =
    (path(key) as? JsonPrimitive)?.content?.toIntOrNull()

internal fun JsonElement.long(key: String): Long? =
    (path(key) as? JsonPrimitive)?.content?.toLongOrNull()

internal fun JsonElement.runsText(key: String): String? {
    val runs = path(key)?.path("runs")?.arr()
    val fromRuns = runs?.firstOrNull()?.str("text")
    if (fromRuns != null) return fromRuns
    return path(key)?.path("simpleText")?.str()
        ?: path(key)?.path("content")?.str()
}

/**
 * First `browseEndpoint.browseId` found inside the `runs` array at [key]. YT
 * bylines (shortBylineText, longBylineText) carry the channel's UC… id here.
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
 * YouTube (non-Music) thumbnail extraction. Picks the largest by width and
 * then rewrites the URL to request a higher-resolution variant when the URL
 * shape supports it — see [bumpYtThumbnailResolution].
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
 * Request a higher-resolution thumbnail from YouTube / Google CDNs when the
 * URL shape advertises a size. Safe to call on any YouTube thumbnail string:
 * unrecognised shapes are returned unchanged.
 *
 * - `i.ytimg.com/vi/<id>/hqdefault.jpg` (480×360) → `hq720.jpg` (1280×720)
 * - `i.ytimg.com/vi/<id>/hq1.jpg .. hq3.jpg` (first-frame thumbs, 120×90) →
 *   `hqdefault.jpg` (480×360)
 * - Google avatar CDNs (`yt3.googleusercontent.com`, `lh3.googleusercontent.com`,
 *   `yt4.ggpht.com`, etc.) using `=sN-...` or `=wN-hM-...` params →
 *   bump to 720. Other segments (crop, rounding, no-rj) are preserved.
 *
 * We deliberately do NOT promote `hqdefault.jpg` → `maxresdefault.jpg` because
 * `maxresdefault` 404s for older / lower-tier uploads (`hq720` is always
 * present when the source is HD).
 */
internal fun bumpYtThumbnailResolution(url: String): String {
    // i.ytimg.com paths
    if (url.contains("/hq1.jpg") || url.contains("/hq2.jpg") || url.contains("/hq3.jpg")) {
        return url
            .replace("/hq1.jpg", "/hqdefault.jpg")
            .replace("/hq2.jpg", "/hqdefault.jpg")
            .replace("/hq3.jpg", "/hqdefault.jpg")
    }
    if (url.endsWith("/hqdefault.jpg") || url.contains("/hqdefault.jpg?")) {
        return url.replace("/hqdefault.jpg", "/hq720.jpg")
    }
    // googleusercontent / ggpht avatar + album-art CDNs
    if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
        val sRewritten = url.replace(Regex("=s\\d+(-[^=&]*)?"), "=s720$1")
        if (sRewritten != url) return sRewritten
        val whRewritten = url.replace(Regex("=w\\d+-h\\d+(-[^=&]*)?"), "=w720-h720$1")
        if (whRewritten != url) return whRewritten
    }
    return url
}
