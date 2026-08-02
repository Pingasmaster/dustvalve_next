package com.dustvalve.next.android.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op audio power policy for the compat flavor (Android 8-16 / minSdk 26).
 *
 * Audio offload is intentionally disabled here: offloaded AudioTrack is a
 * historical source of silent on-device playback failure on buggy OEM HALs
 * (no audio, position frozen at 0:00, no PlaybackException). The v0.5.x
 * "stuck at 0:00" reports on legacy devices traced to offload; future keeps
 * offload via [OffloadAudioPowerPolicy], compat does not.
 */
@OptIn(UnstableApi::class)
@Singleton
class SafeAudioPowerPolicy @Inject constructor() : AudioPowerPolicy {
    override fun apply(player: ExoPlayer) {
        // Intentionally empty - see class KDoc.
    }
}
