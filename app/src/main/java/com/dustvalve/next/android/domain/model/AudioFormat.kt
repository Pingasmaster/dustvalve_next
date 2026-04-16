package com.dustvalve.next.android.domain.model

import androidx.annotation.StringRes
import com.dustvalve.next.android.R

enum class AudioFormat(
    val key: String,
    val extension: String,
    @param:StringRes val displayNameRes: Int,
    val qualityRank: Int,
) {
    FLAC("flac", "flac", R.string.audio_format_flac, 100),
    OGG_VORBIS_320("ogg-320", "ogg", R.string.audio_format_ogg_320, 85),
    MP3_320("mp3-320", "mp3", R.string.audio_format_mp3_320, 80),
    MP3_V0("mp3-v0", "mp3", R.string.audio_format_mp3_v0, 70),
    AAC("aac-hi", "m4a", R.string.audio_format_aac, 60),
    OPUS("opus", "webm", R.string.audio_format_opus, 65),
    OGG_VORBIS("vorbis", "ogg", R.string.audio_format_ogg_vorbis, 50),
    MP3_128("mp3-128", "mp3", R.string.audio_format_mp3_128, 10);

    companion object {
        fun fromKey(key: String): AudioFormat? = entries.find { it.key == key }
        val DOWNLOADABLE = listOf(FLAC, MP3_320, MP3_V0, AAC, OGG_VORBIS)
    }
}
