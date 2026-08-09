package com.dustvalve.next.android.domain.model

/**
 * Cheap stream availability hint, mostly for SoundCloud chart/track JSON
 * that lists media.transcodings before any resolve call.
 *
 * - [UNKNOWN]: no media block (or non-SoundCloud); resolve on demand
 * - [DOWNLOADABLE]: plain progressive present (file download OK)
 * - [STREAM_ONLY]: plain HLS only (playable; not a progressive file download)
 * - [BLOCKED]: encrypted/Go+ DRM, or ghost plain URLs that only exist
 *   alongside encrypted (anonymous resolve 404s)
 */
enum class StreamPolicy {
    UNKNOWN,
    DOWNLOADABLE,
    STREAM_ONLY,
    BLOCKED,
}
