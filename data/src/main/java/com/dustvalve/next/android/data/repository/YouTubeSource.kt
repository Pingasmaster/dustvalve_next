package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlaylistParser
import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.MusicCollection
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.repository.MusicSource
import com.dustvalve.next.android.domain.repository.SourceConcept
import com.dustvalve.next.android.domain.repository.UnsupportedSourceOperation
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MusicSource] adapter for standard YouTube (non-Music). Supports search,
 * artist (as a channel), artist tracks feed (paginated channel videos), and
 * collection (playlist). YouTube has no distinct "album" concept, so
 * [getAlbum] throws.
 *
 * Pagination cursors are the same opaque [Any] tokens the underlying
 * [YouTubeRepository] returns in its `page` parameter - passed through
 * verbatim, EXCEPT for Mix collections, whose cursor is a string that also
 * encodes the already-delivered videoIds (see [encodeMixCursor]) so the
 * repository's dedupe receives the seen set on every follow-up page.
 */
@Singleton
class YouTubeSource @Inject constructor(private val youtubeRepository: YouTubeRepository) : MusicSource {

    override val id: String = "youtube"
    override val capabilities: Set<SourceConcept> = setOf(
        SourceConcept.SEARCH,
        SourceConcept.ARTIST,
        SourceConcept.ARTIST_TRACKS,
        SourceConcept.COLLECTION,
    )

    override suspend fun search(query: String, filter: String?): List<SearchResult> {
        val (results, _) = youtubeRepository.search(query = query, filter = filter)
        return results
    }

    /**
     * Returns an Artist with metadata only; YT channel browse does not give
     * us albums, so `albums` is empty. Callers pull the flat track feed via
     * [getArtistTracks]. `imageUrl` is unavailable from the channel browse
     * endpoint used here; the caller should pass through the thumbnail it
     * already has from the SearchResult that opened the artist screen.
     */
    override suspend fun getArtist(url: String): Artist {
        val (_, channelName, _) = youtubeRepository.getChannelVideos(url, page = null)
        return Artist(
            id = url,
            name = channelName ?: "",
            url = url,
            imageUrl = null,
            bio = null,
            location = null,
            albums = emptyList(),
        )
    }

    override suspend fun getArtistTracks(url: String, continuation: Any?): MusicCollection {
        val (tracks, channelName, nextPage) = youtubeRepository.getChannelVideos(
            channelUrl = url,
            page = continuation,
        )
        return MusicCollection(
            id = url,
            url = url,
            name = channelName ?: "",
            owner = channelName ?: "",
            coverUrl = null,
            tracks = tracks,
            continuation = nextPage,
            hasMore = nextPage != null && tracks.isNotEmpty(),
        )
    }

    override suspend fun getAlbum(url: String): Album = throw UnsupportedSourceOperation(id, SourceConcept.ALBUM)

    override suspend fun getCollection(url: String, continuation: Any?): MusicCollection {
        // Mixes (auto-generated radio playlists, IDs starting with `RD`) are
        // effectively infinite and don't live under the static /browse
        // endpoint - they paginate via /next, one page at a time. Regular
        // playlists keep the synchronous "load to end" behaviour because they
        // are bounded.
        val playlistId = extractPlaylistId(url)
        if (playlistId != null && playlistId.startsWith("RD")) {
            // The opaque cursor string this source hands out encodes BOTH the
            // repository's /next continuation and the videoIds already
            // delivered, so the parser's dedupe actually receives the seen
            // set on every follow-up page (YT re-returns ~24 trailing items
            // per /next page).
            val (repoCursor, seenIds) = decodeMixCursor(continuation)
            val (tracks, title, nextCursor) = youtubeRepository.getMixPage(
                mixUrl = url,
                cursor = repoCursor,
                seenVideoIds = seenIds.toSet(),
            )
            val deliveredIds = (seenIds + tracks.map { it.id.removePrefix("yt_") })
                .takeLast(MIX_CURSOR_MAX_SEEN_IDS)
            val encodedNext = when (nextCursor) {
                null -> null

                is YouTubePlaylistParser.MixContinuation -> encodeMixCursor(nextCursor, deliveredIds)

                // Unknown cursor type: pass through verbatim (repo will
                // re-interpret it); dedupe degrades gracefully to none.
                else -> nextCursor
            }
            return MusicCollection(
                id = url,
                url = url,
                name = title,
                owner = "",
                coverUrl = null,
                tracks = tracks,
                continuation = encodedNext,
                hasMore = encodedNext != null && tracks.isNotEmpty(),
            )
        }
        // YouTubeRepository.getPlaylistTracks internally paginates to the end,
        // so we ignore the [continuation] arg - the returned collection is
        // always complete.
        val (tracks, title) = youtubeRepository.getPlaylistTracks(url)
        return MusicCollection(
            id = url,
            url = url,
            name = title,
            owner = "",
            coverUrl = null,
            tracks = tracks,
            continuation = null,
            hasMore = false,
        )
    }

