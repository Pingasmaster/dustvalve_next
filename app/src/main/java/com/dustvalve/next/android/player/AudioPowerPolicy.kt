package com.dustvalve.next.android.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Flavor-specific ExoPlayer power policy. Future enables audio offload;
 * compat keeps it disabled. [disableOffload] is the runtime fallback when
 * offload leaves position frozen at 0:00 with no PlaybackException.
 */
@UnstableApi
interface AudioPowerPolicy {
    fun apply(player: ExoPlayer)

    /** Disable audio offload after a frozen-position recovery. No-op when unused. */
    fun disableOffload(player: ExoPlayer) {}
}
