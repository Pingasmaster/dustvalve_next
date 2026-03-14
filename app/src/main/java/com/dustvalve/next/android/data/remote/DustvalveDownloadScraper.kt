package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.PurchaseInfo
import com.dustvalve.next.android.util.HtmlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DustvalveDownloadScraper @Inject constructor(
    private val client: OkHttpClient,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    data class BlobData(
        @SerialName("digital_items") val digitalItems: List<DigitalItem> = emptyList(),
    )

    @Serializable
    data class DigitalItem(
        val downloads: Map<String, DownloadInfo> = emptyMap(),
        val title: String = "",
        @SerialName("item_type") val itemType: String = "",
    )

    @Serializable
    data class DownloadInfo(
        val url: String? = null,
        @SerialName("size_mb") val sizeMb: Float = 0f,
        val description: String = "",
    )

    /**
     * Fetches the download page for a purchased item and returns a map of
     * [AudioFormat] to download URL.
     */
    suspend fun getDownloadUrls(
        purchaseInfo: PurchaseInfo,
    ): Map<AudioFormat, String> = withContext(Dispatchers.IO) {
        val downloadPageUrl = "https://bandcamp.com/download?" +
            "from=collection&id=${purchaseInfo.saleItemId}&type=${purchaseInfo.saleItemType}"

        val request = Request.Builder()
            .url(downloadPageUrl)
            .build()

        val call = client.newCall(request)
        coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }

        val html = call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.string()
        }
        ensureActive()

        val blobJson = HtmlUtils.extractDataAttribute(html, "data-blob")
            ?: HtmlUtils.extractJsonFromScript(html, "TralbumData")
            ?: HtmlUtils.extractJsonFromScript(html, "DownloadData")
            ?: throw IOException("Could not find download data on page")

        val blobData = json.decodeFromString<BlobData>(blobJson)

        val item = blobData.digitalItems.firstOrNull()
            ?: throw IOException("No digital items found in download data")

        val result = mutableMapOf<AudioFormat, String>()
        for ((formatKey, downloadInfo) in item.downloads) {
            val format = AudioFormat.fromKey(formatKey) ?: continue
            val url = downloadInfo.url
            if (!url.isNullOrBlank()) {
                result[format] = url
            }
        }

        result
    }
}
