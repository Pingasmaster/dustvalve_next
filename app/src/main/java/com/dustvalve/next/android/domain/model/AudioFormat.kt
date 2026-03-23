package com.dustvalve.next.android.domain.model

enum class AudioFormat(
    val key: String,
    val extension: String,
    val displayName: String,
    val qualityRank: Int,
) {
    FLAC("flac", "flac", "FLAC (Lossless)", 100),
    MP3_320("mp3-320", "mp3", "MP3 320 kbps", 80),
    MP3_V0("mp3-v0", "mp3", "MP3 V0 (VBR)", 70),
    AAC("aac-hi", "m4a", "AAC", 60),
    OPUS("opus", "webm", "Opus", 65),
    OGG_VORBIS("vorbis", "ogg", "Ogg Vorbis", 50),
    MP3_128("mp3-128", "mp3", "MP3 128 kbps", 10);

    companion object {
        fun fromKey(key: String): AudioFormat? = entries.find { it.key == key }
        val DOWNLOADABLE = listOf(FLAC, MP3_320, MP3_V0, AAC, OGG_VORBIS)
    }
}
