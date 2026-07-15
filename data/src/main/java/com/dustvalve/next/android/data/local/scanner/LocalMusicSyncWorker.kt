package com.dustvalve.next.android.data.local.scanner

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dustvalve.next.android.domain.repository.LocalMusicRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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
        // run-attempt breadcrumb + WorkInfo.stopReason before rethrowing so
        // the field log shows when WorkManager bailed us out and why
        // (especially STOP_REASON_TIMEOUT when a scan exceeds the expedited
        // quota — see Android 16 wake-lock enforcement guidance).
        Log.w(TAG, "stopped (attempt=$runAttemptCount)")
        logStopReason()
        throw ce
    } catch (e: Exception) {
        if (e is kotlin.coroutines.cancellation.CancellationException) throw e
        Result.retry()
    }

    /**
     * Best-effort: query our own WorkInfo and emit the platform-provided
     * stopReason. Bounded by 1 s so a stuck WorkManager doesn't block the
     * coroutine returning. No-op if WorkManager can't find us (e.g. we ran
     * before initialization completed in a fresh process).
     */
    private suspend fun logStopReason() {
        val info: WorkInfo? = withTimeoutOrNull(1_000L) {
            WorkManager.getInstance(applicationContext).getWorkInfoByIdFlow(id).first()
        }
        if (info != null) {
            Log.w(
                TAG,
                "workId=$id stopReason=${info.stopReason} state=${info.state} " +
                    "runAttemptCount=$runAttemptCount",
            )
        }
    }

    private companion object {
        const val TAG = "LocalMusicSync"
    }
}
