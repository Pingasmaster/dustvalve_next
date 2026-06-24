package com.dustvalve.next.android.data.local.scanner

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dustvalve.next.android.domain.repository.LocalMusicRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class LocalMusicSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val localMusicRepository: LocalMusicRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        localMusicRepository.scan()
        Result.success()
    } catch (ce: kotlin.coroutines.cancellation.CancellationException) {
        // Cancellation is the worker's normal stop signal; surface the
        // run-attempt breadcrumb before rethrowing so the field log shows
        // when WorkManager bailed us out. Full WorkInfo.stopReason is
        // only available via WorkManager, not the worker itself.
        Log.w("LocalMusicSync", "stopped (attempt=$runAttemptCount)")
        throw ce
    } catch (e: Exception) {
        if (e is kotlin.coroutines.cancellation.CancellationException) throw e
        Result.retry()
    }
}
