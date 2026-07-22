package com.dustvalve.next.android.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dustvalve.next.android.BuildConfig
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DustvalveNextDatabase = Room.databaseBuilder(
        context,
        DustvalveNextDatabase::class.java,
        "dustvalve_database",
    )
        // Migration policy: destructive fallback is a DEBUG-only convenience
        // so schema churn during development never blocks on a hand-written
        // migration. Release builds deliberately omit it - shipping a schema
        // bump without a Migration must fail loudly on open
        // (IllegalStateException) instead of silently dropping every user
        // table (playlists, favorites, downloads). Exported schema JSON for
        // writing those migrations lives in core/database/schemas/.
        .apply { if (BuildConfig.DEBUG) fallbackToDestructiveMigration(dropAllTables = true) }
        // Write-Ahead Logging: readers and writers don't block each other,
        // which matters because DownloadService / DownloadController mutate
        // rows from a different dispatcher than UI queries. Default is
        // TRUNCATE on Room 2.7+ which serialises everything.
        //
        // We deliberately do NOT call enableMultiInstanceInvalidation() - it
        // binds a ServiceConnection that Robolectric can't satisfy, breaking
        // the Compose-test harness (TracksHeaderLabelTest et al.), and the
        // app is single-process anyway (one Application, no :remote Process
        // IPC). The trade-off is that writes from a hypothetical future
        // process boundary wouldn't invalidate this Room instance, but
        // that's not a path we use today.
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .build()

    @Provides
    @Singleton
    fun provideAlbumDao(database: DustvalveNextDatabase): AlbumDao = database.albumDao()

    @Provides
    @Singleton
    fun provideTrackDao(database: DustvalveNextDatabase): TrackDao = database.trackDao()

    @Provides
    @Singleton
    fun provideArtistDao(database: DustvalveNextDatabase): ArtistDao = database.artistDao()

    @Provides
    @Singleton
    fun provideFavoriteDao(database: DustvalveNextDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    @Singleton
    fun provideRecentTrackDao(database: DustvalveNextDatabase): RecentTrackDao = database.recentTrackDao()

    @Provides
    @Singleton
    fun provideDownloadDao(database: DustvalveNextDatabase): DownloadDao = database.downloadDao()

    @Provides
    @Singleton
    fun providePlaylistDao(database: DustvalveNextDatabase): PlaylistDao = database.playlistDao()

    @Provides
    @Singleton
    fun provideRecentSearchDao(database: DustvalveNextDatabase): RecentSearchDao = database.recentSearchDao()

    @Provides
    @Singleton
    fun provideYouTubeVideoCacheDao(database: DustvalveNextDatabase): YouTubeVideoCacheDao = database.youtubeVideoCacheDao()

    @Provides
    @Singleton
    fun provideYouTubePlaylistCacheDao(database: DustvalveNextDatabase): YouTubePlaylistCacheDao = database.youtubePlaylistCacheDao()

    @Provides
    @Singleton
    fun provideYouTubeMusicHomeCacheDao(database: DustvalveNextDatabase): YouTubeMusicHomeCacheDao = database.youtubeMusicHomeCacheDao()
}
