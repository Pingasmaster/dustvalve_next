package com.dustvalve.next.android.data.remote.youtube.innertube

import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses /next responses (MWEB client) for related videos. The watch-next
 * payload puts related items inside singleColumnWatchNextResults at
 *   results.results.contents[*].itemSectionRenderer.contents[*].videoWithContextRenderer
 * We map each one to a YOUTUBE_TRACK SearchResult.
 *
 * Also accepts the desktop twoColumnWatchNextResults shape with
 * compactVideoRenderer items for forward-compat, even though our current
 * MWEB calls don't return that shape.
 */
@Singleton
class YouTubeNextParser @Inject constructor() {

    fun parse(root: JsonElement): List<SearchResult> {
        val out = mutableListOf<SearchResult>()
        val contents = root.path("contents") ?: return emptyList()

        // MWEB single-column shape
        val singleResults = contents.path("singleColumnWatchNextResults")
            ?.path("results")?.path("results")?.path("contents")?.arr()
        if (singleResults != null) {
            for (section in singleResults) {
                val isr = section.path("itemSectionRenderer") ?: continue
                for (item in isr.path("contents")?.arr().orEmpty()) {
                    item.path("videoWithContextRenderer")?.let { parseVwcr(it)?.let(out::add) }
                    item.path("compactVideoRenderer")?.let { parseCvr(it)?.let(out::add) }
                }
            }
        }

        // Desktop two-column shape
        val secondary = contents.path("twoColumnWatchNextResults")
            ?.path("secondaryResults")?.path("secondaryResults")
            ?.path("results")?.arr()
        if (secondary != null) {
            for (item in secondary) {
                item.path("compactVideoRenderer")?.let { parseCvr(it)?.let(out::add) }
            }
        }
        return out
    }

    private fun parseVwcr(vwcr: JsonElement): SearchResult? {
        val videoId = vwcr.str("videoId") ?: return null
        val title = vwcr.runsText("headline") ?: return null
        val artist = vwcr.runsText("shortBylineText") ?: vwcr.runsText("longBylineText")
        val thumbnail = vwcr.path("thumbnail")?.extractThumbnail()
        return SearchResult(
            type = SearchResultType.YOUTUBE_TRACK,
            name = title,
            url = "https://www.youtube.com/watch?v=$videoId",
            imageUrl = thumbnail,
            artist = artist,
            album = null,
            genre = null,
            releaseDate = null,
        )
    }

    private fun parseCvr(cvr: JsonElement): SearchResult? {
        val videoId = cvr.str("videoId") ?: return null
        val title = cvr.runsText("title") ?: return null
        val artist = cvr.runsText("shortBylineText") ?: cvr.runsText("longBylineText")
        val thumbnail = cvr.path("thumbnail")?.extractThumbnail()
        return SearchResult(
            type = SearchResultType.YOUTUBE_TRACK,
            name = title,
            url = "https://www.youtube.com/watch?v=$videoId",
            imageUrl = thumbnail,
            artist = artist,
            album = null,
            genre = null,
            releaseDate = null,
        )
    }
}
