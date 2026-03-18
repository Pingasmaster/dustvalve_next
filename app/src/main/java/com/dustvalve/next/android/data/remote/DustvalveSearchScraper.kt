package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DustvalveSearchScraper @Inject constructor(
    private val client: OkHttpClient
) {

    suspend fun search(
        query: String,
        page: Int = 1,
        type: SearchResultType? = null
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val safePage = page.coerceIn(1, 1000)

        val itemType = when (type) {
            SearchResultType.ARTIST -> "b"
            SearchResultType.ALBUM -> "a"
            SearchResultType.TRACK -> "t"
            SearchResultType.LOCAL_TRACK,
            SearchResultType.YOUTUBE_TRACK,
            SearchResultType.YOUTUBE_ALBUM,
            SearchResultType.YOUTUBE_ARTIST,
            SearchResultType.YOUTUBE_PLAYLIST -> return@withContext emptyList()
            null -> null
        }

        val searchUrl = NetworkUtils.buildSearchUrl(query, safePage, itemType)

        val request = Request.Builder()
            .url(searchUrl)
            .build()

        val call = client.newCall(request)
        coroutineContext[Job]?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
        val html = call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.string()
        }
        ensureActive()

        val document = Jsoup.parse(html, searchUrl)
        val results = mutableListOf<SearchResult>()

        document.select(".searchresult .result-info").forEach { element ->
            val resultType = element.selectFirst(".itemtype")?.text()?.trim()?.lowercase()
            val searchResultType = when {
                resultType?.contains("artist") == true || resultType?.contains("band") == true -> SearchResultType.ARTIST
                resultType?.contains("album") == true -> SearchResultType.ALBUM
                resultType?.contains("track") == true -> SearchResultType.TRACK
                else -> return@forEach
            }

            val name = element.selectFirst(".heading a")?.text()?.trim() ?: return@forEach
            val url = element.selectFirst(".heading a")?.attr("href")?.trim() ?: return@forEach

            // Skip results with non-Dustvalve URLs
            if (!NetworkUtils.isValidHttpsUrl(url)) return@forEach

            val artElement = element.parent()?.selectFirst(".art img")
            val imageUrl = artElement?.attr("src")
                ?.takeIf { it.isNotBlank() && (it.startsWith("https://") || it.startsWith("//")) }
                ?.let { if (it.startsWith("//")) "https:$it" else it }
                ?.let { NetworkUtils.upgradeBandcampImageUrl(it) }

            val subhead = element.selectFirst(".subhead")?.text()?.trim()
            val releaseDate = element.selectFirst(".released")?.text()
                ?.replace("released ", "")?.trim()
            val genre = element.selectFirst(".genre")?.text()
                ?.replace("genre: ", "")?.trim()

            val artist: String?
            val album: String?
            when (searchResultType) {
                SearchResultType.ALBUM -> {
                    artist = subhead?.removePrefix("by ")?.trim()
                    album = null
                }
                SearchResultType.TRACK -> {
                    // Parse "by Artist from Album" using lastIndexOf to handle artists
                    // with " from " in their name. Ambiguous if album title contains " from ".
                    val byStripped = subhead?.removePrefix("by ")
                    val fromIndex = byStripped?.lastIndexOf(" from ")
                    if (fromIndex != null && fromIndex >= 0) {
                        artist = byStripped.substring(0, fromIndex).trim()
                        album = byStripped.substring(fromIndex + 6).trim()
                    } else {
                        artist = subhead?.removePrefix("by ")?.trim()
                        album = null
                    }
                }
                SearchResultType.ARTIST, SearchResultType.LOCAL_TRACK,
                SearchResultType.YOUTUBE_TRACK, SearchResultType.YOUTUBE_ALBUM,
                SearchResultType.YOUTUBE_ARTIST, SearchResultType.YOUTUBE_PLAYLIST -> {
                    artist = null
                    album = null
                }
            }

            results.add(
                SearchResult(
                    type = searchResultType,
                    name = name,
                    url = url,
                    imageUrl = imageUrl,
                    artist = artist,
                    album = album,
                    genre = genre,
                    releaseDate = releaseDate,
                )
            )
        }

        results
    }
}
