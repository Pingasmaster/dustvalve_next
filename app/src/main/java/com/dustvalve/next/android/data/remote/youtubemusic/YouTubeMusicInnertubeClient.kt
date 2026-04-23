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
    sharedOkHttpClient: OkHttpClient,
) {

    /**
     * Innertube requests must NOT carry the shared CookieJar's cookies. If the
     * jar is empty the call is anonymous (which is what we want for the public
     * feed); if it carries half-set cookies (e.g. an aborted login attempt left
     * some YT cookies behind) the API responds with an empty content section.
     * Reuse the underlying connection pool/dispatcher/interceptors via
     * newBuilder(), then explicitly drop the cookie jar.
     */
    private val okHttpClient: OkHttpClient = sharedOkHttpClient.newBuilder()
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun browse(
        browseId: String,
        params: String? = null,
    ): JsonElement = post(
        endpoint = "browse?prettyPrint=false",
        body = buildJsonObject {
            put("context", clientContext())
            put("browseId", browseId)
            if (params != null) put("params", params)
        },
    )

    suspend fun browseContinuation(continuation: String): JsonElement = post(
        endpoint = "browse?prettyPrint=false&continuation=$continuation&type=next",
        body = buildJsonObject { put("context", clientContext()) },
    )

    suspend fun search(
        query: String,
        params: String? = null,
    ): JsonElement = post(
        endpoint = "search?prettyPrint=false",
        body = buildJsonObject {
            put("context", clientContext())
            put("query", query)
            if (params != null) put("params", params)
        },
    )

    private suspend fun post(endpoint: String, body: JsonObject): JsonElement =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE_URL/$endpoint")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .header("Content-Type", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("X-YouTube-Client-Name", CLIENT_NAME_CODE)
                .header("X-YouTube-Client-Version", CLIENT_VERSION)
                // SOCS=CAI accepts the EU cookie consent gate; without it,
                // Innertube returns an empty content section to anonymous EU
                // users instead of the public feed. CAI = "Consent Accepted,
                // analytics+ads off" - the same value the web UI sets when you
                // dismiss the consent banner.
                .header("Cookie", "SOCS=CAI")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        "Innertube POST /$endpoint failed: HTTP ${response.code} - ${text.take(200)}"
                    )
                }
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
