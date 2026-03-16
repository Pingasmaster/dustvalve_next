package com.dustvalve.next.android.domain.model

enum class TrackSource(val key: String) {
    LOCAL("local"),
    BANDCAMP("bandcamp"),
    YOUTUBE("youtube");

    companion object {
        fun fromKey(key: String): TrackSource =
            entries.find { it.key == key } ?: BANDCAMP
    }
}
