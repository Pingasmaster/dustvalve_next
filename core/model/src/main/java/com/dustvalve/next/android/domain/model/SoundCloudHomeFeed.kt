package com.dustvalve.next.android.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class SoundCloudHomeFeed(val genre: String, val trending: List<Track>, val shelves: List<SoundCloudShelf>)

@Immutable
data class SoundCloudShelf(val title: String, val items: List<SoundCloudShelfItem>)

@Immutable
data class SoundCloudShelfItem(
    val kind: SoundCloudShelfKind,
    val id: String,
    val title: String,
    val subtitle: String,
    val url: String,
    val artUrl: String?,
)

enum class SoundCloudShelfKind {
    TRACK,
    PLAYLIST,
    USER,
    ALBUM,
}
