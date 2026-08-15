package com.dustvalve.next.android.data.network

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RefreshGateModule {
    @Binds
    @Singleton
    abstract fun bindOpportunisticRefreshGate(impl: UnmeteredRefreshGate): OpportunisticRefreshGate
}
