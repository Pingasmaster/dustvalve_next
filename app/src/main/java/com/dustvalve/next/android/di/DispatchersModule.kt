package com.dustvalve.next.android.di

import android.annotation.SuppressLint
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    // This provider IS the binding for Dispatchers.IO; suppressing the lint
    // here is the only place the raw reference has to leak — every consumer
    // receives a qualified @Dispatcher(AppDispatchers.IO) CoroutineDispatcher.
    @SuppressLint("SlackDispatchersUse")
    @Provides
    @Dispatcher(AppDispatchers.IO)
    fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(AppDispatchers.Default)
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
