package com.dustvalve.next.android.data.mapper

import com.dustvalve.next.android.data.local.db.entity.PlaylistEntity
import com.dustvalve.next.android.domain.model.Playlist

fun PlaylistEntity.toDomain(): Playlist {
    return Playlist(
        id = id,
        name = name,
        iconUrl = iconUrl,
        shapeKey = shapeKey,
        isSystem = isSystem,
        systemType = systemType?.let { name ->
            Playlist.SystemPlaylistType.entries.find { it.name == name }
        },
        isPinned = isPinned,
        sortOrder = sortOrder,
        trackCount = trackCount,
        autoDownload = autoDownload,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun Playlist.toEntity(): PlaylistEntity {
    return PlaylistEntity(
        id = id,
        name = name,
        iconUrl = iconUrl,
        shapeKey = shapeKey,
        isSystem = isSystem,
        systemType = systemType?.name,
        isPinned = isPinned,
        sortOrder = sortOrder,
        trackCount = trackCount,
        autoDownload = autoDownload,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
