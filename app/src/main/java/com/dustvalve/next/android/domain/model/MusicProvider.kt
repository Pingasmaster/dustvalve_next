package com.dustvalve.next.android.domain.model

import com.dustvalve.next.android.R

enum class MusicProvider(
    val key: String,
    val label: String,
    val iconRes: Int,
    val alwaysEnabled: Boolean = false,
) {
    LOCAL("local", "Local", R.drawable.ic_phone_android, alwaysEnabled = true),
    BANDCAMP("bandcamp", "Bandcamp", R.drawable.ic_cloud),
    YOUTUBE("youtube", "YouTube", R.drawable.ic_play_circle),
}
