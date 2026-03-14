package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.util.HtmlUtils
import com.dustvalve.next.android.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DustvalveStreamResolver @Inject constructor(
    private val client: OkHttpClient,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun resolveStreamUrl(track: Track, albumPageUrl: String? = null): String? =
        withContext(Dispatchers.IO) {
            // If the track already has a stream URL, return it directly
            if (!track.streamUrl.isNullOrBlank()) {
                return@withContext track.streamUrl
            }

            // Need to fetch the album page and re-parse TralbumData
            val pageUrl = albumPageUrl ?: return@withContext null
            require(NetworkUtils.isValidHttpsUrl(pageUrl)) { "Invalid URL: $pageUrl" }

            // OkHttp's CookieJar handles cookies automatically — no manual cookie header needed
            val request = Request.Builder()
                .url(pageUrl)
                .build()

            val call = client.newCall(request)
            coroutineContext[Job]?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
            val html = call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.body.string()
            }
            ensureActive()

            val tralbumJson = HtmlUtils.extractJsonFromScript(html, "TralbumData")
                ?: HtmlUtils.extractDataAttribute(html, "data-tralbum")
                ?: return@withContext null

            val tralbumData = try {
                json.decodeFromString<DustvalveAlbumScraper.TralbumData>(tralbumJson)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext null
            }

            // Find the matching track: prefer title match, fall back to track number
            val matchingTrack = tralbumData.trackinfo.find { it.title.equals(track.title, ignoreCase = true) }
                ?: tralbumData.trackinfo.find { it.trackNum == track.trackNumber }

            matchingTrack?.file?.mp3128
        }
}
