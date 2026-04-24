package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.MusicCollection
import com.dustvalve.next.android.domain.model.MusicProvider
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
 * [YouTubeRepository] returns in its `page` parameter — passed through
 * verbatim.
 */
@Singleton
class YouTubeSource @Inject constructor(
    private val youtubeRepository: YouTubeRepository,
) : MusicSource {

    override val provider: MusicProvider = MusicProvider.YOUTUBE
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

    override suspend fun getArtistTracks(
        url: String,
        continuation: Any?,
    ): MusicCollection {
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

    override suspend fun getAlbum(url: String): Album =
        throw UnsupportedSourceOperation(id, SourceConcept.ALBUM)

    override suspend fun getCollection(
        url: String,
        continuation: Any?,
    ): MusicCollection {
        // Mixes (auto-generated radio playlists, IDs starting with `RD`) are
        // effectively infinite and don't live under the static /browse
        // endpoint — they paginate via /next, one page at a time. Regular
        // playlists keep the synchronous "load to end" behaviour because they
        // are bounded.
        val playlistId = extractPlaylistId(url)
        if (playlistId != null && playlistId.startsWith("RD")) {
            val (tracks, title, nextCursor) = youtubeRepository.getMixPage(
                mixUrl = url,
                cursor = continuation,
                seenVideoIds = emptySet(),
            )
            return MusicCollection(
                id = url,
                url = url,
                name = title,
                owner = "",
                coverUrl = null,
                tracks = tracks,
                continuation = nextCursor,
                hasMore = nextCursor != null && tracks.isNotEmpty(),
            )
        }
        // YouTubeRepository.getPlaylistTracks internally paginates to the end,
        // so we ignore the [continuation] arg — the returned collection is
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

    private fun extractPlaylistId(url: String): String? =
        Regex("[?&]list=([A-Za-z0-9_-]+)").find(url)?.groupValues?.getOrNull(1)
            ?: url.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) && it.length in 8..64 }
}
