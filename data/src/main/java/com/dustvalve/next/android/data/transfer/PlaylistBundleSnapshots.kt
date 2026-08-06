package com.dustvalve.next.android.data.transfer

import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * DTOs and the Json configuration for the `.dvplaylist` bundle format.
 *
 * Kept separate from the Room entities so the on-the-wire bundle can evolve
 * without being bound by SQL-schema decisions (and so entities don't need
 * kotlinx.serialization metadata).
 */
object PlaylistBundleSerializer {

    val json: Json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}

@Serializable
data class PlaylistSnapshot(
    val id: String,
    val name: String,
    val iconUrl: String? = null,
    val shapeKey: String? = null,
    val isSystem: Boolean = false,
    val systemType: String? = null,
    val isPinned: Boolean = false,
    val sortOrder: Int = 0,
    val trackCount: Int = 0,
    val autoDownload: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class TrackSnapshot(
    val id: String,
    val albumId: String,
    val title: String,
    val artist: String,
    val artistUrl: String = "",
    val trackNumber: Int,
    val duration: Float,
    val streamUrl: String? = null,
    val artUrl: String,
    val albumTitle: String,
    val source: String = "bandcamp",
    val folderUri: String = "",
    val dateAdded: Long = 0L,
    val year: Int = 0,
    val albumUrl: String = "",
    val bandcampTrackUrl: String? = null,
)

fun TrackSnapshot.toEntity() = TrackEntity(
    id, albumId, title, artist, artistUrl, trackNumber, duration, streamUrl,
    artUrl, albumTitle, source, folderUri, dateAdded, year, albumUrl,
    bandcampTrackUrl,
)
