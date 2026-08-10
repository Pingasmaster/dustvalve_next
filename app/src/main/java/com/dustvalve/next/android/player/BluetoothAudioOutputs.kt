package com.dustvalve.next.android.player

import android.media.AudioDeviceInfo

/**
 * Music-relevant Bluetooth output device types.
 *
 * Media3 audio offload (DSP decode) and high-rate A2DP/LDAC paths do not mix
 * reliably: offloaded tracks on Bluetooth commonly glitch or freeze. Use this
 * to keep offload on speaker/wired only.
 */
object BluetoothAudioOutputs {

    fun isMusicBluetoothOutput(type: Int): Boolean = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        AudioDeviceInfo.TYPE_HEARING_AID,
        -> true

        else -> false
    }

    fun isMusicBluetoothOutput(device: AudioDeviceInfo?): Boolean = device != null && isMusicBluetoothOutput(device.type)

    fun hasMusicBluetoothOutput(devices: Array<AudioDeviceInfo>): Boolean = devices.any { it.isSink && isMusicBluetoothOutput(it.type) }
}
