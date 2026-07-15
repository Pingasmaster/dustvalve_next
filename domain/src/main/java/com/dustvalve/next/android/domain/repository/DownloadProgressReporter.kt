package com.dustvalve.next.android.domain.repository

/**
 * Progress sink for downloads. The domain and data layers report batch and
 * per-track progress through this interface; the app layer implements it
 * (DownloadNotificationCenter) to drive the foreground notification. This
 * inversion keeps :domain/:data free of android.app.Notification, R and
 * MainActivity dependencies.
 */
interface DownloadProgressReporter {

    enum class BatchKind { ALBUM, ARTIST, PLAYLIST }

    /** Runs [block] with a batch registered so progress renders as "x of n". */
    suspend fun <T> withBatch(label: String, totalTracks: Int, kind: BatchKind, block: suspend () -> T): T

    fun trackStarted(trackId: String, title: String)

    fun trackProgress(trackId: String, bytesWritten: Long, expectedTotal: Long?)

    fun trackFinished(trackId: String, success: Boolean)
}
