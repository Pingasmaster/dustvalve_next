package com.dustvalve.next.android.data.remote.youtube.innertube

import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * First-party Innertube client for the standard YouTube (non-Music) API.
 *
 * Endpoint -> client mapping (rationale in [YouTubeClient]):
 *   /player              -> ANDROID_VR_NO_AUTH (primary), IOS (fallback)
 *   /search              -> WEB_NO_AUTH
 *   /browse (channel)    -> WEB_NO_AUTH (richGridRenderer w/ videoRenderer)
 *   /browse (playlist)   -> MWEB_NO_AUTH on m.youtube.com
 *                           (only public client still emitting playlistVideoListRenderer)
 *   /next                -> MWEB_NO_AUTH on m.youtube.com
 *                           (videoWithContextRenderer; WEB now returns lockupViewModel)
 *   /browse (continuation) -> same client as the originating browse:
 *                           MWEB_NO_AUTH for playlists (default),
 *                           WEB_NO_AUTH for channel Videos-tab pagination
 *
 * Innertube must NOT carry the shared CookieJar's cookies; stray half-set
 * login cookies confuse the API. We reuse the shared OkHttp connection
 * pool / dispatcher / interceptors via newBuilder() but drop the cookie jar.
 */
@Singleton
open class YouTubeInnertubeClient @Inject constructor(
    sharedOkHttpClient: OkHttpClient,
    private val visitorDataFetcher: YouTubeVisitorDataFetcher,
    @param:Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /** Overridable in tests to point at MockWebServer. */
    protected open val baseUrlWww: String = BASE_URL_WWW

    /** Overridable in tests to point at MockWebServer. */
    protected open val baseUrlM: String = BASE_URL_M

    private val okHttpClient: OkHttpClient = sharedOkHttpClient.newBuilder()
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Fetches /player by trying clients in cascade order until one returns
     * a streamingData.adaptiveFormats list with at least one audio entry.
     * Throws IllegalStateException with details from the last attempt if
     * every client fails.
     */
    suspend fun player(videoId: String): JsonElement {
        val clients = listOf(YouTubeClient.ANDROID_VR_NO_AUTH, YouTubeClient.IOS)
        var lastError: String? = null
        for (client in clients) {
            val response = try {
                postWithRetry(
                    client = client,
                    endpointPath = "player",
                    queryParams = "",
                ) { cfg ->
                    buildJsonObject {
                        put(
                            "context",
                            buildJsonObject {
                                put("client", client.toContext(cfg.visitorData, cfg.clientVersion))
                            },
                        )
                        put("videoId", videoId)
                        put("contentCheckOk", true)
                        put("racyCheckOk", true)
                    }
                }
            } catch (e: CancellationException) {
                // Caller cancellation must propagate, never be converted
                // into a "failed across all clients" IllegalStateException.
                throw e
            } catch (e: Exception) {
                lastError = "${client.clientName}: ${e.message}"
                continue
            }
            if (hasAudioFormats(response)) return response
            lastError = "${client.clientName}: no audio adaptiveFormats " +
                "(playabilityStatus=${response.path("playabilityStatus")?.path("status")?.toString()})"
        }
        throw IllegalStateException(
            "YouTube /player failed for videoId=$videoId across all clients: $lastError",
        )
    }

    suspend fun search(query: String, params: String? = null): JsonElement {
        val client = YouTubeClient.WEB_NO_AUTH
        return postWithRetry(
            client = client,
            endpointPath = "search",
            queryParams = "",
        ) { cfg ->
            buildJsonObject {
                put(
                    "context",
                    buildJsonObject {
                        put("client", client.toContext(cfg.visitorData, cfg.clientVersion))
                    },
                )
                put("query", query)
                if (params != null) put("params", params)
            }
        }
    }

    /**
     * Generic browse. For browseIds beginning with "VL" (playlist browse),
     * routes via MWEB on m.youtube.com so we get playlistVideoListRenderer
     * instead of the WEB-only lockupViewModel. All other browseIds use WEB.
     */
    suspend fun browse(browseId: String, params: String? = null): JsonElement {
        val client = if (browseId.startsWith("VL")) {
            YouTubeClient.MWEB_NO_AUTH
        } else {
            YouTubeClient.WEB_NO_AUTH
        }
        return postWithRetry(
            client = client,
            endpointPath = "browse",
            queryParams = "",
        ) { cfg ->
            buildJsonObject {
                put(
                    "context",
                    buildJsonObject {
                        put("client", client.toContext(cfg.visitorData, cfg.clientVersion))
                    },
                )
                put("browseId", browseId)
                if (params != null) put("params", params)
            }
        }
    }

    /**
     * Browse continuation. Defaults to MWEB to match the playlist browse
     * routing; callers paginating a WEB browse (channel Videos tab) MUST
     * pass [YouTubeClient.WEB_NO_AUTH] so the continuation comes back in
     * the same richGrid shape as page 1 - an MWEB continuation for a WEB
     * browse parses to zero tracks.
     */
    suspend fun browseContinuation(continuation: String, client: YouTubeClient = YouTubeClient.MWEB_NO_AUTH): JsonElement = postWithRetry(
        client = client,
        endpointPath = "browse",
        queryParams = "&continuation=$continuation&type=next",
    ) { cfg ->
        buildJsonObject {
            put(
                "context",
                buildJsonObject {
                    put("client", client.toContext(cfg.visitorData, cfg.clientVersion))
                },
            )
        }
    }

    /**
     * Watch-next / related videos. Uses MWEB so we get videoWithContextRenderer.
     *
     * videoId is optional: genre mixes (`RDGMEM*`) accept playlistId alone.
     * playlistIndex + params are used when paginating through a Mix - pass
     * the last track's playlist index and the watchEndpoint.params from the
     * previous page's last item.
     */
    suspend fun next(
        videoId: String? = null,
        playlistId: String? = null,
        playlistIndex: Int? = null,
        params: String? = null,
    ): JsonElement {
        val client = YouTubeClient.MWEB_NO_AUTH
        return postWithRetry(
            client = client,
            endpointPath = "next",
            queryParams = "",
        ) { cfg ->
            buildJsonObject {
                put(
                    "context",
                    buildJsonObject {
                        put("client", client.toContext(cfg.visitorData, cfg.clientVersion))
                    },
                )
                if (videoId != null) put("videoId", videoId)
                if (playlistId != null) put("playlistId", playlistId)
                if (playlistIndex != null) put("playlistIndex", playlistIndex)
                if (params != null) put("params", params)
            }
        }
    }

    private fun hasAudioFormats(response: JsonElement): Boolean {
        val formats = response.path("streamingData")?.path("adaptiveFormats")?.arr() ?: return false
        return formats.any { it.str("mimeType")?.startsWith("audio/") == true }
    }

    /**
     * POSTs with the cached visitor config; on HTTP 400/401/403 (Google
     * rotated the visitorData / clientVersion server-side) invalidates the
     * cache and retries exactly once with a freshly scraped config. Any
     * failure of the retry propagates - no retry loops.
     */
    private suspend fun postWithRetry(
        client: YouTubeClient,
        endpointPath: String,
        queryParams: String,
        buildBody: (YouTubeVisitorDataFetcher.VisitorConfig) -> JsonObject,
    ): JsonElement {
        val cfg = visitorDataFetcher.get()
        return try {
            post(client, endpointPath, cfg, queryParams, buildBody(cfg))
        } catch (_: StaleVisitorConfigException) {
            visitorDataFetcher.invalidate()
            val fresh = visitorDataFetcher.get()
            post(client, endpointPath, fresh, queryParams, buildBody(fresh))
        }
    }

    private suspend fun post(
        client: YouTubeClient,
        endpointPath: String,
        visitor: YouTubeVisitorDataFetcher.VisitorConfig,
        queryParams: String,
        body: JsonObject,
    ): JsonElement = withContext(ioDispatcher) {
        val base = if (client == YouTubeClient.MWEB_NO_AUTH) baseUrlM else baseUrlWww
        val url = "$base/$endpointPath?prettyPrint=false$queryParams"
        val clientVersion = client.resolveClientVersion(visitor.clientVersion)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", client.userAgent)
            .header("X-Origin", client.origin)
            .header("Origin", client.origin)
            .header("Referer", client.referer)
            .header("Content-Type", "application/json")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("X-YouTube-Client-Name", client.clientNameCode)
            .header("X-YouTube-Client-Version", clientVersion)
            .header("X-Goog-Api-Format-Version", "1")
            .header("X-Goog-Visitor-Id", visitor.visitorData)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                val message =
                    "YouTube Innertube POST /$endpointPath failed: HTTP ${response.code} - ${text.take(200)}"
                throw if (response.code in STALE_VISITOR_HTTP_CODES) {
                    StaleVisitorConfigException(message)
                } else {
                    IllegalStateException(message)
                }
            }
            if (text.isEmpty()) {
                throw IllegalStateException("YouTube Innertube POST /$endpointPath returned empty body")
            }
            json.parseToJsonElement(text)
        }
    }

    /**
     * HTTP 400/401/403 marker: the cached visitorData / clientVersion is
     * likely stale. Subclass of IllegalStateException so callers that only
     * know the generic contract keep working when the retry also fails.
     */
    private class StaleVisitorConfigException(message: String) : IllegalStateException(message)

    companion object {
        private const val BASE_URL_WWW = "https://www.youtube.com/youtubei/v1"
        private const val BASE_URL_M = "https://m.youtube.com/youtubei/v1"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val STALE_VISITOR_HTTP_CODES = setOf(400, 401, 403)
    }
}
