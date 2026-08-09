package com.dustvalve.next.android.ui.screens.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.dustvalve.next.android.player.PlaybackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/** Audio-output device list + volume writes for the player UI. */
internal class PlayerAudioController(
    appContext: Context,
    private val playbackManager: PlaybackManager,
) {
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _audioDevices = MutableStateFlow(getOutputDevices())
    val audioDevices: StateFlow<List<AudioDeviceInfo>> = _audioDevices.asStateFlow()

    private val _activeAudioDevice = MutableStateFlow<AudioDeviceInfo?>(null)
    val activeAudioDevice: StateFlow<AudioDeviceInfo?> = _activeAudioDevice.asStateFlow()

    val streamMaxVolume: Int get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val streamVolume: Int get() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            _audioDevices.value = getOutputDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            _audioDevices.value = getOutputDevices()
            val active = _activeAudioDevice.value
            if (active != null && _audioDevices.value.none { it.id == active.id }) {
                _activeAudioDevice.value = null
                playbackManager.setPreferredAudioDevice(null)
            }
        }
    }

    fun register() {
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
    }

    fun unregister() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }

    fun setAudioOutputDevice(device: AudioDeviceInfo?) {
        _activeAudioDevice.value = device
        playbackManager.setPreferredAudioDevice(device)
    }

    fun setVolume(level: Float) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVol = (level * maxVol).roundToInt().coerceIn(0, maxVol)
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        } catch (_: SecurityException) {
            // Do-Not-Disturb / volume policy can forbid the change.
        }
    }

    private fun getOutputDevices(): List<AudioDeviceInfo> = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter {
            it.type !in setOf(
                AudioDeviceInfo.TYPE_TELEPHONY,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            )
        }
        .distinctBy { it.type to it.productName?.toString().orEmpty() }
        .toList()
}
