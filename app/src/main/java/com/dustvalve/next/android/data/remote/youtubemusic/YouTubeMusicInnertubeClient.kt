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
open class YouTubeMusicInnertubeClient @Inject constructor(
    sharedOkHttpClient: OkHttpClient,
    private val visitorDataFetcher: YouTubeMusicVisitorDataFetcher,
) {

    /** Overridable in tests to point at MockWebServer. */
    protected open val baseUrl: String = BASE_URL

    /**
     * Innertube must NOT carry the shared CookieJar's cookies. If the jar is
     * empty the call is anonymous (which is what we want for the public feed);
     * if it carries half-set login cookies (e.g. an aborted YT login left
     * cookies behind) the API responds with a placeholder messageRenderer.
     * We reuse the underlying connection pool / dispatcher / interceptors via
     * newBuilder() and explicitly drop the cookie jar.
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
    ): JsonElement {
        val cfg = visitorDataFetcher.get()
        return post(
            endpoint = "browse?prettyPrint=false",
            visitor = cfg,
            body = buildJsonObject {
                put("context", clientContext(cfg))
                put("browseId", browseId)
                if (params != null) put("params", params)
            },
        )
    }

    suspend fun browseContinuation(continuation: String): JsonElement {
        val cfg = visitorDataFetcher.get()
        return post(
            endpoint = "browse?prettyPrint=false&continuation=$continuation&type=next",
            visitor = cfg,
            body = buildJsonObject { put("context", clientContext(cfg)) },
        )
    }

    suspend fun search(
        query: String,
        params: String? = null,
    ): JsonElement {
        val cfg = visitorDataFetcher.get()
        return post(
            endpoint = "search?prettyPrint=false",
            visitor = cfg,
            body = buildJsonObject {
                put("context", clientContext(cfg))
                put("query", query)
                if (params != null) put("params", params)
            },
        )
    }

    /**
     * YTM /next for a given videoId. The response's tabbed watch-next shelves
     * carry the album link (`MUSIC_PAGE_TYPE_ALBUM` browseId) for music-tagged
     * videos; plain YouTube uploads come back without it.
     */
    suspend fun next(videoId: String): JsonElement {
        val cfg = visitorDataFetcher.get()
        return post(
            endpoint = "next?prettyPrint=false",
            visitor = cfg,
            body = buildJsonObject {
                put("context", clientContext(cfg))
                put("videoId", videoId)
            },
        )
    }

    private suspend fun post(
        endpoint: String,
        visitor: YouTubeMusicVisitorDataFetcher.VisitorConfig,
        body: JsonObject,
    ): JsonElement = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/$endpoint")
            .header("Origin", "https://music.youtube.com")
            .header("X-Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("Content-Type", "application/json")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("X-YouTube-Client-Name", CLIENT_NAME_CODE)
            .header("X-YouTube-Client-Version", visitor.clientVersion)
            .header("X-Goog-Api-Format-Version", "1")
            .header("X-Goog-Visitor-Id", visitor.visitorData)
            // SOCS=CAI accepts the EU cookie consent gate; without it,
            // Innertube returns an empty content section to anonymous EU
            // users instead of the public feed.
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

    private fun clientContext(
        visitor: YouTubeMusicVisitorDataFetcher.VisitorConfig,
    ): JsonObject = buildJsonObject {
        put("client", buildJsonObject {
            put("clientName", "WEB_REMIX")
            put("clientVersion", visitor.clientVersion)
            put("hl", "en")
            put("gl", "US")
            put("visitorData", visitor.visitorData)
        })
    }

    companion object {
        private const val BASE_URL = "https://music.youtube.com/youtubei/v1"
        private const val CLIENT_NAME_CODE = "67"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
