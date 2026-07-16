package com.dustvalve.next.android.di

import com.dustvalve.next.android.domain.repository.DownloadProgressReporter
import com.dustvalve.next.android.download.DownloadNotificationCenter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the app-layer notification center as the [DownloadProgressReporter]
 * the domain and data layers report progress through.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadReportingModule {

    @Binds
    @Singleton
    abstract fun bindDownloadProgressReporter(impl: DownloadNotificationCenter): DownloadProgressReporter
}
