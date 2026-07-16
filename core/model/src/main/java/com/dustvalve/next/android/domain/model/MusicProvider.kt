package com.dustvalve.next.android.domain.model

enum class MusicProvider(val key: String, val label: String, val alwaysEnabled: Boolean = false) {
    LOCAL("local", "Local", alwaysEnabled = true),
    BANDCAMP("bandcamp", "Bandcamp"),
    YOUTUBE("youtube", "YouTube"),
}
