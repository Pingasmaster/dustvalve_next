package com.dustvalve.next.android.workflow.support

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.dustvalve.next.android.player.AudioPowerPolicy

/** No-op [AudioPowerPolicy] for PlaybackManager unit tests (both flavors). */
@OptIn(UnstableApi::class)
fun noopAudioPowerPolicy(): AudioPowerPolicy = object : AudioPowerPolicy {
    override fun apply(player: ExoPlayer) = Unit
}
