package com.dustvalve.next.android.data.remote.youtube.innertube

import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses /browse?browseId=VL<playlistId> responses (MWEB client). Walks
 * the playlistVideoListRenderer for items and surfaces the playlist title
 * from the page header. The continuation token is returned as the
 * [PlaylistPage.continuation] field.
 */
@Singleton
class YouTubePlaylistParser @Inject constructor() {

    data class PlaylistPage(
        val tracks: List<Track>,
        val title: String?,
        val continuation: String?,
    )

    fun parse(root: JsonElement, playlistId: String): PlaylistPage {
        val title = extractTitle(root)
        val sectionContents = extractSectionList(root) ?: return PlaylistPage(emptyList(), title, null)

        val tracks = mutableListOf<Track>()
        var continuation: String? = null

        for (section in sectionContents) {
            val isr = section.path("itemSectionRenderer") ?: continue
            for (item in isr.path("contents")?.arr().orEmpty()) {
                val pvlr = item.path("playlistVideoListRenderer") ?: continue
                for (entry in pvlr.path("contents")?.arr().orEmpty()) {
                    val pvr = entry.path("playlistVideoRenderer")
                    if (pvr != null) {
                        parseItem(pvr, playlistId, tracks.size + 1)?.let { tracks += it }
                        continue
                    }
                    // Continuation row at the entry level
                    entry.path("continuationItemRenderer")
                        ?.path("continuationEndpoint")?.path("continuationCommand")
                        ?.str("token")
                        ?.let { continuation = it }
                }
            }
        }
        return PlaylistPage(tracks, title, continuation)
    }

    /** Continuation page parser (no header, just more items). */
    fun parseContinuation(root: JsonElement, playlistId: String, startIndex: Int): PlaylistPage {
        val tracks = mutableListOf<Track>()
        var continuation: String? = null

        // Continuation responses live under onResponseReceivedActions[*].appendContinuationItemsAction.continuationItems
        val actions = root.path("onResponseReceivedActions")?.arr().orEmpty()
        for (action in actions) {
            val appended = action.path("appendContinuationItemsAction")
                ?.path("continuationItems")?.arr().orEmpty()
            for (entry in appended) {
                val pvr = entry.path("playlistVideoRenderer")
                if (pvr != null) {
                    parseItem(pvr, playlistId, startIndex + tracks.size)?.let { tracks += it }
                    continue
                }
                entry.path("continuationItemRenderer")
                    ?.path("continuationEndpoint")?.path("continuationCommand")
                    ?.str("token")
                    ?.let { continuation = it }
            }
        }
        return PlaylistPage(tracks, null, continuation)
    }

    private fun extractTitle(root: JsonElement): String? {
        val header = root.path("header") ?: return null
        return header.path("playlistHeaderRenderer")?.runsText("title")
            ?: header.path("pageHeaderRenderer")?.str("pageTitle")
            ?: header.path("musicDetailHeaderRenderer")?.runsText("title")
    }

    private fun extractSectionList(root: JsonElement): List<JsonElement>? {
        val contents = root.path("contents") ?: return null
        val tabs = contents.path("singleColumnBrowseResultsRenderer")?.path("tabs")?.arr()
            ?: contents.path("twoColumnBrowseResultsRenderer")?.path("tabs")?.arr()
            ?: return null
        for (tab in tabs) {
            val sl = tab.path("tabRenderer")?.path("content")?.path("sectionListRenderer")
                ?: continue
            return sl.path("contents")?.arr()
        }
        return null
    }

    private fun parseItem(pvr: JsonElement, playlistId: String, trackNumber: Int): Track? {
        val videoId = pvr.str("videoId") ?: return null
        val title = pvr.runsText("title") ?: return null
        val artist = pvr.runsText("shortBylineText") ?: ""
        val durationSec = pvr.str("lengthSeconds")?.toFloatOrNull() ?: 0f
        val art = pvr.path("thumbnail")?.extractThumbnail() ?: ""
        return Track(
            id = "yt_$videoId",
            albumId = "yt_playlist_$playlistId",
            title = title,
            artist = artist,
            artistUrl = "",
            trackNumber = trackNumber,
            duration = durationSec,
            streamUrl = "https://www.youtube.com/watch?v=$videoId",
            artUrl = art,
            albumTitle = "",
            source = TrackSource.YOUTUBE,
        )
    }
}