    private fun extractPlaylistId(url: String): String? = Regex("[?&]list=([A-Za-z0-9_-]+)").find(url)?.groupValues?.getOrNull(1)
        ?: url.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) && it.length in 8..64 }

    /**
     * Mix cursor codec. Format:
     * `lastVideoId:playlistIndex:params|id1,id2,...` - the part before `|`
     * is the repository's MixContinuation, the part after is the ordered
     * list of already-delivered videoIds (capped at the most recent
     * [MIX_CURSOR_MAX_SEEN_IDS]). videoIds are `[A-Za-z0-9_-]`, so neither
     * delimiter can collide; `params` goes last in the head because it is
     * the only free-form field.
     */
    private fun encodeMixCursor(cursor: YouTubePlaylistParser.MixContinuation, seenIds: List<String>): String =
        cursor.lastVideoId + MIX_CURSOR_FIELD_DELIMITER +
            cursor.playlistIndex + MIX_CURSOR_FIELD_DELIMITER +
            cursor.params.orEmpty() +
            MIX_CURSOR_DELIMITER + seenIds.joinToString(MIX_CURSOR_ID_DELIMITER)

    /**
     * Inverse of [encodeMixCursor]. Legacy / foreign cursors degrade
     * gracefully: a non-string token passes through verbatim with no seen
     * ids; a string without the `|` delimiter is treated as head-only; an
     * unparseable head restarts the mix (null repo cursor) while any seen
     * ids still feed the dedupe.
     */
    private fun decodeMixCursor(continuation: Any?): Pair<Any?, List<String>> {
        if (continuation == null) return null to emptyList()
        if (continuation !is String) return continuation to emptyList()
        val head = continuation.substringBefore(MIX_CURSOR_DELIMITER)
        val seenIds = continuation.substringAfter(MIX_CURSOR_DELIMITER, missingDelimiterValue = "")
            .split(MIX_CURSOR_ID_DELIMITER)
            .filter { it.isNotBlank() }
        val fields = head.split(MIX_CURSOR_FIELD_DELIMITER, limit = MIX_CURSOR_HEAD_FIELDS)
        val index = fields.getOrNull(1)?.toIntOrNull()
        val repoCursor = if (fields.size == MIX_CURSOR_HEAD_FIELDS && fields[0].isNotBlank() && index != null) {
            YouTubePlaylistParser.MixContinuation(
                lastVideoId = fields[0],
                playlistIndex = index,
                params = fields[2].takeIf { it.isNotBlank() },
            )
        } else {
            null
        }
        return repoCursor to seenIds
    }

    private companion object {
        const val MIX_CURSOR_DELIMITER = "|"
        const val MIX_CURSOR_FIELD_DELIMITER = ":"
        const val MIX_CURSOR_ID_DELIMITER = ","

        /** lastVideoId, playlistIndex, params. */
        const val MIX_CURSOR_HEAD_FIELDS = 3

        /**
         * Cap on remembered videoIds - plenty to cover YT's ~24-item
         * re-return window many pages deep without growing unboundedly.
         */
        const val MIX_CURSOR_MAX_SEEN_IDS = 200
    }
}
