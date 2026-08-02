package com.dustvalve.next.android.player

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enables Media3 audio offload on the future (Android 17 / minSdk 37) flavor.
 *
 * Stream compressed audio straight to the DSP, bypassing the CPU.
 * Speed change must stay off for offload to engage; the speed-control
 * path re-enables CPU decoding dynamically if the user picks != 1.0x.
 *
 * KNOWN RISK (kept on the future flavor only; compat uses [SafeAudioPowerPolicy]):
 * offloaded AudioTrack is a historical source of silent on-device playback
 * failure - no audio, position frozen at 0:00, no PlaybackException - on
 * buggy OEM HALs. Neither the JVM test tier nor emulators can see it. If
 * Android 17 devices ever report the same signature, revisit this policy.
 */
@OptIn(UnstableApi::class)
@Singleton
class OffloadAudioPowerPolicy @Inject constructor() : AudioPowerPolicy {

    override fun apply(player: ExoPlayer) {
        val offloadPrefs = AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
            .setIsGaplessSupportRequired(true)
            .setIsSpeedChangeSupportRequired(false)
            .build()
        // Player accessors must run on the application looper (main).
        // When built off-main, apply via a fire-and-forget post - never a
        // blocking wait (that deadlocked the old construction path).
        val applyOffloadPrefs = Runnable {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(offloadPrefs)
                .build()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyOffloadPrefs.run()
        } else {
            Handler(Looper.getMainLooper()).post(applyOffloadPrefs)
        }
    }
}
