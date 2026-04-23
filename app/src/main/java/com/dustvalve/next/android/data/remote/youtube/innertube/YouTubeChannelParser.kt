package com.dustvalve.next.android.data.remote.youtube.innertube

import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses /browse?browseId=<channelId>&params=<videosTab> responses (WEB
 * client). The Videos tab uses richGridRenderer wrapping richItemRenderer,
 * each containing a videoRenderer.
 */
@Singleton
class YouTubeChannelParser @Inject constructor() {

    data class ChannelPage(
        val tracks: List<Track>,
        val channelName: String?,
        val continuation: String?,
    )

    fun parse(root: JsonElement, channelId: String): ChannelPage {
        val channelName = extractChannelName(root)
        val (gridContents, fromContinuation) = extractGridContents(root) to false

        val tracks = mutableListOf<Track>()
        var continuation: String? = null

        for (entry in gridContents.orEmpty()) {
            val ric = entry.path("richItemRenderer")
            if (ric != null) {
                val vr = ric.path("content")?.path("videoRenderer")
                if (vr != null) {
                    parseVideo(vr, channelId, channelName, tracks.size + 1)?.let { tracks += it }
                }
                continue
            }
            entry.path("continuationItemRenderer")
                ?.path("continuationEndpoint")?.path("continuationCommand")
                ?.str("token")
                ?.let { continuation = it }
        }
        return ChannelPage(tracks, channelName, continuation)
    }

    /** Continuation page parser - same shape as initial richGrid contents. */
    fun parseContinuation(
        root: JsonElement,
        channelId: String,
        channelName: String?,
        startIndex: Int,
    ): ChannelPage {
        val tracks = mutableListOf<Track>()
        var continuation: String? = null

        val actions = root.path("onResponseReceivedActions")?.arr().orEmpty()
        for (action in actions) {
            val items = action.path("appendContinuationItemsAction")
                ?.path("continuationItems")?.arr().orEmpty()
            for (entry in items) {
                val ric = entry.path("richItemRenderer")
                if (ric != null) {
                    val vr = ric.path("content")?.path("videoRenderer")
                    if (vr != null) {
                        parseVideo(vr, channelId, channelName, startIndex + tracks.size)?.let { tracks += it }
                    }
                    continue
                }
                entry.path("continuationItemRenderer")
                    ?.path("continuationEndpoint")?.path("continuationCommand")
                    ?.str("token")
                    ?.let { continuation = it }
            }
        }
        return ChannelPage(tracks, channelName, continuation)
    }

    private fun extractChannelName(root: JsonElement): String? {
        val header = root.path("header") ?: return null
        return header.path("c4TabbedHeaderRenderer")?.str("title")
            ?: header.path("pageHeaderRenderer")?.str("pageTitle")
    }

    /**
     * Walks tabs to find the selected one, then returns its richGridRenderer
     * contents. We look at the selected tab to honor whatever params were
     * passed; if none were passed, the channel's default tab wins.
     */
    private fun extractGridContents(root: JsonElement): List<JsonElement>? {
        val tabs = root.path("contents")?.path("twoColumnBrowseResultsRenderer")
            ?.path("tabs")?.arr()
            ?: root.path("contents")?.path("singleColumnBrowseResultsRenderer")
                ?.path("tabs")?.arr()
            ?: return null

        // Prefer selected tab; fall back to first tab with a richGridRenderer.
        val selected = tabs.firstOrNull { it.path("tabRenderer")?.str("selected") == "true" }
            ?: tabs.firstOrNull {
                it.path("tabRenderer")?.path("content")?.path("richGridRenderer") != null
            }
            ?: return null
        return selected.path("tabRenderer")?.path("content")?.path("richGridRenderer")
            ?.path("contents")?.arr()
    }

    private fun parseVideo(
        vr: JsonElement,
        channelId: String,
        channelName: String?,
        trackNumber: Int,
    ): Track? {
        val videoId = vr.str("videoId") ?: return null
        val title = vr.runsText("title") ?: return null
        val artist = channelName ?: vr.runsText("ownerText") ?: ""
        val art = vr.extractThumbnail() ?: ""
        // YT WEB videoRenderer omits lengthSeconds; we have lengthText like "4:56".
        val lengthSec = vr.runsText("lengthText")
            ?.let { parseLengthText(it) }
            ?: 0f
        return Track(
            id = "yt_$videoId",
            albumId = "yt_channel_$channelId",
            title = title,
            artist = artist,
            artistUrl = "https://www.youtube.com/channel/$channelId",
            trackNumber = trackNumber,
            duration = lengthSec,
            streamUrl = "https://www.youtube.com/watch?v=$videoId",
            artUrl = art,
            albumTitle = "",
            source = TrackSource.YOUTUBE,
        )
    }

    /** Parses "h:mm:ss" / "m:ss" / "ss" length text into total seconds. */
    private fun parseLengthText(text: String): Float {
        val parts = text.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return 0f
        var total = 0
        for (p in parts) total = total * 60 + p
        return total.toFloat()
    }
}
