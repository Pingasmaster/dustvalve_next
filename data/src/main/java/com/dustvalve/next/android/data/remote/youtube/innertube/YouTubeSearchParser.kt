package com.dustvalve.next.android.data.remote.youtube.innertube

import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses standard YouTube /search responses (WEB client). Walks the
 * primary section list and emits domain SearchResults for the renderer
 * shapes we recognize: videoRenderer, channelRenderer, playlistRenderer,
 * lockupViewModel (playlist + podcast lockups), and contents nested under
 * officialCardViewModel (official artist / playlist promo cards).
 *
 * Anything else (shelfRenderer, gridShelfViewModel, ads, etc.) is skipped
 * silently so the result list stays clean.
 *
 * Returns the response's continuation token alongside results so callers
 * can paginate via [YouTubeInnertubeClient.searchContinuation].
 */
@Singleton
class YouTubeSearchParser @Inject constructor() {

    data class Page(val items: List<SearchResult>, val continuation: String?)

    fun parse(root: JsonElement): Page {
        val sectionContents = resolvePrimarySectionList(root) ?: return Page(emptyList(), null)
        val items = mutableListOf<SearchResult>()
        var continuation: String? = null

        for (section in sectionContents) {
            // Continuation lives at the section list level, not inside
            // itemSectionRenderer. Look for it on every iteration.
            sectionContinuationToken(section)?.let { continuation = it }
            items += itemSectionRows(section).flatMap { collectRow(it) }
        }
        return Page(items, continuation)
    }

    /**
     * Continuation pages land under onResponseReceivedCommands as
     * appendContinuationItemsAction.continuationItems: typically an
     * itemSectionRenderer of rows plus a trailing continuationItemRenderer.
     */
    fun parseContinuation(root: JsonElement): Page {
        val continuationItems = resolveContinuationItems(root) ?: return Page(emptyList(), null)
        val items = mutableListOf<SearchResult>()
        var continuation: String? = null
        for (entry in continuationItems) {
            sectionContinuationToken(entry)?.let { continuation = it }
            val sectionRows = itemSectionRows(entry)
            if (sectionRows.isNotEmpty()) {
                items += sectionRows.flatMap { collectRow(it) }
            } else {
                // Some continuations flatten rows directly into continuationItems.
                collectRow(entry).let { items += it }
            }
        }
        return Page(items, continuation)
    }

    private fun sectionContinuationToken(section: JsonElement): String? = section.path("continuationItemRenderer")
        ?.path("continuationEndpoint")?.path("continuationCommand")
        ?.str("token")

    private fun itemSectionRows(section: JsonElement): List<JsonElement> =
        section.path("itemSectionRenderer")?.path("contents")?.arr().orEmpty()

    private fun resolvePrimarySectionList(root: JsonElement): List<JsonElement>? {
        val contents = root.path("contents") ?: return null
        // WEB (twoColumnSearchResultsRenderer) is the canonical shape for
        // our search calls. We also accept the legacy single-column shape
        // in case Innertube ever switches answer modes.
        val sl = contents
            .path("twoColumnSearchResultsRenderer")
            ?.path("primaryContents")?.path("sectionListRenderer")
            ?: contents.path("sectionListRenderer")
            ?: return null
        return sl.path("contents")?.arr()
    }

    private fun resolveContinuationItems(root: JsonElement): List<JsonElement>? {
        val commands = root.path("onResponseReceivedCommands")?.arr() ?: return null
        for (command in commands) {
            val items = command.path("appendContinuationItemsAction")
                ?.path("continuationItems")?.arr()
                ?: command.path("reloadContinuationItemsCommand")
                    ?.path("continuationItems")?.arr()
            if (items != null) return items
        }
        return null
    }

    /**
     * officialCardViewModel wraps channel / playlist lockups for "Official
     * artist" style cards. Flatten its contents through [parseRow]; anything
     * else is a normal single-renderer row.
     */
    private fun collectRow(row: JsonElement): List<SearchResult> {
        val card = row.path("officialCardViewModel") ?: return listOfNotNull(parseRow(row))
        val nested = card.path("contents")?.arr()
            ?: card.path("cards")?.arr()
            ?: emptyList()
        if (nested.isNotEmpty()) {
            return nested.mapNotNull { parseRow(it) }
        }
        // primaryContent / other nested lockups: walk the card tree.
        return listOfNotNull(parseOfficialCard(card))
    }

    private fun parseRow(row: JsonElement): SearchResult? {
        row.path("officialCardViewModel")?.let { card ->
            parseOfficialCard(card)?.let { return it }
        }
        val parsers = listOf(
            "videoRenderer" to ::parseVideo,
            "playlistRenderer" to ::parsePlaylist,
            "channelRenderer" to ::parseChannel,
            "lockupViewModel" to ::parseLockup,
        )
        return parsers.firstNotNullOfOrNull { (key, parser) ->
            row.path(key)?.let(parser)
        }
    }

