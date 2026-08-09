package com.dustvalve.next.android.di

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.dustvalve.next.android.player.AudioPowerPolicy
import com.dustvalve.next.android.player.OffloadAudioPowerPolicy
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
interface AudioPowerModule {

    @Binds
    @Singleton
    fun bindAudioPowerPolicy(impl: OffloadAudioPowerPolicy): AudioPowerPolicy
}
