package com.dustvalve.next.android.player

import android.media.AudioDeviceInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class BluetoothAudioOutputsTest {

    @Test
    fun musicBluetoothTypes_includeA2dpAndBleHeadset() {
        assertThat(BluetoothAudioOutputs.isMusicBluetoothOutput(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
            .isTrue()
        assertThat(BluetoothAudioOutputs.isMusicBluetoothOutput(AudioDeviceInfo.TYPE_BLE_HEADSET))
            .isTrue()
        assertThat(BluetoothAudioOutputs.isMusicBluetoothOutput(AudioDeviceInfo.TYPE_BLE_SPEAKER))
            .isTrue()
    }

    @Test
    fun nonBluetoothOutputs_areNotMusicBluetooth() {
        assertThat(BluetoothAudioOutputs.isMusicBluetoothOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
            .isFalse()
        assertThat(BluetoothAudioOutputs.isMusicBluetoothOutput(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
            .isFalse()
        assertThat(BluetoothAudioOutputs.isMusicBluetoothOutput(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
            .isFalse()
        assertThat(BluetoothAudioOutputs.isMusicBluetoothOutput(null as AudioDeviceInfo?)).isFalse()
    }

    @Test
    fun hiFiPcmBuffer_isAtLeastTwoSeconds() {
        assertThat(HiFiAudioRenderersFactory.TARGET_PCM_BUFFER_DURATION_US)
            .isAtLeast(2_000_000)
    }
}
