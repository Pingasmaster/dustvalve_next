package com.dustvalve.next.android.data.remote.youtubemusic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeMusicInnertubeClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun browse(
        browseId: String,
        params: String? = null,
    ): JsonElement = post(
        endpoint = "browse",
        body = buildJsonObject {
            put("context", clientContext())
            put("browseId", browseId)
            if (params != null) put("params", params)
        },
    )

    suspend fun browseContinuation(continuation: String): JsonElement = post(
        endpoint = "browse?continuation=$continuation&type=next",
        body = buildJsonObject { put("context", clientContext()) },
    )

    private suspend fun post(endpoint: String, body: JsonObject): JsonElement =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE_URL/$endpoint")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .header("X-YouTube-Client-Name", CLIENT_NAME_CODE)
                .header("X-YouTube-Client-Version", CLIENT_VERSION)
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Innertube POST /$endpoint failed: HTTP ${response.code}")
                }
                val text = response.body.string()
                if (text.isEmpty()) {
                    throw IllegalStateException("Innertube POST /$endpoint returned empty body")
                }
                json.parseToJsonElement(text)
            }
        }

    private fun clientContext(): JsonObject = buildJsonObject {
        put("client", buildJsonObject {
            put("clientName", "WEB_REMIX")
            put("clientVersion", CLIENT_VERSION)
            put("hl", "en")
            put("gl", "US")
        })
    }

    companion object {
        private const val BASE_URL = "https://music.youtube.com/youtubei/v1"
        private const val CLIENT_NAME_CODE = "67"
        private const val CLIENT_VERSION = "1.20260101.01.00"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
