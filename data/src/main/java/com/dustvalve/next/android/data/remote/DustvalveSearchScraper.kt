package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.util.NetworkUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class DustvalveSearchScraper @Inject constructor(
    private val client: OkHttpClient,
    @param:Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Serializable
    private data class SearchRequest(
        @SerialName("search_text") val searchText: String,
        @SerialName("search_filter") val searchFilter: String,
        @SerialName("full_page") val fullPage: Boolean = true,
        @SerialName("fan_id") val fanId: String? = null,
    )

    @Serializable
    private data class SearchEnvelope(val auto: AutoBlock = AutoBlock())

    @Serializable
    private data class AutoBlock(val results: List<SearchItem> = emptyList())

    @Serializable
    private data class SearchItem(
        val type: String = "",
        val name: String = "",
        @SerialName("band_name") val bandName: String? = null,
        @SerialName("album_name") val albumName: String? = null,
        @SerialName("item_url_path") val itemUrlPath: String? = null,
        @SerialName("item_url_root") val itemUrlRoot: String? = null,
        val img: String? = null,
        @SerialName("tag_names") val tagNames: List<String> = emptyList(),
    )

    suspend fun search(query: String, page: Int = 1, type: SearchResultType? = null): List<SearchResult> =
        withContext(ioDispatcher) {
            val searchFilter = bandcampSearchFilter(type) ?: return@withContext emptyList()
            // The autocomplete_elastic endpoint returns a single batch (~50
            // results) and has no pagination; subsequent pages are empty.
            if (page > 1) return@withContext emptyList()
            val envelope = fetchSearchEnvelope(query, searchFilter)
            ensureActive()
            envelope.auto.results.mapNotNull { item -> toSearchResult(item) }
        }

    /** Bandcamp filter letter, or null when the requested type is not a Bandcamp result. */
    private fun bandcampSearchFilter(type: SearchResultType?): String? = when (type) {
        SearchResultType.ARTIST -> "b"
        SearchResultType.ALBUM -> "a"
        SearchResultType.TRACK -> "t"
        SearchResultType.LOCAL_TRACK,
        SearchResultType.YOUTUBE_TRACK,
        SearchResultType.YOUTUBE_ALBUM,
        SearchResultType.YOUTUBE_ARTIST,
        SearchResultType.YOUTUBE_PLAYLIST,
        SearchResultType.SOUNDCLOUD_TRACK,
        SearchResultType.SOUNDCLOUD_ARTIST,
        SearchResultType.SOUNDCLOUD_PLAYLIST,
        SearchResultType.SOUNDCLOUD_ALBUM,
        -> null
        null -> ""
    }

    private suspend fun fetchSearchEnvelope(query: String, searchFilter: String): SearchEnvelope {
        val bodyJson = json.encodeToString(
            SearchRequest.serializer(),
            SearchRequest(searchText = query, searchFilter = searchFilter),
        )
        val request = Request.Builder()
            .url(SEARCH_API_URL)
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .header("Content-Type", "application/json")
            .build()
        val call = client.newCall(request)
        coroutineContext[Job]?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
        val responseBody = call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.string()
        }
        return json.decodeFromString(SearchEnvelope.serializer(), responseBody)
    }

    private fun toSearchResult(item: SearchItem): SearchResult? {
        val searchResultType = when (item.type) {
            "b" -> SearchResultType.ARTIST
            "a" -> SearchResultType.ALBUM
            "t" -> SearchResultType.TRACK
            else -> return null
        }
        val name = item.name.trim().takeIf { it.isNotEmpty() } ?: return null
        val url = when (searchResultType) {
            SearchResultType.ARTIST -> item.itemUrlRoot
            else -> item.itemUrlPath
        }?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!NetworkUtils.isValidHttpsUrl(url)) return null

        val imageUrl = item.img?.trim()?.takeIf { it.startsWith("https://") }
        val (artist, album) = artistAndAlbumFor(searchResultType, item)
        val genre = item.tagNames
            .mapNotNull { it.trim().takeIf { tag -> tag.isNotEmpty() } }
            .joinToString(", ")
            .takeIf { it.isNotEmpty() }

        return SearchResult(
            type = searchResultType,
            name = name,
            url = url,
            imageUrl = imageUrl,
            artist = artist,
            album = album,
            genre = genre,
            releaseDate = null,
        )
    }

    private fun artistAndAlbumFor(
        type: SearchResultType,
        item: SearchItem,
    ): Pair<String?, String?> = when (type) {
        SearchResultType.ALBUM -> item.bandName?.trim()?.takeIf { it.isNotEmpty() } to null
        SearchResultType.TRACK -> {
            val artist = item.bandName?.trim()?.takeIf { it.isNotEmpty() }
            val album = item.albumName?.trim()?.takeIf { it.isNotEmpty() }
            artist to album
        }
        else -> null to null
    }

    private companion object {
        const val SEARCH_API_URL =
            "https://bandcamp.com/api/bcsearch_public_api/1/autocomplete_elastic"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
