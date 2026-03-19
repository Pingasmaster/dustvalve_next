package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.CollectionResult
import com.dustvalve.next.android.domain.model.PurchaseInfo
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
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DustvalveCollectionScraper @Inject constructor(
    private val client: OkHttpClient
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    private data class CollectionRequest(
        @SerialName("fan_id") val fanId: Long,
        @SerialName("older_than_token") val olderThanToken: String? = null,
        val count: Int = 20,
    )

    @Serializable
    private data class CollectionResponse(
        val items: List<CollectionItem> = emptyList(),
        @SerialName("more_available") val moreAvailable: Boolean = false,
        @SerialName("last_token") val lastToken: String? = null,
    )

    @Serializable
    private data class CollectionItem(
        @SerialName("item_type") val itemType: String = "",
        @SerialName("item_title") val itemTitle: String = "",
        @SerialName("item_url") val itemUrl: String = "",
        @SerialName("item_art_id") val itemArtId: Long = 0,
        @SerialName("band_name") val bandName: String = "",
        @SerialName("band_url") val bandUrl: String = "",
        @SerialName("sale_item_id") val saleItemId: Long? = null,
        @SerialName("sale_item_type") val saleItemType: String? = null,
    )

    suspend fun getCollection(
        fanId: Long,
        olderThanToken: String? = null,
    ): CollectionResult = withContext(Dispatchers.IO) {
        val token = olderThanToken ?: "9999999999::a::"

        val requestBody = json.encodeToString(
            CollectionRequest.serializer(),
            CollectionRequest(
                fanId = fanId,
                olderThanToken = token,
            )
        )

        val request = Request.Builder()
            .url("https://bandcamp.com/api/fancollection/1/collection_items")
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

        val collectionResponse = json.decodeFromString<CollectionResponse>(responseBody)

        val purchaseInfoMap = mutableMapOf<String, PurchaseInfo>()

        val albums = collectionResponse.items.mapNotNull { item ->
            if (item.itemType != "album" && item.itemType != "track") return@mapNotNull null
            if (item.itemUrl.isEmpty()) return@mapNotNull null
            if (!NetworkUtils.isValidHttpsUrl(item.itemUrl)) return@mapNotNull null

            val albumId = stableId(item.itemUrl)

            if (item.saleItemId != null && item.saleItemType != null) {
                purchaseInfoMap[albumId] = PurchaseInfo(
                    saleItemId = item.saleItemId,
                    saleItemType = item.saleItemType,
                )
            }

            val artUrl = if (item.itemArtId > 0) {
                "https://f4.bcbits.com/img/a${item.itemArtId}_10.jpg"
            } else {
                ""
            }

            val bandUrl = item.bandUrl.takeIf {
                it.isNotEmpty() && NetworkUtils.isValidHttpsUrl(it)
            } ?: ""

            Album(
                id = albumId,
                url = item.itemUrl,
                title = item.itemTitle,
                artist = item.bandName,
                artistUrl = bandUrl,
                artUrl = artUrl,
                releaseDate = null,
                about = null,
                tracks = emptyList(),
                tags = emptyList(),
                purchaseInfo = purchaseInfoMap[albumId],
            )
        }

        CollectionResult(
            albums = albums,
            hasMore = collectionResponse.moreAvailable,
            lastToken = collectionResponse.lastToken,
            purchaseInfo = purchaseInfoMap,
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
