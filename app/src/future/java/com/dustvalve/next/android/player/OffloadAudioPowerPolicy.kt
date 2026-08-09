package com.dustvalve.next.android.player

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
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
 * buggy OEM HALs. When that signature is observed while playing, this policy
 * disables offload for the process and restarts the current item.
 */
@OptIn(UnstableApi::class)
@Singleton
class OffloadAudioPowerPolicy @Inject constructor() : AudioPowerPolicy {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var offloadDisabled = false

    private var watchRunnable: Runnable? = null
    private var frozenMs = 0L

    override fun apply(player: ExoPlayer) {
        applyOffloadMode(player, enabled = !offloadDisabled)
        // Watch for the stuck-at-0:00 signature and fall back at runtime.
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (offloadDisabled) return
                    if (isPlaying) startFrozenWatch(player) else stopFrozenWatch()
                }
            },
        )
    }

    override fun disableOffload(player: ExoPlayer) {
        offloadDisabled = true
        stopFrozenWatch()
        applyOffloadMode(player, enabled = false)
    }

    private fun applyOffloadMode(player: ExoPlayer, enabled: Boolean) {
        val mode = if (enabled) {
            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
        } else {
            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
        }
        val offloadPrefs = AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(mode)
            .setIsGaplessSupportRequired(true)
            .setIsSpeedChangeSupportRequired(false)
            .build()
        val applyPrefs = Runnable {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(offloadPrefs)
                .build()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyPrefs.run()
        } else {
            mainHandler.post(applyPrefs)
        }
    }

    private fun startFrozenWatch(player: ExoPlayer) {
        stopFrozenWatch()
        frozenMs = 0L
        val runnable = object : Runnable {
            override fun run() {
                if (offloadDisabled || !player.isPlaying) return
                val stuckAtZero = player.playbackState == Player.STATE_READY &&
                    player.currentPosition <= 0L
                if (stuckAtZero) {
                    frozenMs += POLL_MS
                    if (frozenMs >= FROZEN_THRESHOLD_MS) {
                        Log.w(TAG, "Disabling audio offload after frozen position at 0:00")
                        disableOffload(player)
                        // Restart the current item so decoding resumes without offload.
                        val item = player.currentMediaItem
                        if (item != null) {
                            player.setMediaItem(item, /* startPositionMs = */ 0L)
                            player.prepare()
                            player.play()
                        }
                        return
                    }
                } else {
                    frozenMs = 0L
                }
                mainHandler.postDelayed(this, POLL_MS)
            }
        }
        watchRunnable = runnable
        mainHandler.postDelayed(runnable, POLL_MS)
    }

    private fun stopFrozenWatch() {
        watchRunnable?.let { mainHandler.removeCallbacks(it) }
        watchRunnable = null
        frozenMs = 0L
    }

    private companion object {
        private const val TAG = "OffloadAudio"
        private const val POLL_MS = 500L
        private const val FROZEN_THRESHOLD_MS = 3_000L
    }
}
