package com.dustvalve.next.android.data.storage.folder

import com.dustvalve.next.android.data.local.db.entity.AlbumEntity
import com.dustvalve.next.android.data.local.db.entity.ArtistEntity
import com.dustvalve.next.android.data.local.db.entity.DownloadEntity
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.local.db.entity.PlaylistEntity
import com.dustvalve.next.android.data.local.db.entity.PlaylistTrackEntity
import com.dustvalve.next.android.data.local.db.entity.RecentSearchEntity
import com.dustvalve.next.android.data.local.db.entity.RecentTrackEntity
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.dustvalve.next.android.data.local.db.entity.YouTubeMusicHomeCacheEntity
import com.dustvalve.next.android.data.local.db.entity.YouTubePlaylistCacheEntity
import com.dustvalve.next.android.data.local.db.entity.YouTubeVideoCacheEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Canonical DTOs for every JSON snapshot stored in the dedicated folder.
 *
 * Kept separate from Room entities so we can evolve persistence without being
 * bound by SQL-schema decisions (and so we don't have to annotate entities
 * with kotlinx.serialization metadata). One DTO = one `.json` file.
 */
object FolderSnapshotSerializer {

    val json: Json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}

// ─── Playlists ────────────────────────────────────────────────────────────

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
data class PlaylistTrackSnapshot(
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val addedAt: Long = 0L,
)

@Serializable
data class PlaylistsFile(
    val playlists: List<PlaylistSnapshot> = emptyList(),
    val mappings: List<PlaylistTrackSnapshot> = emptyList(),
)

fun PlaylistEntity.toSnapshot() = PlaylistSnapshot(
    id = id, name = name, iconUrl = iconUrl, shapeKey = shapeKey,
    isSystem = isSystem, systemType = systemType, isPinned = isPinned,
    sortOrder = sortOrder, trackCount = trackCount, autoDownload = autoDownload,
    createdAt = createdAt, updatedAt = updatedAt,
)
fun PlaylistSnapshot.toEntity() = PlaylistEntity(
    id = id, name = name, iconUrl = iconUrl, shapeKey = shapeKey,
    isSystem = isSystem, systemType = systemType, isPinned = isPinned,
    sortOrder = sortOrder, trackCount = trackCount, autoDownload = autoDownload,
    createdAt = createdAt, updatedAt = updatedAt,
)
fun PlaylistTrackEntity.toSnapshot() = PlaylistTrackSnapshot(playlistId, trackId, position, addedAt)
fun PlaylistTrackSnapshot.toEntity() = PlaylistTrackEntity(playlistId, trackId, position, addedAt)

// ─── Favorites ────────────────────────────────────────────────────────────

@Serializable
data class FavoriteSnapshot(
    val id: String,
    val type: String,
    val addedAt: Long = 0L,
    val isPinned: Boolean = false,
    val shapeKey: String? = null,
)

@Serializable
data class FavoritesFile(val favorites: List<FavoriteSnapshot> = emptyList())

fun FavoriteEntity.toSnapshot() = FavoriteSnapshot(id, type, addedAt, isPinned, shapeKey)
fun FavoriteSnapshot.toEntity() = FavoriteEntity(id, type, addedAt, isPinned, shapeKey)

// ─── Tracks ───────────────────────────────────────────────────────────────

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
)

@Serializable
data class TracksFile(val tracks: List<TrackSnapshot> = emptyList())

fun TrackEntity.toSnapshot() = TrackSnapshot(
    id, albumId, title, artist, artistUrl, trackNumber, duration, streamUrl,
    artUrl, albumTitle, source, folderUri, dateAdded, year, albumUrl,
)
fun TrackSnapshot.toEntity() = TrackEntity(
    id, albumId, title, artist, artistUrl, trackNumber, duration, streamUrl,
    artUrl, albumTitle, source, folderUri, dateAdded, year, albumUrl,
)

// ─── Albums ───────────────────────────────────────────────────────────────

@Serializable
data class AlbumSnapshot(
    val id: String,
    val url: String,
    val title: String,
    val artist: String,
    val artistUrl: String,
    val artUrl: String,
    val releaseDate: String? = null,
    val about: String? = null,
    val tags: String,
    val cachedAt: Long = 0L,
    val autoDownload: Boolean = false,
    val saleItemId: Long? = null,
    val saleItemType: String? = null,
    val source: String = "bandcamp",
    val discogPriceAmount: Double? = null,
    val discogPriceCurrency: String? = null,
    val discogUrl: String? = null,
    val discogName: String? = null,
    val singleTrackPriceAmount: Double? = null,
    val singleTrackPriceCurrency: String? = null,
)

@Serializable
data class AlbumsFile(val albums: List<AlbumSnapshot> = emptyList())

fun AlbumEntity.toSnapshot() = AlbumSnapshot(
    id, url, title, artist, artistUrl, artUrl, releaseDate, about, tags,
    cachedAt, autoDownload, saleItemId, saleItemType, source,
    discogPriceAmount, discogPriceCurrency, discogUrl, discogName,
    singleTrackPriceAmount, singleTrackPriceCurrency,
)
fun AlbumSnapshot.toEntity() = AlbumEntity(
    id, url, title, artist, artistUrl, artUrl, releaseDate, about, tags,
    cachedAt, autoDownload, saleItemId, saleItemType, source,
    discogPriceAmount, discogPriceCurrency, discogUrl, discogName,
    singleTrackPriceAmount, singleTrackPriceCurrency,
)

