package com.dustvalve.next.android.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Track(
    val id: String,
    val albumId: String,
    val title: String,
    val artist: String,
    val artistUrl: String = "",
    val trackNumber: Int,
    val duration: Float,
    val streamUrl: String?,
    val artUrl: String,
    val albumTitle: String,
    val isFavorite: Boolean = false,
    val source: TrackSource = TrackSource.BANDCAMP,
    val folderUri: String = "",
    val dateAdded: Long = 0,
    val year: Int = 0,
    /**
     * Album page URL for navigation (used by the player's Album button).
     * Only populated for tracks that came from an album scrape (Bandcamp);
     * empty for streaming sources where there is no canonical album URL.
     */
    val albumUrl: String = "",
    /**
     * Track-page URL on Bandcamp (e.g. `https://moeshop.bandcamp.com/track/cerise`),
     * captured from the album's `tralbumData.trackinfo[*].title_link`. Used to
     * fetch each track's individual `defaultPrice` so the album-view row
     * subtitle can show "$1.50" per track. Null for tracks Bandcamp doesn't
     * sell separately (`title_link == null`) and for non-Bandcamp tracks
     * (LOCAL, YouTube).
     */
    val bandcampTrackUrl: String? = null,
) {
    val isLocal: Boolean get() = source == TrackSource.LOCAL
}
