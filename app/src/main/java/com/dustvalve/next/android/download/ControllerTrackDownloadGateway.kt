package com.dustvalve.next.android.download

import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.TrackDownloadGateway
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes feature-layer single-track downloads through [DownloadController]
 * so they share queue durability, the foreground service, and payload
 * validation with UI-initiated downloads.
 */
@Singleton
class ControllerTrackDownloadGateway @Inject constructor(
    private val downloadController: DownloadController,
) : TrackDownloadGateway {
    override suspend fun downloadTrack(track: Track, formatOverride: AudioFormat?) {
        downloadController.downloadTrackBlocking(track, formatOverride)
    }
}
