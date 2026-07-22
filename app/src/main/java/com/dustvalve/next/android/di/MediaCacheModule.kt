package com.dustvalve.next.android.di

import com.dustvalve.next.android.domain.repository.MediaCacheClearer
import com.dustvalve.next.android.player.MediaCacheClearerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the app-layer media-cache clearer (backed by the @Singleton Media3
 * SimpleCache from PlayerModule) as the [MediaCacheClearer] the data layer
 * uses when wiping downloads.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MediaCacheModule {

    @Binds
    @Singleton
    abstract fun bindMediaCacheClearer(impl: MediaCacheClearerImpl): MediaCacheClearer
}
