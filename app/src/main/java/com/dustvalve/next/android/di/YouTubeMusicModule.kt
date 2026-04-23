package com.dustvalve.next.android.di

import com.dustvalve.next.android.data.repository.YouTubeMusicRepositoryImpl
import com.dustvalve.next.android.domain.repository.YouTubeMusicRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class YouTubeMusicModule {

    @Binds
    abstract fun bindYouTubeMusicRepository(impl: YouTubeMusicRepositoryImpl): YouTubeMusicRepository
}
