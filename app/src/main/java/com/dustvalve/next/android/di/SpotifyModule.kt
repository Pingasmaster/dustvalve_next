package com.dustvalve.next.android.di

import com.dustvalve.next.android.data.repository.SpotifyRepositoryImpl
import com.dustvalve.next.android.domain.repository.SpotifyRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SpotifyModule {

    @Binds
    abstract fun bindSpotifyRepository(impl: SpotifyRepositoryImpl): SpotifyRepository
}
