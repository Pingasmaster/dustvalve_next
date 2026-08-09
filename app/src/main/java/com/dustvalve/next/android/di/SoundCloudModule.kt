package com.dustvalve.next.android.di

import com.dustvalve.next.android.data.repository.SoundCloudRepositoryImpl
import com.dustvalve.next.android.domain.repository.SoundCloudRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface SoundCloudModule {

    @Binds
    fun bindSoundCloudRepository(impl: SoundCloudRepositoryImpl): SoundCloudRepository
}
