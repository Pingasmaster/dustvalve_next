package com.dustvalve.next.android.di

import android.content.Context
import androidx.room.Room
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.AlbumDao
import com.dustvalve.next.android.data.local.db.dao.ArtistDao
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.PlaylistDao
import com.dustvalve.next.android.data.local.db.dao.RecentSearchDao
import com.dustvalve.next.android.data.local.db.dao.RecentTrackDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
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
    fun provideDatabase(@ApplicationContext context: Context): DustvalveNextDatabase {
        return Room.databaseBuilder(
            context,
            DustvalveNextDatabase::class.java,
            "dustvalve_database",
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    @Singleton
    fun provideAlbumDao(database: DustvalveNextDatabase): AlbumDao {
        return database.albumDao()
    }

    @Provides
    @Singleton
    fun provideTrackDao(database: DustvalveNextDatabase): TrackDao {
        return database.trackDao()
    }

    @Provides
    @Singleton
    fun provideArtistDao(database: DustvalveNextDatabase): ArtistDao {
        return database.artistDao()
    }

    @Provides
    @Singleton
    fun provideFavoriteDao(database: DustvalveNextDatabase): FavoriteDao {
        return database.favoriteDao()
    }

    @Provides
    @Singleton
    fun provideRecentTrackDao(database: DustvalveNextDatabase): RecentTrackDao {
        return database.recentTrackDao()
    }

    @Provides
    @Singleton
    fun provideDownloadDao(database: DustvalveNextDatabase): DownloadDao {
        return database.downloadDao()
    }

    @Provides
    @Singleton
    fun providePlaylistDao(database: DustvalveNextDatabase): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    @Singleton
    fun provideRecentSearchDao(database: DustvalveNextDatabase): RecentSearchDao {
        return database.recentSearchDao()
    }
}
