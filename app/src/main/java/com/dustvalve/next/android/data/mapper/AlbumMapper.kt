package com.dustvalve.next.android.data.mapper

import com.dustvalve.next.android.data.local.db.entity.AlbumEntity
import com.dustvalve.next.android.data.local.db.entity.ArtistEntity
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.PurchaseInfo
import com.dustvalve.next.android.domain.model.Track
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val tagJson = Json { ignoreUnknownKeys = true }

fun AlbumEntity.toDomain(tracks: List<Track>, isFavorite: Boolean): Album = Album(
    id = id,
    url = url,
    title = title,
    artist = artist,
    artistUrl = artistUrl,
    artUrl = artUrl,
    releaseDate = releaseDate,
    about = about,
    tracks = tracks,
    tags = if (tags.isBlank()) {
        emptyList()
    } else {
        try {
            tagJson.decodeFromString<List<String>>(tags).take(50)
        } catch (_: Exception) {
            // Fallback for legacy comma-separated format
            tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(50)
        }
    },
    isFavorite = isFavorite,
    autoDownload = autoDownload,
    purchaseInfo = if (saleItemId != null && saleItemType != null) {
        PurchaseInfo(saleItemId, saleItemType)
    } else null,
)

fun Album.toEntity(): AlbumEntity = AlbumEntity(
    id = id,
    url = url,
    title = title,
    artist = artist,
    artistUrl = artistUrl,
    artUrl = artUrl,
    releaseDate = releaseDate,
    about = about,
    tags = tagJson.encodeToString(tags),
    autoDownload = autoDownload,
    saleItemId = purchaseInfo?.saleItemId,
    saleItemType = purchaseInfo?.saleItemType,
)

fun TrackEntity.toDomain(isFavorite: Boolean): Track = Track(
    id = id,
    albumId = albumId,
    title = title,
    artist = artist,
    artistUrl = artistUrl,
    trackNumber = trackNumber,
    duration = duration,
    streamUrl = streamUrl,
    artUrl = artUrl,
    albumTitle = albumTitle,
    isFavorite = isFavorite,
)

fun Track.toEntity(): TrackEntity = TrackEntity(
    id = id,
    albumId = albumId,
    title = title,
    artist = artist,
    artistUrl = artistUrl,
    trackNumber = trackNumber,
    duration = duration,
    streamUrl = streamUrl,
    artUrl = artUrl,
    albumTitle = albumTitle,
)

fun ArtistEntity.toDomain(albums: List<Album>, isFavorite: Boolean = false): Artist = Artist(
    id = id,
    name = name,
    url = url,
    imageUrl = imageUrl,
    bio = bio,
    location = location,
    albums = albums,
    isFavorite = isFavorite,
    autoDownload = autoDownload,
)

fun Artist.toEntity(): ArtistEntity = ArtistEntity(
    id = id,
    name = name,
    url = url,
    imageUrl = imageUrl,
    bio = bio,
    location = location,
    autoDownload = autoDownload,
    albumIdOrder = tagJson.encodeToString(albums.map { it.id }),
)
