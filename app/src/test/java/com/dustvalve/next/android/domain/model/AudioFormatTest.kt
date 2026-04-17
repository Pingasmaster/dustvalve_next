package com.dustvalve.next.android.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioFormatTest {

    @Test fun `fromKey roundtrip for all formats`() {
        for (f in AudioFormat.entries) {
            assertThat(AudioFormat.fromKey(f.key)).isEqualTo(f)
        }
    }

    @Test fun `fromKey unknown returns null`() {
        assertThat(AudioFormat.fromKey("nonexistent")).isNull()
        assertThat(AudioFormat.fromKey("")).isNull()
    }

    @Test fun `keys are distinct`() {
        val keys = AudioFormat.entries.map { it.key }
        assertThat(keys).containsNoDuplicates()
    }

    @Test fun `FLAC has the highest quality rank`() {
        val maxRank = AudioFormat.entries.maxOf { it.qualityRank }
        assertThat(AudioFormat.FLAC.qualityRank).isEqualTo(maxRank)
    }

    @Test fun `MP3_128 is the lowest rank`() {
        val minRank = AudioFormat.entries.minOf { it.qualityRank }
        assertThat(AudioFormat.MP3_128.qualityRank).isEqualTo(minRank)
    }

    @Test fun `DOWNLOADABLE covers expected formats`() {
        assertThat(AudioFormat.DOWNLOADABLE).containsExactly(
            AudioFormat.FLAC,
            AudioFormat.MP3_320,
            AudioFormat.MP3_V0,
            AudioFormat.AAC,
            AudioFormat.OGG_VORBIS,
        ).inOrder()
    }

    @Test fun `extension matches format`() {
        assertThat(AudioFormat.FLAC.extension).isEqualTo("flac")
        assertThat(AudioFormat.MP3_320.extension).isEqualTo("mp3")
        assertThat(AudioFormat.AAC.extension).isEqualTo("m4a")
        assertThat(AudioFormat.OPUS.extension).isEqualTo("webm")
    }
}
