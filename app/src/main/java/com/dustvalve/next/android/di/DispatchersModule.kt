package com.dustvalve.next.android.di

import android.util.Log
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.ApplicationScope
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    // This provider IS the binding for Dispatchers.IO / Default: the raw
    // Dispatchers.* reference must live here so every consumer can take a
    // qualified @Dispatcher(...) CoroutineDispatcher. InjectDispatcher and
    // Slack RawDispatchersUse both false-positive at this binding site.
    @Suppress("InjectDispatcher", "RawDispatchersUse")
    @Provides
    @Dispatcher(AppDispatchers.IO)
    fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Suppress("InjectDispatcher", "RawDispatchersUse")
    @Provides
    @Dispatcher(AppDispatchers.Default)
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /** One supervised app-lifetime scope for singletons' background work. */
    @Provides
    @Singleton
    @ApplicationScope
    fun providesApplicationScope(@Dispatcher(AppDispatchers.Default) defaultDispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(
            SupervisorJob() + defaultDispatcher +
                CoroutineExceptionHandler { _, throwable ->
                    Log.e(TAG, "Unhandled application-scope coroutine error", throwable)
                },
        )

    private const val TAG = "ApplicationScope"
}
