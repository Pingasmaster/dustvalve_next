package com.dustvalve.next.android.data.remote.youtubemusic

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

internal fun JsonElement.runsText(key: String): String? {
    val runs = path(key)?.path("runs")?.arr()
    val fromRuns = runs?.firstOrNull()?.str("text")
    if (fromRuns != null) return fromRuns
    return path(key)?.path("simpleText")?.str()
}

internal fun JsonElement.extractMusicThumbnail(): String? {
    val thumbnails = path("thumbnail")?.path("musicThumbnailRenderer")
        ?.path("thumbnail")?.path("thumbnails")?.arr()
        ?: path("thumbnailRenderer")?.path("musicThumbnailRenderer")
            ?.path("thumbnail")?.path("thumbnails")?.arr()
        ?: path("thumbnail")?.path("thumbnails")?.arr()
        ?: return null

    return thumbnails.maxByOrNull { it.path("width")?.jsonPrimitive?.content?.toIntOrNull() ?: 0 }
        ?.str("url")
        ?.replace(Regex("=w\\d+-h\\d+.*$"), "=w544-h544-l90-rj")
}
