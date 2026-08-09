package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.Track

/**
 * Single-track download entry point for features above the download
 * controller (e.g. offline playlist export). Implementations should route
 * through [com.dustvalve.next.android.download.DownloadController] so work
 * shares the process-wide queue, foreground service, and
 * [com.dustvalve.next.android.data.remote.DownloadPayloadValidator] path
 * used by the UI download buttons.
 */
interface TrackDownloadGateway {
    suspend fun downloadTrack(track: Track, formatOverride: AudioFormat? = null)
}
