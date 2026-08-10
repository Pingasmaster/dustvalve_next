package com.dustvalve.next.android.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BluetoothStabilityModeTest {
    @Test
    fun storageRoundTrip() {
        assertThat(BluetoothStabilityMode.fromStorage("normal")).isEqualTo(BluetoothStabilityMode.NORMAL)
        assertThat(BluetoothStabilityMode.fromStorage("extreme")).isEqualTo(BluetoothStabilityMode.EXTREME)
        assertThat(BluetoothStabilityMode.fromStorage("off")).isEqualTo(BluetoothStabilityMode.OFF)
        assertThat(BluetoothStabilityMode.fromStorage(null)).isEqualTo(BluetoothStabilityMode.OFF)
        assertThat(BluetoothStabilityMode.NORMAL.toStorage()).isEqualTo("normal")
        assertThat(BluetoothStabilityMode.EXTREME.toStorage()).isEqualTo("extreme")
    }

    @Test
    fun extremeUnlocksQualityTradeoffs() {
        assertThat(BluetoothStabilityMode.NORMAL.isExtreme).isFalse()
        assertThat(BluetoothStabilityMode.EXTREME.isExtreme).isTrue()
        assertThat(BluetoothStabilityMode.NORMAL.isEnabled).isTrue()
        assertThat(BluetoothStabilityMode.OFF.isEnabled).isFalse()
    }
}