// ─── Artists ──────────────────────────────────────────────────────────────

@Serializable
data class ArtistSnapshot(
    val id: String,
    val name: String,
    val url: String,
    val imageUrl: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val cachedAt: Long = 0L,
    val autoDownload: Boolean = false,
    val albumIdOrder: String? = null,
    val source: String = "bandcamp",
    val hasDiscographyOffer: Boolean = false,
)

@Serializable
data class ArtistsFile(val artists: List<ArtistSnapshot> = emptyList())

fun ArtistEntity.toSnapshot() = ArtistSnapshot(
    id, name, url, imageUrl, bio, location, cachedAt, autoDownload,
    albumIdOrder, source, hasDiscographyOffer,
)
fun ArtistSnapshot.toEntity() = ArtistEntity(
    id, name, url, imageUrl, bio, location, cachedAt, autoDownload,
    albumIdOrder, source, hasDiscographyOffer,
)

// ─── Downloads ────────────────────────────────────────────────────────────

@Serializable
data class DownloadSnapshot(
    val trackId: String,
    val albumId: String,
    val filePath: String,
    val sizeBytes: Long,
    val downloadedAt: Long = 0L,
    val format: String = "mp3-128",
    val pinned: Boolean = true,
    val lastAccessed: Long = 0L,
)

@Serializable
data class DownloadsFile(val downloads: List<DownloadSnapshot> = emptyList())

fun DownloadEntity.toSnapshot() = DownloadSnapshot(
    trackId, albumId, filePath, sizeBytes, downloadedAt, format, pinned, lastAccessed,
)
fun DownloadSnapshot.toEntity() = DownloadEntity(
    trackId, albumId, filePath, sizeBytes, downloadedAt, format, pinned, lastAccessed,
)

// ─── History (recent tracks + recent searches) ────────────────────────────

@Serializable
data class RecentTrackSnapshot(val trackId: String, val playedAt: Long = 0L)

@Serializable
data class RecentSearchSnapshot(val query: String, val source: String, val searchedAt: Long = 0L)

@Serializable
data class HistoryFile(
    val tracks: List<RecentTrackSnapshot> = emptyList(),
    val searches: List<RecentSearchSnapshot> = emptyList(),
)

fun RecentTrackEntity.toSnapshot() = RecentTrackSnapshot(trackId, playedAt)
fun RecentTrackSnapshot.toEntity() = RecentTrackEntity(trackId, playedAt)
fun RecentSearchEntity.toSnapshot() = RecentSearchSnapshot(query, source, searchedAt)
fun RecentSearchSnapshot.toEntity() = RecentSearchEntity(query, source, searchedAt)

// ─── Metadata cache (YouTube + YTM home) ──────────────────────────────────

@Serializable
data class YtVideoSnapshot(
    val videoId: String,
    val title: String,
    val artist: String,
    val artistUrl: String,
    val durationSec: Float,
    val artUrl: String,
    val cachedAt: Long = 0L,
)

@Serializable
data class YtPlaylistSnapshot(
    val playlistId: String,
    val title: String,
    val videoIdsJson: String,
    val cachedAt: Long = 0L,
)

@Serializable
data class YtmHomeSnapshot(
    val key: String,
    val feedJson: String,
    val cachedAt: Long = 0L,
)

@Serializable
data class MetadataCacheFile(
    val videos: List<YtVideoSnapshot> = emptyList(),
    val playlists: List<YtPlaylistSnapshot> = emptyList(),
    val home: List<YtmHomeSnapshot> = emptyList(),
)

fun YouTubeVideoCacheEntity.toSnapshot() = YtVideoSnapshot(
    videoId, title, artist, artistUrl, durationSec, artUrl, cachedAt,
)
fun YtVideoSnapshot.toEntity() = YouTubeVideoCacheEntity(
    videoId, title, artist, artistUrl, durationSec, artUrl, cachedAt,
)
fun YouTubePlaylistCacheEntity.toSnapshot() = YtPlaylistSnapshot(
    playlistId, title, videoIdsJson, cachedAt,
)
fun YtPlaylistSnapshot.toEntity() = YouTubePlaylistCacheEntity(
    playlistId, title, videoIdsJson, cachedAt,
)
fun YouTubeMusicHomeCacheEntity.toSnapshot() = YtmHomeSnapshot(key, feedJson, cachedAt)
fun YtmHomeSnapshot.toEntity() = YouTubeMusicHomeCacheEntity(key, feedJson, cachedAt)

// ─── Settings ─────────────────────────────────────────────────────────────
//
// Settings are stored as a map of string keys to primitive values. Every
// DataStore key is encoded with a type-prefixed value to round-trip through
// JSON without losing type information.

@Serializable
data class SettingsFile(val entries: Map<String, SettingValue> = emptyMap())

@Serializable
sealed interface SettingValue {
    @Serializable
    data class BoolV(val value: Boolean) : SettingValue

    @Serializable
    data class IntV(val value: Int) : SettingValue

    @Serializable
    data class LongV(val value: Long) : SettingValue

    @Serializable
    data class FloatV(val value: Float) : SettingValue

    @Serializable
    data class StringV(val value: String) : SettingValue

    @Serializable
    data class StringSetV(val value: Set<String>) : SettingValue
}
