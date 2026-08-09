package com.dustvalve.next.android.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dustvalve.next.android.R

@Composable
internal fun audioDeviceDisplayName(device: android.media.AudioDeviceInfo): String {
    val productName = device.productName?.toString()?.takeIf { it.isNotBlank() }
    return productName ?: when (device.type) {
        android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> stringResource(R.string.player_audio_device_speaker)
        android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> stringResource(R.string.player_audio_device_earpiece)
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> stringResource(R.string.player_audio_device_wired_headset)
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> stringResource(R.string.player_audio_device_wired_headphones)
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> stringResource(R.string.player_audio_device_bluetooth)
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> stringResource(R.string.player_audio_device_bluetooth_sco)
        android.media.AudioDeviceInfo.TYPE_USB_DEVICE -> stringResource(R.string.player_audio_device_usb)
        android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY -> stringResource(R.string.player_audio_device_usb_accessory)
        android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> stringResource(R.string.player_audio_device_usb_headset)
        android.media.AudioDeviceInfo.TYPE_HDMI -> stringResource(R.string.player_audio_device_hdmi)
        android.media.AudioDeviceInfo.TYPE_DOCK -> stringResource(R.string.player_audio_device_dock)
        android.media.AudioDeviceInfo.TYPE_AUX_LINE -> stringResource(R.string.player_audio_device_aux)
        android.media.AudioDeviceInfo.TYPE_BLE_HEADSET -> stringResource(R.string.player_audio_device_ble_headset)
        android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER -> stringResource(R.string.player_audio_device_ble_speaker)
        else -> stringResource(R.string.player_audio_device_unknown)
    }
}

@androidx.annotation.DrawableRes
internal fun audioDeviceIcon(device: android.media.AudioDeviceInfo): Int = when (device.type) {
    android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
    android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER,
    -> R.drawable.ic_bluetooth

    android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
    android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
    -> R.drawable.ic_headphones

    else -> R.drawable.ic_speaker
}
