package com.dustvalve.next.android.di

import com.dustvalve.next.android.domain.repository.BandcampStreamUrlResolver
import com.dustvalve.next.android.ui.screens.player.DustvalveBandcampStreamUrlResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface BandcampStreamModule {
    @Binds
    @Singleton
    fun bindBandcampStreamUrlResolver(impl: DustvalveBandcampStreamUrlResolver): BandcampStreamUrlResolver
}
