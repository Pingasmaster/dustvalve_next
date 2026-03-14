package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.DiscoverResult
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
import com.dustvalve.next.android.util.NetworkUtils
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DustvalveDiscoverScraper @Inject constructor(
    private val client: OkHttpClient
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Serializable
    private data class DiscoverRequest(
        @SerialName("tag_norm_names") val tagNormNames: List<String>,
        val cursor: String = "*",
        @SerialName("category_id") val categoryId: Int = 1,
        val size: Int = 60,
        val slice: String = "top",
        @SerialName("include_result_types") val includeResultTypes: List<String> = listOf("a", "s"),
        @SerialName("geoname_id") val geonameId: Int = 0,
        @SerialName("time_facet_id") val timeFacetId: Int? = null,
    )

    @Serializable
    private data class DiscoverResponse(
        val results: List<DiscoverItem> = emptyList(),
        @SerialName("result_count") val resultCount: Int = 0,
        val cursor: String? = null,
    )

    @Serializable
    private data class PrimaryImage(
        @SerialName("image_id") val imageId: Long = 0,
    )

    @Serializable
    private data class DiscoverItem(
        @SerialName("result_type") val resultType: String = "",
        val title: String = "",
        @SerialName("album_artist") val albumArtist: String? = null,
        @SerialName("band_name") val bandName: String = "",
        @SerialName("primary_image") val primaryImage: PrimaryImage? = null,
        @SerialName("item_image_id") val itemImageId: Long = 0,
        @SerialName("item_url") val itemUrl: String = "",
        @SerialName("band_url") val bandUrl: String = "",
        val id: Long = 0,
        @SerialName("band_location") val bandLocation: String? = null,
        @SerialName("band_genre_id") val bandGenreId: Int? = null,
    )

    suspend fun discover(genre: String? = null, cursor: String? = null): DiscoverResult =
        withContext(Dispatchers.IO) {
            val tagNames = if (genre != null) listOf(genre) else emptyList()

            val requestBody = json.encodeToString(
                DiscoverRequest.serializer(),
                DiscoverRequest(
                    tagNormNames = tagNames,
                    cursor = cursor ?: "*",
                )
            )

            val request = Request.Builder()
                .url("https://bandcamp.com/api/discover/1/discover_web")
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val call = client.newCall(request)
            coroutineContext[Job]?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
            val responseBody = call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.body.string()
            }
            ensureActive()

            if (responseBody.contains("\"__api_special__\"")) {
                throw IOException("Dustvalve API error: $responseBody")
            }
            val discoverResponse = json.decodeFromString<DiscoverResponse>(responseBody)

            val albums = discoverResponse.results
                .filter { it.resultType == "a" }
                .mapNotNull { item ->
                    val imageId = item.primaryImage?.imageId ?: item.itemImageId
                    val artUrl = if (imageId > 0) {
                        NetworkUtils.buildArtUrl(imageId)
                    } else {
                        ""
                    }

                    val albumUrl = item.itemUrl
                        .takeIf { it.isNotBlank() }
                        ?.let { normalizeUrl(it) }
                        ?: return@mapNotNull null
                    val bandUrl = item.bandUrl
                        .takeIf { it.isNotBlank() }
                        ?.let { normalizeUrl(it) }

                    Album(
                        id = stableId(albumUrl),
                        url = albumUrl,
                        title = item.title,
                        artist = item.albumArtist ?: item.bandName,
                        artistUrl = bandUrl?.takeIf { NetworkUtils.isValidHttpsUrl(it) } ?: "",
                        artUrl = artUrl,
                        releaseDate = null,
                        about = null,
                        tracks = emptyList(),
                        tags = emptyList(),
                    )
                }

            DiscoverResult(
                albums = albums,
                cursor = discoverResponse.cursor,
            )
        }

    private fun normalizeUrl(url: String): String {
        return url.trimEnd('/').substringBefore('?').substringBefore('#')
    }

    private fun stableId(input: String): String {
        val normalized = normalizeUrl(input)
        val bytes = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return bytes.take(16).joinToString("") { "%02x".format(it) }
    }
}
