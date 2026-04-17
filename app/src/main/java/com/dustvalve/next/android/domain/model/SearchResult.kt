package com.dustvalve.next.android.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class SearchResult(
    val type: SearchResultType,
    val name: String,
    val url: String,
    val imageUrl: String?,
    val artist: String?,
    val album: String?,
    val genre: String?,
    val releaseDate: String?,
)

enum class SearchResultType {
    ARTIST, ALBUM, TRACK, LOCAL_TRACK,
    YOUTUBE_TRACK, YOUTUBE_ALBUM, YOUTUBE_ARTIST, YOUTUBE_PLAYLIST,
    SPOTIFY_TRACK, SPOTIFY_ALBUM, SPOTIFY_ARTIST, SPOTIFY_PLAYLIST,
}
