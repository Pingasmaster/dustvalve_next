package com.dustvalve.next.android.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Playlist(
    val id: String,
    val name: String,
    val iconUrl: String? = null,
    val shapeKey: String? = null,
    val isSystem: Boolean = false,
    val systemType: SystemPlaylistType? = null,
    val isPinned: Boolean = false,
    val sortOrder: Int = 0,
    val trackCount: Int = 0,
    val autoDownload: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    enum class SystemPlaylistType(val defaultName: String, val iconRes: String? = null) {
        DOWNLOADS("Downloads"),
        RECENT("Recent"),
        COLLECTION("Bandcamp purchases"),
        FAVORITES("Favorites"),
    }

    val isEditable: Boolean
        get() = !isSystem

    val isDeletable: Boolean
        get() = !isSystem

    val displaySubtitle: String
        get() = when {
            isSystem -> "Auto playlist"
            trackCount == 1 -> "1 song"
            else -> "$trackCount songs"
        }

    companion object {
        const val ID_DOWNLOADS = "system_downloads"
        const val ID_RECENT = "system_recent"
        const val ID_COLLECTION = "system_collection"
        const val ID_FAVORITES = "system_favorites"
    }
}
