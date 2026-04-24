package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.MusicCollection
import com.dustvalve.next.android.domain.model.MusicProvider
import com.dustvalve.next.android.domain.model.SearchResult

/**
 * Provider-agnostic port for "find + browse music from a source." Used by the
 * unified UI so one screen can render artists / albums / collections from any
 * source.
 *
 * Each [MusicSource] reports which concepts it supports via [capabilities].
 * Calling an unsupported concept throws [UnsupportedSourceOperation] — callers
 * should check capabilities first, or only route to a source that supports
 * the concept they need.
 *
 * Implementations live under `data/repository/` and wrap the existing
 * per-source repositories (`BandcampSearchRepository`, `YouTubeRepository`,
 * `YouTubeMusicRepository`). No new network code — this is a pure adapter
 * layer.
 */
interface MusicSource {

    /** Which provider this source represents. */
    val provider: MusicProvider

    /**
     * Stable per-source identifier. Two sources can share [provider] but
     * have distinct ids — e.g. the standard YouTube source and the YT Music
     * source both return [MusicProvider.YOUTUBE] but `id` is "youtube" vs
     * "youtube_music". Use for navigation routing and registry lookup.
     */
    val id: String

    /** Concepts this source supports — see [SourceConcept]. */
    val capabilities: Set<SourceConcept>

    /**
     * Search the source. [filter] is a source-specific freeform string
     * (YT Music filter params, Bandcamp category slug, etc.); null for
     * unfiltered search.
     */
    suspend fun search(query: String, filter: String? = null): List<SearchResult>

    /**
     * Load artist detail — name, bio, cover, and (for sources with an album
     * concept) the discography in `Artist.albums`. For sources that expose
     * flat track lists rather than albums (YouTube channels), `albums` is
     * empty and callers should use [getArtistTracks] for the track feed.
     */
    suspend fun getArtist(url: String): Artist

    /**
     * Paginated track feed for an artist. Returns the artist's tracks as a
     * [MusicCollection] (owner = artist name). Use the returned
     * [MusicCollection.continuation] as the [continuation] arg for the next
     * page. Throws [UnsupportedSourceOperation] for sources that don't expose
     * a per-artist flat track feed (Bandcamp — all Bandcamp tracks live under
     * an album, not directly under the artist).
     */
    suspend fun getArtistTracks(
        url: String,
        continuation: Any? = null,
    ): MusicCollection

    /** Load album detail. Throws [UnsupportedSourceOperation] on sources without an album concept. */
    suspend fun getAlbum(url: String): Album

    /**
     * Load a collection (playlist / radio / mix). Throws
     * [UnsupportedSourceOperation] on sources without a collection concept.
     */
    suspend fun getCollection(
        url: String,
        continuation: Any? = null,
    ): MusicCollection
}

/** The browse concepts a [MusicSource] may expose. */
enum class SourceConcept {
    SEARCH,
    ARTIST,
    /** Per-artist flat track feed — distinct from ARTIST because Bandcamp supports the artist concept but not a flat track list. */
    ARTIST_TRACKS,
    ALBUM,
    COLLECTION,
}

/** Thrown when callers invoke a [MusicSource] method the source doesn't support. */
class UnsupportedSourceOperation(
    sourceId: String,
    concept: SourceConcept,
) : UnsupportedOperationException(
    "Source '$sourceId' does not support ${concept.name.lowercase()}",
)
