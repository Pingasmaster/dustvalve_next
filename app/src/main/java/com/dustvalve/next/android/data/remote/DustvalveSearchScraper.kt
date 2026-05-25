package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
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

@Singleton
class DustvalveSearchScraper @Inject constructor(private val client: OkHttpClient) {

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

    suspend fun search(query: String, page: Int = 1, type: SearchResultType? = null): List<SearchResult> = withContext(Dispatchers.IO) {
        val searchFilter = when (type) {
            SearchResultType.ARTIST -> "b"

            SearchResultType.ALBUM -> "a"

            SearchResultType.TRACK -> "t"

            SearchResultType.LOCAL_TRACK,
            SearchResultType.YOUTUBE_TRACK,
            SearchResultType.YOUTUBE_ALBUM,
            SearchResultType.YOUTUBE_ARTIST,
            SearchResultType.YOUTUBE_PLAYLIST,
            -> return@withContext emptyList()

            null -> ""
        }

        // The autocomplete_elastic endpoint returns a single batch (~50
        // results) and has no pagination; subsequent pages are empty.
        if (page > 1) return@withContext emptyList()

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
        ensureActive()

        val envelope = json.decodeFromString(SearchEnvelope.serializer(), responseBody)

        envelope.auto.results.mapNotNull { item ->
            val searchResultType = when (item.type) {
                "b" -> SearchResultType.ARTIST
                "a" -> SearchResultType.ALBUM
                "t" -> SearchResultType.TRACK
                else -> return@mapNotNull null
            }

            val name = item.name.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null

            val url = when (searchResultType) {
                SearchResultType.ARTIST -> item.itemUrlRoot
                else -> item.itemUrlPath
            }?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null

            if (!NetworkUtils.isValidHttpsUrl(url)) return@mapNotNull null

            val imageUrl = item.img?.trim()?.takeIf { it.startsWith("https://") }

            val artist: String?
            val album: String?
            when (searchResultType) {
                SearchResultType.ALBUM -> {
                    artist = item.bandName?.trim()?.takeIf { it.isNotEmpty() }
                    album = null
                }

                SearchResultType.TRACK -> {
                    artist = item.bandName?.trim()?.takeIf { it.isNotEmpty() }
                    album = item.albumName?.trim()?.takeIf { it.isNotEmpty() }
                }

                else -> {
                    artist = null
                    album = null
                }
            }

            val genre = item.tagNames
                .mapNotNull { it.trim().takeIf { tag -> tag.isNotEmpty() } }
                .joinToString(", ")
                .takeIf { it.isNotEmpty() }

            SearchResult(
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
    }

    private companion object {
        const val SEARCH_API_URL =
            "https://bandcamp.com/api/bcsearch_public_api/1/autocomplete_elastic"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
