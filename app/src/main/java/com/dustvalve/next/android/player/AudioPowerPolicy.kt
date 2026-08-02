package com.dustvalve.next.android.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@UnstableApi
fun interface AudioPowerPolicy {
    fun apply(player: ExoPlayer)
}
