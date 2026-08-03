package com.dustvalve.next.android.data.remote.youtube.innertube

import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses /browse?browseId=<channelId>&params=<videosTab> responses (WEB
 * client). The Videos tab uses richGridRenderer wrapping richItemRenderer;
 * each item is either a legacy videoRenderer or a modern lockupViewModel.
 */
@Singleton
class YouTubeChannelParser @Inject constructor() {

    data class ChannelPage(val tracks: List<Track>, val channelName: String?, val continuation: String?, val avatarUrl: String? = null)

    fun parse(root: JsonElement, channelId: String): ChannelPage {
        val channelName = extractChannelName(root)
        val avatarUrl = extractAvatarUrl(root)
        val gridContents = extractGridContents(root)

        val tracks = mutableListOf<Track>()
        var continuation: String? = null

        for (entry in gridContents.orEmpty()) {
            val ric = entry.path("richItemRenderer")
            if (ric != null) {
                parseRichItem(ric, channelId, channelName, tracks.size + 1)?.let { tracks += it }
                continue
            }
            entry.extractContinuationToken()?.let { continuation = it }
        }
        return ChannelPage(tracks, channelName, continuation, avatarUrl)
    }

    /** Continuation page parser - same shape as initial richGrid contents. */
    fun parseContinuation(root: JsonElement, channelId: String, channelName: String?, startIndex: Int): ChannelPage {
        val tracks = mutableListOf<Track>()
        var continuation: String? = null

        val actions = root.path("onResponseReceivedActions")?.arr().orEmpty()
        for (action in actions) {
            val items = action.path("appendContinuationItemsAction")
                ?.path("continuationItems")?.arr().orEmpty()
            for (entry in items) {
                val ric = entry.path("richItemRenderer")
                if (ric != null) {
                    parseRichItem(ric, channelId, channelName, startIndex + tracks.size)?.let { tracks += it }
                    continue
                }
                entry.extractContinuationToken()?.let { continuation = it }
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
     * Channel avatar from the page header (preferred) or microformat fallback.
     * Modern WEB responses use pageHeaderViewModel.image.sources; older ones
     * use c4TabbedHeaderRenderer.avatar.thumbnails.
     */
    private fun extractAvatarUrl(root: JsonElement): String? {
        val header = root.path("header")
        val pageHeaderAvatar = header
            ?.path("pageHeaderRenderer")
            ?.path("content")
            ?.path("pageHeaderViewModel")
            ?.path("image")
            ?.path("decoratedAvatarViewModel")
            ?.path("avatar")
            ?.path("avatarViewModel")
            ?.extractImageSources()
        if (pageHeaderAvatar != null) return pageHeaderAvatar

        val legacyAvatar = header
            ?.path("c4TabbedHeaderRenderer")
            ?.path("avatar")
            ?.extractThumbnail()
        if (legacyAvatar != null) return legacyAvatar

        val metadataAvatar = root.path("metadata")
            ?.path("channelMetadataRenderer")
            ?.path("avatar")
            ?.extractThumbnail()
        if (metadataAvatar != null) return metadataAvatar

        return root.path("microformat")
            ?.path("microformatDataRenderer")
            ?.path("thumbnail")
            ?.extractThumbnail()
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
        // `selected` is a JSON boolean, not a string - str() returns null for
        // booleans, which used to make this preference dead code.
        val selected = tabs.firstOrNull { it.path("tabRenderer")?.bool("selected") == true }
            ?: tabs.firstOrNull {
                it.path("tabRenderer")?.path("content")?.path("richGridRenderer") != null
            }
            ?: return null
        return selected.path("tabRenderer")?.path("content")?.path("richGridRenderer")
            ?.path("contents")?.arr()
    }

    private fun parseRichItem(ric: JsonElement, channelId: String, channelName: String?, trackNumber: Int): Track? {
        ric.path("content")?.path("videoRenderer")?.let {
            return parseVideo(it, channelId, channelName, trackNumber)
        }
        ric.path("content")?.path("lockupViewModel")?.let {
            return parseLockupItem(it, channelId, channelName, trackNumber)
        }
        return null
    }

    private fun parseVideo(vr: JsonElement, channelId: String, channelName: String?, trackNumber: Int): Track? {
        val videoId = vr.str("videoId") ?: return null
        val title = vr.runsText("title") ?: return null
        val artist = channelName ?: vr.runsText("ownerText") ?: ""
        val art = vr.extractThumbnail() ?: ""
        // YT WEB videoRenderer omits lengthSeconds; we have lengthText like "4:56".
        val lengthSec = vr.runsText("lengthText")
            ?.let { parseDurationText(it) }
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

    private fun parseLockupItem(lvm: JsonElement, channelId: String, channelName: String?, trackNumber: Int): Track? {
        val lockup = lvm.parseLockupVideo() ?: return null
        return Track(
            id = "yt_${lockup.videoId}",
            albumId = "yt_channel_$channelId",
            title = lockup.title,
            artist = channelName ?: lockup.artist,
            artistUrl = "https://www.youtube.com/channel/$channelId",
            trackNumber = trackNumber,
            duration = lockup.durationSec,
            streamUrl = "https://www.youtube.com/watch?v=${lockup.videoId}",
            artUrl = lockup.artUrl,
            albumTitle = "",
            source = TrackSource.YOUTUBE,
        )
    }
}
