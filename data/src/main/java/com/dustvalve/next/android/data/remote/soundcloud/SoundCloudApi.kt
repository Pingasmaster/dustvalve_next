package com.dustvalve.next.android.data.remote.soundcloud

import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin HTTP client for `api-v2.soundcloud.com`. Attaches [client_id] (and
 * optional [app_version]) to every request; on 401/403 invalidates the cached
 * client_id and retries once with a freshly scraped value.
 */
@Singleton
class SoundCloudApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val clientIdProvider: SoundCloudClientIdProvider,
    @param:Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val appVersion = AtomicReference<String?>(null)
    private val appVersionMutex = Mutex()

    suspend fun charts(genreSlug: String, kind: String = "trending", limit: Int = 50): JsonElement {
        val genre = if (genreSlug.startsWith("soundcloud:genres:")) {
            genreSlug
        } else {
            "soundcloud:genres:$genreSlug"
        }
        return get(
            path = "charts",
            query = mapOf(
                "kind" to kind,
                "genre" to genre,
                "limit" to limit.toString(),
            ),
        )
    }

    suspend fun mixedSelections(): JsonElement = get(path = "mixed-selections")

    suspend fun search(query: String, filter: String? = null, limit: Int = 50): JsonElement {
        val path = when (filter?.lowercase()) {
            "tracks", "track" -> "search/tracks"
            "users", "artists", "artist", "user" -> "search/users"
            "playlists", "playlist", "sets" -> "search/playlists"
            "albums", "album" -> "search/albums"
            else -> "search"
        }
        return get(
            path = path,
            query = mapOf(
                "q" to query,
                "limit" to limit.toString(),
                "linked_partitioning" to "1",
            ),
        )
    }

    suspend fun resolve(url: String): JsonElement = get(
        path = "resolve",
        query = mapOf("url" to url),
    )

    suspend fun track(id: String): JsonElement = get(path = "tracks/$id")

    suspend fun tracksByIds(ids: List<String>): JsonElement {
        require(ids.isNotEmpty()) { "ids must not be empty" }
        return get(
            path = "tracks",
            query = mapOf("ids" to ids.joinToString(",")),
        )
    }

    suspend fun user(id: String): JsonElement = get(path = "users/$id")

    suspend fun userTracks(userId: String, nextHref: String? = null, limit: Int = 50): JsonElement {
        if (nextHref != null) return getAbsolute(nextHref)
        return get(
            path = "users/$userId/tracks",
            query = mapOf(
                "limit" to limit.toString(),
                "linked_partitioning" to "1",
            ),
        )
    }

    suspend fun playlist(id: String): JsonElement = get(path = "playlists/$id")

    /**
     * Resolves a media transcoding URL (`.../stream/progressive` etc.) to the
     * actual CDN stream URL in `{ "url": "..." }`. Pass [trackAuthorization]
     * from the track payload when present - many streams 401 without it.
     */
    suspend fun resolveStream(transcodingUrl: String, trackAuthorization: String? = null): JsonElement {
        if (trackAuthorization.isNullOrBlank()) return getAbsolute(transcodingUrl)
        val withAuth = transcodingUrl.toHttpUrl().newBuilder()
            .setQueryParameter("track_authorization", trackAuthorization)
            .build()
            .toString()
        return getAbsolute(withAuth)
    }

    suspend fun getAppVersion(): String? = appVersionMutex.withLock {
        appVersion.get()?.let { return it }
        return try {
            val body = rawGet(VERSIONS_URL)
            val root = json.parseToJsonElement(body) as? JsonObject ?: return null
            val version = root["app"]?.let {
                (it as? kotlinx.serialization.json.JsonPrimitive)?.content
            }
            if (!version.isNullOrBlank()) {
                appVersion.set(version)
            }
            version
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private suspend fun get(path: String, query: Map<String, String> = emptyMap()): JsonElement {
        val base = (API_BASE + path.trimStart('/')).toHttpUrl().newBuilder()
        for ((k, v) in query) {
            base.addQueryParameter(k, v)
        }
        return getAbsolute(base.build().toString())
    }

    private suspend fun getAbsolute(url: String): JsonElement {
        var attemptedRefresh = false
        while (true) {
            val clientId = clientIdProvider.getClientId()
            val version = getAppVersion()
            val httpUrl = url.toHttpUrl().newBuilder()
                .setQueryParameter("client_id", clientId)
                .apply {
                    if (!version.isNullOrBlank() && this.build().queryParameter("app_version") == null) {
                        setQueryParameter("app_version", version)
                    }
                }
                .build()
            val (code, body) = executeForBody(httpUrl)
            when {
                code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN -> {
                    if (attemptedRefresh) {
                        throw IOException("SoundCloud API $code after client_id refresh")
                    }
                    clientIdProvider.invalidate()
                    attemptedRefresh = true
                }

                body != null -> return json.parseToJsonElement(body)

                else -> throw IOException("SoundCloud API HTTP $code for $httpUrl")
            }
        }
    }

    /**
     * Runs the blocking OkHttp call and body read on [ioDispatcher] (callers
     * sit on viewModelScope / the playback manager's Main.immediate scope).
     * Returns the HTTP code plus the body for 2xx responses (null otherwise).
     */
    private suspend fun executeForBody(url: HttpUrl): Pair<Int, String?> = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Origin", "https://soundcloud.com")
            .header("Referer", "https://soundcloud.com/")
            .get()
            .build()
        val response = okHttpClient.newCall(request).execute()
        if (response.code in HTTP_OK_MIN..HTTP_OK_MAX) {
            response.code to response.use { it.body.string() }
        } else {
            response.close()
            response.code to null
        }
    }

    private suspend fun rawGet(url: String): String = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} fetching $url")
            }
            response.body.string()
        }
    }

    private companion object {
        const val API_BASE = "https://api-v2.soundcloud.com/"
        const val VERSIONS_URL = "https://soundcloud.com/versions.json"
        const val USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Safari/537.36"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_OK_MIN = 200
        const val HTTP_OK_MAX = 299
    }
}
