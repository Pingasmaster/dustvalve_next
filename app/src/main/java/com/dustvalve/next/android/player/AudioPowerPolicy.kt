package com.dustvalve.next.android.player

import android.media.AudioDeviceInfo
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Flavor-specific ExoPlayer power policy. Future enables audio offload on
 * speaker/wired; compat keeps it disabled. [disableOffload] is the runtime
 * fallback when offload leaves position frozen at 0:00 with no
 * PlaybackException. [onPreferredAudioDeviceChanged] lets future turn offload
 * off for Bluetooth A2DP/LDAC routes.
 */
@UnstableApi
interface AudioPowerPolicy {
    fun apply(player: ExoPlayer)

    /** Disable audio offload after a frozen-position recovery. No-op when unused. */
    fun disableOffload(player: ExoPlayer) {}

    /**
     * Preferred output changed via the in-app device picker. Default no-op;
     * future uses this (plus device callbacks) to keep offload off on BT.
     */
    fun onPreferredAudioDeviceChanged(player: ExoPlayer, device: AudioDeviceInfo?) {}
}
