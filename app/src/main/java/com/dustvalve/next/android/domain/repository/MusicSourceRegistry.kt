package com.dustvalve.next.android.domain.repository

/**
 * Tiny lookup so callers can fetch a [MusicSource] by [MusicSource.id]
 * ("bandcamp", "youtube", "youtube_music") without wiring every adapter as
 * a separate Hilt dependency. Implementation is provided by the Hilt module
 * that also binds each concrete [MusicSource].
 */
interface MusicSourceRegistry {
    /** All registered sources. */
    fun all(): List<MusicSource>

    /** Looked up by [MusicSource.id]; null if no such source is registered. */
    operator fun get(id: String): MusicSource?
}
