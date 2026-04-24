package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.local.db.dao.YouTubeMusicHomeCacheDao
import com.dustvalve.next.android.data.local.db.entity.YouTubeMusicHomeCacheEntity
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeInnertubeClient
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlayerParser
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicInnertubeClient
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicParser
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicSearchParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.YouTubeMusicHomeFeed
import com.dustvalve.next.android.domain.repository.YouTubeMusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeMusicRepositoryImpl @Inject constructor(
    private val client: YouTubeMusicInnertubeClient,
    private val parser: YouTubeMusicParser,
    private val searchParser: YouTubeMusicSearchParser,
    private val youtubeInnertubeClient: YouTubeInnertubeClient,
    private val youtubePlayerParser: YouTubePlayerParser,
    private val homeCache: YouTubeMusicHomeCacheDao,
) : YouTubeMusicRepository {

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val HOME_BROWSE_ID = "FEmusic_home"
        // YT Music's home is editorial; refresh in the background once an
        // hour. The cached snapshot is always returned first so the UI
        // never blocks on the network.
        const val HOME_REVALIDATE_MS = 60L * 60L * 1000L
        const val SONGS_PARAMS = "EgWKAQIIAWoMEA4QChADEAQQCRAF"
        const val VIDEOS_PARAMS = "EgWKAQIQAWoMEA4QChADEAQQCRAF"
        const val ALBUMS_PARAMS = "EgWKAQIYAWoMEA4QChADEAQQCRAF"
        const val PLAYLISTS_PARAMS = "EgWKAQIoAWoMEA4QChADEAQQCRAF"
        const val ARTISTS_PARAMS = "EgWKAQIgAWoMEA4QChADEAQQCRAF"
    }

    override suspend fun resolveStreamUrl(videoId: String): String {
        val response = youtubeInnertubeClient.player(videoId)
        return youtubePlayerParser.parsePlayerStreamInfo(response).streamUrl
    }

    override suspend fun getHome(): YouTubeMusicHomeFeed = getHomeCached(key = "home", params = null)

    override suspend fun getMoodHome(params: String): YouTubeMusicHomeFeed =
        getHomeCached(key = "mood:$params", params = params)

    /**
     * Cache-first home/mood loader. The YT Music browse response (raw
     * Innertube JSON) is persisted under [key] and re-parsed on hit, so we
     * avoid hitting the network until [HOME_REVALIDATE_MS] elapses.
     * Background refresh errors are swallowed.
     */
    private suspend fun getHomeCached(key: String, params: String?): YouTubeMusicHomeFeed {
        val cached = homeCache.getByKey(key)
        if (cached != null) {
            val parsed = try {
                parser.parseHome(json.parseToJsonElement(cached.feedJson))
            } catch (_: Throwable) {
                null
            }
            if (parsed != null) {
                val age = System.currentTimeMillis() - cached.cachedAt
                if (age >= HOME_REVALIDATE_MS) {
                    backgroundScope.launch {
                        try { fetchAndCacheHome(key, params) } catch (_: Throwable) {}
                    }
                }
                return parsed
            }
        }
        return fetchAndCacheHome(key, params)
    }

    private suspend fun fetchAndCacheHome(key: String, params: String?): YouTubeMusicHomeFeed {
        val response = if (params == null) {
            client.browse(browseId = HOME_BROWSE_ID)
        } else {
            client.browse(browseId = HOME_BROWSE_ID, params = params)
        }
        val feed = parser.parseHome(response)
        try {
            homeCache.insert(
                YouTubeMusicHomeCacheEntity(key = key, feedJson = response.toString())
            )
        } catch (_: Throwable) {}
        return feed
    }

    override suspend fun search(query: String, filter: String?): List<SearchResult> {
        val params = filterParams(filter)
        val response = client.search(query = query, params = params)
        return searchParser.parse(response)
    }

    /** YT Music search filter params (opaque tokens YT publishes for each filter chip). */
    private fun filterParams(filter: String?): String = when (filter) {
        null, "songs" -> SONGS_PARAMS
        "videos" -> VIDEOS_PARAMS
        "albums" -> ALBUMS_PARAMS
        "playlists" -> PLAYLISTS_PARAMS
        "artists" -> ARTISTS_PARAMS
        else -> SONGS_PARAMS
    }

    /**
     * Two-step YTM album lookup for a track's [videoId]:
     *   1. POST /next → find the first `browseEndpoint` whose
     *      `browseEndpointContextMusicConfig.pageType == MUSIC_PAGE_TYPE_ALBUM`;
     *      short-circuit to null if the video has no YTM album context.
     *   2. POST /browse with that `MPREb_…` id → pluck the `audioPlaylistId`
     *      (the `OLAK5uy_…` playlist the YTM UI uses for the album).
     * Returns a canonical youtube.com/playlist URL so callers can route
     * straight through the shared [com.dustvalve.next.android.data.repository.YouTubeSource]
     * path. Any network or parse failure yields null — the caller treats that
     * as "no album" and stores it idempotently.
     */
    override suspend fun lookupAlbumPlaylistForVideo(videoId: String): String? {
        val nextJson = try {
            client.next(videoId)
        } catch (_: Throwable) {
            return null
        }
        val albumBrowseId = findAlbumBrowseId(nextJson) ?: return null
        val browseJson = try {
            client.browse(albumBrowseId)
        } catch (_: Throwable) {
            return null
        }
        val audioPlaylistId = findAudioPlaylistId(browseJson) ?: return null
        return "https://www.youtube.com/playlist?list=$audioPlaylistId"
    }

    private fun findAlbumBrowseId(root: JsonElement): String? {
        when (root) {
            is JsonObject -> {
                val browseEndpoint = root["browseEndpoint"] as? JsonObject
                if (browseEndpoint != null) {
                    val pageType = ((browseEndpoint["browseEndpointContextSupportedConfigs"]
                        as? JsonObject)
                        ?.get("browseEndpointContextMusicConfig") as? JsonObject)
                        ?.get("pageType")
                        ?.let { (it as? JsonPrimitive)?.content }
                    if (pageType == "MUSIC_PAGE_TYPE_ALBUM") {
                        val id = (browseEndpoint["browseId"] as? JsonPrimitive)?.content
                        if (!id.isNullOrBlank()) return id
                    }
                }
                for (v in root.values) findAlbumBrowseId(v)?.let { return it }
            }
            is JsonArray -> for (v in root) findAlbumBrowseId(v)?.let { return it }
            else -> Unit
        }
        return null
    }

    private fun findAudioPlaylistId(root: JsonElement): String? {
        when (root) {
            is JsonObject -> {
                (root["audioPlaylistId"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                    ?.let { return it }
                for (v in root.values) findAudioPlaylistId(v)?.let { return it }
            }
            is JsonArray -> for (v in root) findAudioPlaylistId(v)?.let { return it }
            else -> Unit
        }
        return null
    }
}
