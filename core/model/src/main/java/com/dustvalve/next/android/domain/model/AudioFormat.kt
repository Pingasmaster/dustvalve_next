package com.dustvalve.next.android.domain.model

enum class AudioFormat(val key: String, val extension: String, val qualityRank: Int) {
    FLAC("flac", "flac", 100),
    OGG_VORBIS_320("ogg-320", "ogg", 85),
    MP3_320("mp3-320", "mp3", 80),
    MP3_V0("mp3-v0", "mp3", 70),
    AAC("aac-hi", "m4a", 60),
    OPUS("opus", "webm", 65),
    OGG_VORBIS("vorbis", "ogg", 50),
    MP3_128("mp3-128", "mp3", 10),
    ;

    companion object {
        fun fromKey(key: String): AudioFormat? = entries.find { it.key == key }
        val DOWNLOADABLE = listOf(FLAC, MP3_320, MP3_V0, AAC, OGG_VORBIS)
    }
}
