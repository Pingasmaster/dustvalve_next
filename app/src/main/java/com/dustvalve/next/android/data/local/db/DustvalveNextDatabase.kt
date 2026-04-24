package com.dustvalve.next.android.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dustvalve.next.android.data.local.db.dao.AlbumDao
import com.dustvalve.next.android.data.local.db.dao.ArtistDao
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.PlaylistDao
import com.dustvalve.next.android.data.local.db.dao.RecentSearchDao
import com.dustvalve.next.android.data.local.db.dao.RecentTrackDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.dao.YouTubeMusicHomeCacheDao
import com.dustvalve.next.android.data.local.db.dao.YouTubePlaylistCacheDao
import com.dustvalve.next.android.data.local.db.dao.YouTubeVideoCacheDao
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

@Database(
    entities = [
        AlbumEntity::class,
        TrackEntity::class,
        ArtistEntity::class,
        FavoriteEntity::class,
        RecentTrackEntity::class,
        DownloadEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        RecentSearchEntity::class,
        YouTubeVideoCacheEntity::class,
        YouTubePlaylistCacheEntity::class,
        YouTubeMusicHomeCacheEntity::class,
    ],
    version = 1,
    exportSchema = false
)
abstract class DustvalveNextDatabase : RoomDatabase() {

    abstract fun albumDao(): AlbumDao
    abstract fun trackDao(): TrackDao
    abstract fun artistDao(): ArtistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentTrackDao(): RecentTrackDao
    abstract fun downloadDao(): DownloadDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun youtubeVideoCacheDao(): YouTubeVideoCacheDao
    abstract fun youtubePlaylistCacheDao(): YouTubePlaylistCacheDao
    abstract fun youtubeMusicHomeCacheDao(): YouTubeMusicHomeCacheDao
}
