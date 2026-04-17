package com.dustvalve.next.android.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackSourceTest {

    @Test fun `fromKey roundtrip for all sources`() {
        for (src in TrackSource.entries) {
            assertThat(TrackSource.fromKey(src.key)).isEqualTo(src)
        }
    }

    @Test fun `fromKey unknown defaults to BANDCAMP`() {
        assertThat(TrackSource.fromKey("nonexistent")).isEqualTo(TrackSource.BANDCAMP)
        assertThat(TrackSource.fromKey("")).isEqualTo(TrackSource.BANDCAMP)
    }

    @Test fun `keys are distinct`() {
        val keys = TrackSource.entries.map { it.key }
        assertThat(keys).containsNoDuplicates()
    }
}
