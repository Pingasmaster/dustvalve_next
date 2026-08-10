package com.dustvalve.next.android.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enables Media3 audio offload on the future (Android 17 / minSdk 37) flavor
 * when output is speaker or wired.
 *
 * Stream compressed audio straight to the DSP, bypassing the CPU.
 * Speed change must stay off for offload to engage; the speed-control
 * path re-enables CPU decoding dynamically if the user picks != 1.0x.
 *
 * Offload is forced off whenever a music Bluetooth sink is present (A2DP /
 * BLE headset/speaker). High-rate LDAC encode already taxes the BT HAL;
 * combining it with Media3 offload is a known source of periodic dropouts
 * and the historical silent freeze at 0:00 on buggy OEM HALs.
 *
 * KNOWN RISK (kept on the future flavor only; compat uses [SafeAudioPowerPolicy]):
 * offloaded AudioTrack can still freeze at 0:00 with no PlaybackException on
 * speaker/wired. When that signature is observed while playing, this policy
 * disables offload for the process and restarts the current item.
 */
@OptIn(UnstableApi::class)
@Singleton
class OffloadAudioPowerPolicy @Inject constructor(@ApplicationContext context: Context) : AudioPowerPolicy {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var offloadDisabledPermanently = false

    @Volatile
    private var bluetoothRouteActive = false

    @Volatile
    private var preferredBluetooth = false

    private var boundPlayer: ExoPlayer? = null
    private var watchRunnable: Runnable? = null
    private var frozenMs = 0L
    private var deviceCallbackRegistered = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refreshBluetoothRoute()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refreshBluetoothRoute()
        }
    }

    override fun apply(player: ExoPlayer) {
        boundPlayer = player
        if (!deviceCallbackRegistered) {
            audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
            deviceCallbackRegistered = true
        }
        refreshBluetoothRoute()
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (offloadDisabledPermanently || !isOffloadEligible()) return
                    if (isPlaying) startFrozenWatch(player) else stopFrozenWatch()
                }
            },
        )
    }

    override fun disableOffload(player: ExoPlayer) {
        offloadDisabledPermanently = true
        stopFrozenWatch()
        applyOffloadMode(player, enabled = false)
    }

    override fun onPreferredAudioDeviceChanged(player: ExoPlayer, device: AudioDeviceInfo?) {
        preferredBluetooth = BluetoothAudioOutputs.isMusicBluetoothOutput(device)
        boundPlayer = player
        refreshBluetoothRoute()
    }

    private fun refreshBluetoothRoute() {
        val connectedBluetooth = BluetoothAudioOutputs.hasMusicBluetoothOutput(
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS),
        )
        bluetoothRouteActive = preferredBluetooth || connectedBluetooth
        val player = boundPlayer ?: return
        applyOffloadMode(player, enabled = isOffloadEligible())
        if (!isOffloadEligible()) {
            stopFrozenWatch()
        }
    }

    private fun isOffloadEligible(): Boolean = !offloadDisabledPermanently && !bluetoothRouteActive

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
                if (!isOffloadEligible() || !player.isPlaying) return
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
                            player.setMediaItem(item, 0L)
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
