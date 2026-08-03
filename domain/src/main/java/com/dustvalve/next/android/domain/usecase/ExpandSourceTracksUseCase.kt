package com.dustvalve.next.android.domain.usecase

import com.dustvalve.next.android.domain.model.MusicCollection
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.MusicSource
import com.dustvalve.next.android.domain.repository.SourceConcept
import javax.inject.Inject

/**
 * Drains a paginated [MusicSource] track feed into a single list, capped at
 * [MAX_TRACKS]. Used before play / download so infinite collections (YouTube
 * Mix, SoundCloud artist uploads, channel videos) queue or download more than
 * the first scrolled page.
 */
class ExpandSourceTracksUseCase @Inject constructor() {

    /**
     * Expand a collection via [MusicSource.getCollection], starting from the
     * already-loaded [seed] tracks / cursor when provided.
     */
    suspend fun expandCollection(
        source: MusicSource,
        url: String,
        seedTracks: List<Track> = emptyList(),
        seedContinuation: Any? = null,
        seedHasMore: Boolean = seedContinuation != null,
        maxTracks: Int = MAX_TRACKS,
    ): List<Track> {
        if (SourceConcept.COLLECTION !in source.capabilities) {
            return seedTracks.take(maxTracks)
        }
        return drain(
            seedTracks = seedTracks,
            seedContinuation = seedContinuation,
            seedHasMore = seedHasMore || seedTracks.isEmpty(),
            maxTracks = maxTracks,
        ) { continuation ->
            source.getCollection(url, continuation)
        }
    }

    /**
     * Expand a flat artist track feed via [MusicSource.getArtistTracks].
     */
    suspend fun expandArtistTracks(
        source: MusicSource,
        url: String,
        seedTracks: List<Track> = emptyList(),
        seedContinuation: Any? = null,
        seedHasMore: Boolean = seedContinuation != null,
        maxTracks: Int = MAX_TRACKS,
    ): List<Track> {
        if (SourceConcept.ARTIST_TRACKS !in source.capabilities) {
            return seedTracks.take(maxTracks)
        }
        return drain(
            seedTracks = seedTracks,
            seedContinuation = seedContinuation,
            seedHasMore = seedHasMore || seedTracks.isEmpty(),
            maxTracks = maxTracks,
        ) { continuation ->
            source.getArtistTracks(url, continuation)
        }
    }

    private suspend fun drain(
        seedTracks: List<Track>,
        seedContinuation: Any?,
        seedHasMore: Boolean,
        maxTracks: Int,
        fetch: suspend (continuation: Any?) -> MusicCollection,
    ): List<Track> {
        val out = ArrayList<Track>(seedTracks.size.coerceAtLeast(64))
        val seen = HashSet<String>()
        for (track in seedTracks) {
            if (seen.add(track.id)) out += track
            if (out.size >= maxTracks) return out
        }

        var continuation: Any? = seedContinuation
        var hasMore = seedHasMore
        // First page when the caller has nothing yet.
        if (out.isEmpty()) {
            val first = fetch(null)
            for (track in first.tracks) {
                if (seen.add(track.id)) out += track
                if (out.size >= maxTracks) return out
            }
            continuation = first.continuation
            hasMore = first.hasMore && first.tracks.isNotEmpty()
        }

        var pages = 0
        while (hasMore && out.size < maxTracks && pages < MAX_PAGES) {
            val page = fetch(continuation)
            var added = 0
            for (track in page.tracks) {
                if (!seen.add(track.id)) continue
                out += track
                added += 1
                if (out.size >= maxTracks) return out
            }
            continuation = page.continuation
            hasMore = page.hasMore && added > 0 && continuation != null
            pages += 1
        }
        return out
    }

    companion object {
        /** Hard ceiling for queue / download expansion of infinite feeds. */
        const val MAX_TRACKS = 5_000

        /**
         * Safety against runaway pagination even when [MAX_TRACKS] is not hit
         * (tiny pages). At 50 tracks/page this still covers well past the
         * track cap.
         */
        const val MAX_PAGES = 200
    }
}
