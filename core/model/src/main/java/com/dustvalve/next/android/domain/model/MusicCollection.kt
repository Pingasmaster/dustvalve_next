package com.dustvalve.next.android.domain.model

import androidx.compose.runtime.Immutable

/**
 * Generic "collection of tracks" — playlist, radio, mix, curated list.
 *
 * Used by [com.dustvalve.next.android.domain.repository.MusicSource] so UI
 * code can render any source's playlist without knowing whether the content
 * came from YouTube, YT Music, or Bandcamp (Bandcamp has no public playlist
 * concept but a future source might).
 *
 * Not a `kotlin.collections.Collection`; the prefix avoids a name clash.
 */
@Immutable
data class MusicCollection(
    val id: String,
    val url: String,
    val name: String,
    /** Uploader / channel / curator name. May be blank. */
    val owner: String,
    val coverUrl: String?,
    val tracks: List<Track>,
    /**
     * Source-opaque pagination cursor. Pass back verbatim to
     * [com.dustvalve.next.android.domain.repository.MusicSource.getCollection]
     * or [com.dustvalve.next.android.domain.repository.MusicSource.getArtistTracks]
     * to fetch the next page. `null` means end-of-list. Typed as [Any] so each
     * source can choose its own representation (plain string, JSON blob, data
     * class) without a cross-source serialization contract — the adapter
     * always unpacks its own tokens.
     */
    val continuation: Any? = null,
    /** True when the caller should request more via [continuation]. */
    val hasMore: Boolean = false,
)
