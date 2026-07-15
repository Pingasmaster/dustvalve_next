package com.dustvalve.next.android.ui.util

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.MusicProvider

/**
 * UI-layer resource mappings for domain enums. Keeping android resource ids
 * out of the domain models lets :core:model stay free of the app's R class.
 */
val AudioFormat.displayNameRes: Int
    @StringRes get() = when (this) {
        AudioFormat.FLAC -> R.string.audio_format_flac
        AudioFormat.OGG_VORBIS_320 -> R.string.audio_format_ogg_320
        AudioFormat.MP3_320 -> R.string.audio_format_mp3_320
        AudioFormat.MP3_V0 -> R.string.audio_format_mp3_v0
        AudioFormat.AAC -> R.string.audio_format_aac
        AudioFormat.OPUS -> R.string.audio_format_opus
        AudioFormat.OGG_VORBIS -> R.string.audio_format_ogg_vorbis
        AudioFormat.MP3_128 -> R.string.audio_format_mp3_128
    }

val MusicProvider.iconRes: Int
    @DrawableRes get() = when (this) {
        MusicProvider.LOCAL -> R.drawable.ic_phone_android
        MusicProvider.BANDCAMP -> R.drawable.ic_cloud
        MusicProvider.YOUTUBE -> R.drawable.ic_play_circle
    }
