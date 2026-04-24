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
 *
 * Also parses /next responses for YouTube Mixes (auto-generated radio
 * playlists whose IDs start with `RD`). Mixes live under the watch-next
 * playlistPanelRenderer and paginate by re-POSTing /next with the last
 * track's videoId + playlistIndex + params.
 */
@Singleton
class YouTubePlaylistParser @Inject constructor() {

    data class PlaylistPage(
        val tracks: List<Track>,
        val title: String?,
        val continuation: String?,
    )

    /** Next-page cursor for a Mix (nullable fields mean "not available"). */
    data class MixContinuation(
        val lastVideoId: String,
        val playlistIndex: Int,
        val params: String?,
    )

    data class MixPage(
        val tracks: List<Track>,
        val title: String?,
        val continuation: MixContinuation?,
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

    /**
     * Parses /next responses for Mix playlists. Tracks live under
     *   contents.singleColumnWatchNextResults.playlist.playlist.contents[*].playlistPanelVideoRenderer
     * (MWEB single-column shape) or the twoColumnWatchNextResults equivalent.
     *
     * [startIndex] is the 1-based trackNumber for the first parsed row; pass
     * `previouslyLoaded + 1` when paginating. [skipBeforeIndex] is the number
     * of leading items to skip from the response (YT re-returns the last ~24
     * tracks on each paginated /next — we rely on the response's currentIndex
     * to slice, and also dedupe via [seenVideoIds] as a belt-and-braces guard).
     */
    fun parseMix(
        root: JsonElement,
        playlistId: String,
        startIndex: Int = 1,
        seenVideoIds: Set<String> = emptySet(),
    ): MixPage {
        val playlistPanel = findPlaylistPanel(root)
            ?: return MixPage(emptyList(), null, null)

        val panelTitle = playlistPanel.str("title")
            ?: playlistPanel.runsText("title")
            ?: playlistPanel.runsText("titleText")
        val currentIndex = playlistPanel.int("currentIndex") ?: 0
        val rawContents = playlistPanel.path("contents")?.arr().orEmpty()

        // YT re-returns already-loaded items on subsequent /next pages; the
        // server tells us where "new" starts via currentIndex. On the first
        // page currentIndex is 0 (no skip needed).
        val toConsume = if (currentIndex > 0 && currentIndex < rawContents.size) {
            rawContents.drop(currentIndex + 1)
        } else {
            rawContents
        }

        val tracks = mutableListOf<Track>()
        var lastVideoId: String? = null
        var lastIndex = currentIndex
        var lastParams: String? = null

        for (entry in toConsume) {
            val pvr = entry.path("playlistPanelVideoRenderer") ?: continue
            val videoId = pvr.str("videoId") ?: continue
            if (videoId in seenVideoIds) continue
            val track = parseMixItem(pvr, playlistId, startIndex + tracks.size) ?: continue
            tracks += track
            lastVideoId = videoId
            // indexText lives under runs/simpleText, e.g. "5" — best-effort.
            val idxText = pvr.runsText("indexText") ?: pvr.path("indexText")?.str("simpleText")
            idxText?.toIntOrNull()?.let { lastIndex = it }
            pvr.path("navigationEndpoint")?.path("watchEndpoint")?.str("params")
                ?.let { lastParams = it }
        }

        val cont = lastVideoId?.let {
            MixContinuation(lastVideoId = it, playlistIndex = lastIndex, params = lastParams)
        }
        return MixPage(tracks = tracks, title = panelTitle, continuation = cont)
    }

    private fun findPlaylistPanel(root: JsonElement): JsonElement? {
        val contents = root.path("contents") ?: return null
        // MWEB single-column
        contents.path("singleColumnWatchNextResults")
            ?.path("playlist")?.path("playlist")
            ?.let { return it }
        // Desktop two-column (fallback)
        contents.path("twoColumnWatchNextResults")
            ?.path("playlist")?.path("playlist")
            ?.let { return it }
        return null
    }

    private fun parseMixItem(pvr: JsonElement, playlistId: String, trackNumber: Int): Track? {
        val videoId = pvr.str("videoId") ?: return null
        val title = pvr.runsText("title") ?: return null
        val artist = pvr.runsText("longBylineText")
            ?: pvr.runsText("shortBylineText")
            ?: ""
        // playlistPanelVideoRenderer carries lengthText (e.g. "3:45"), not lengthSeconds.
        val durationSec = pvr.str("lengthSeconds")?.toFloatOrNull()
            ?: pvr.runsText("lengthText")?.let { parseLengthText(it) }
            ?: 0f
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

    private fun parseLengthText(text: String): Float {
        val parts = text.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return 0f
        var total = 0
        for (p in parts) total = total * 60 + p
        return total.toFloat()
    }

    /**
     * Decodes the seed videoId from a Mix playlistId per NewPipe's rules.
     * Returns null for genre mixes (`RDGMEM*`) and unknown / non-Mix IDs;
     * the /next endpoint accepts playlistId alone for those.
     */
    fun extractMixSeedVideoId(playlistId: String): String? {
        if (!playlistId.startsWith("RD")) return null
        val videoIdRegex = Regex("^[A-Za-z0-9_-]{11}$")
        fun safe(substr: String): String? = substr.takeIf { videoIdRegex.matches(it) }
        return when {
            playlistId.startsWith("RDAMVM") && playlistId.length >= 17 ->
                safe(playlistId.substring(6, 17))
            playlistId.startsWith("RDCLAK") && playlistId.length >= 17 ->
                safe(playlistId.substring(6, 17))
            playlistId.startsWith("RDMM") && playlistId.length >= 15 ->
                safe(playlistId.substring(4, 15))
            playlistId.startsWith("RDGMEM") -> null
            playlistId.startsWith("RDEM") -> null
            playlistId.length == 13 -> safe(playlistId.substring(2, 13))
            else -> null
        }
    }
}
