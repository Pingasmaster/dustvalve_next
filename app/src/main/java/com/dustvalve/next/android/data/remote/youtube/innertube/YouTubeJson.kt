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
 * YouTube (non-Music) thumbnail extraction. Picks the largest by width and
 * returns the URL as-is (no =wH-hH rewrite, since YT video thumbs already
 * encode size in the path like hqdefault.jpg / hq720.jpg).
 */
internal fun JsonElement.extractThumbnail(): String? {
    val thumbnails = path("thumbnail")?.path("thumbnails")?.arr()
        ?: path("thumbnails")?.arr()
        ?: return null
    return thumbnails.maxByOrNull {
        (it.path("width")?.jsonPrimitive?.content?.toIntOrNull() ?: 0)
    }?.str("url")
}
