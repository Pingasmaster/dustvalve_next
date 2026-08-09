package com.dustvalve.next.android.di

import com.dustvalve.next.android.data.repository.YouTubeRepositoryImpl
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface YouTubeModule {

    @Binds
    fun bindYouTubeRepository(impl: YouTubeRepositoryImpl): YouTubeRepository
}