    /**
     * WEB search sometimes wraps the official artist / playlist promo in
     * `officialCardViewModel`. Walk its nested contents for the same
     * channelRenderer / playlist lockup shapes we already understand.
     */
    private fun parseOfficialCard(card: JsonElement): SearchResult? {
        // Prefer an explicit contents array when present, then fall back to
        // walking every nested object for known renderer keys.
        val contents = card.path("contents")?.arr()
        if (contents != null) {
            for (child in contents) {
                parseRow(child)?.let { return it }
            }
        }
        return walkOfficialCard(card)
    }

    private fun walkOfficialCard(el: JsonElement?): SearchResult? {
        when (el) {
            is kotlinx.serialization.json.JsonObject -> {
                // Avoid re-entering the same officialCardViewModel key.
                for ((key, value) in el) {
                    when (key) {
                        "channelRenderer" -> parseChannel(value)?.let { return it }
                        "playlistRenderer" -> parsePlaylist(value)?.let { return it }
                        "lockupViewModel" -> parseLockup(value)?.let { return it }
                        "videoRenderer" -> parseVideo(value)?.let { return it }
                        "officialCardViewModel" -> Unit // already entered
                        else -> walkOfficialCard(value)?.let { return it }
                    }
                }
            }

            is kotlinx.serialization.json.JsonArray -> {
                for (child in el) walkOfficialCard(child)?.let { return it }
            }

            else -> Unit
        }
        return null
    }

    private fun parseVideo(vr: JsonElement): SearchResult? {
        val videoId = vr.str("videoId") ?: return null
        val title = vr.runsText("title") ?: return null
        val artist = vr.runsText("ownerText") ?: vr.runsText("longBylineText")
        val thumbnail = vr.extractThumbnail()
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

    private fun parsePlaylist(pr: JsonElement): SearchResult? {
        val playlistId = pr.str("playlistId") ?: return null
        val title = pr.runsText("title") ?: return null
        val owner = pr.runsText("shortBylineText") ?: pr.runsText("longBylineText")
        // playlistRenderer thumbnails live under thumbnailRenderer
        val thumbnail = pr.path("thumbnailRenderer")
            ?.path("playlistVideoThumbnailRenderer")
            ?.path("thumbnail")?.extractThumbnail()
            ?: pr.extractThumbnail()
        return SearchResult(
            type = SearchResultType.YOUTUBE_PLAYLIST,
            name = title,
            url = "https://www.youtube.com/playlist?list=$playlistId",
            imageUrl = thumbnail,
            artist = owner,
            album = null,
            genre = null,
            releaseDate = null,
        )
    }

    private fun parseChannel(cr: JsonElement): SearchResult? {
        val channelId = cr.str("channelId") ?: return null
        val title = cr.runsText("title") ?: return null
        val thumbnail = cr.path("thumbnail")?.extractThumbnail()
        return SearchResult(
            type = SearchResultType.YOUTUBE_ARTIST,
            name = title,
            url = "https://www.youtube.com/channel/$channelId",
            imageUrl = thumbnail,
            artist = null,
            album = null,
            genre = null,
            releaseDate = null,
        )
    }

    /**
     * Newer "view model" rendering used for playlist (and podcast) search
     * results. PLAYLIST and PODCAST lockups share the same contentId +
     * metadata shape and are both openable as playlist URLs (NewPipe maps
     * both through YoutubeMixOrPlaylistLockupInfoItemExtractor). VIDEO
     * lockups are ignored here - videos still arrive as videoRenderer for
     * WEB search. Other content types are skipped with an explicit gate so
     * the playlists filter does not silently drop unknown lockups without
     * a documented reason.
     */
    private fun parseLockup(lvm: JsonElement): SearchResult? {
        val type = lvm.str("contentType") ?: return null
        when (type) {
            "LOCKUP_CONTENT_TYPE_PLAYLIST",
            "LOCKUP_CONTENT_TYPE_PODCAST",
            -> Unit
            // VIDEO lockups exist on some surfaces; WEB search still emits
            // videoRenderer for tracks, so skip rather than double-count.
            "LOCKUP_CONTENT_TYPE_VIDEO" -> return null
            else -> return null
        }
        val playlistId = lvm.str("contentId")
            ?: lvm.path("rendererContext")?.path("commandContext")
                ?.path("onTap")?.path("innertubeCommand")
                ?.path("watchEndpoint")?.str("playlistId")
            ?: return null
        val meta = lvm.path("metadata")?.path("lockupMetadataViewModel") ?: return null
        val title = meta.path("title")?.str("content") ?: return null
        val owner = meta.path("metadata")?.path("contentMetadataViewModel")
            ?.path("metadataRows")?.arr()?.firstOrNull()
            ?.path("metadataParts")?.arr()?.firstOrNull()
            ?.path("text")?.str("content")
        val thumbnail = lvm.path("contentImage")
            ?.path("collectionThumbnailViewModel")
            ?.path("primaryThumbnail")?.path("thumbnailViewModel")
            ?.path("image")?.path("sources")?.arr()
            ?.maxByOrNull { it.int("width") ?: 0 }
            ?.str("url")
            ?.let { bumpYtThumbnailResolution(it) }
        return SearchResult(
            type = SearchResultType.YOUTUBE_PLAYLIST,
            name = title,
            url = "https://www.youtube.com/playlist?list=$playlistId",
            imageUrl = thumbnail,
            artist = owner,
            album = null,
            genre = null,
            releaseDate = null,
        )
    }
}
