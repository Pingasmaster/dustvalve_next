package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.MusicCollection
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SoundCloudHomeFeed
import com.dustvalve.next.android.domain.model.StreamPolicy
import com.dustvalve.next.android.domain.model.Track

/**
 * Anonymous SoundCloud api-v2 access (NewPipe / yt-dlp style client_id).
 *
 * Resolve is folded into [getTrack] / [getArtist] / [getAlbum] /
 * [getCollection]: pass a permalink URL or a numeric / `sc_` id where
 * applicable. Stream URLs are resolved on demand via [resolvePlayableStream].
 */
interface SoundCloudRepository {
    /**
     * Home feed: all-music trending charts (DRM/ghost tracks filtered) plus
     * mixed-selections shelves. [genre] is accepted for call-site compat but
     * only `all-music` charts exist; other values are ignored.
     */
    suspend fun getHome(genre: String = "all-music"): SoundCloudHomeFeed
    suspend fun search(query: String, filter: String? = null): List<SearchResult>
    suspend fun getTrack(urlOrId: String): Track
    suspend fun getStreamUrl(track: Track): String

    /**
     * Resolve a playable CDN URL and the [StreamPolicy] inferred from the
     * fresh track JSON (so callers can stamp HLS-only / blocked on the queue).
     */
    suspend fun resolvePlayableStream(track: Track): SoundCloudResolvedStream

    /**
     * Progressive-only CDN URL suitable for file download (rejects HLS and
     * encrypted/Go+ transcodings). Pair is (url, format inferred from path).
     */
    suspend fun getDownloadableStream(track: Track): Pair<String, AudioFormat>
    suspend fun getArtist(url: String): Artist
    suspend fun getArtistTracks(url: String, continuation: Any? = null): MusicCollection
    suspend fun getAlbum(url: String): Album
    suspend fun getCollection(url: String, continuation: Any? = null): MusicCollection
}

/** Playable SoundCloud stream plus policy inferred without guessing from the URL alone. */
data class SoundCloudResolvedStream(val url: String, val streamPolicy: StreamPolicy)
