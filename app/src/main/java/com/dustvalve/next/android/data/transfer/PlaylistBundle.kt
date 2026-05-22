package com.dustvalve.next.android.data.transfer

import com.dustvalve.next.android.data.storage.folder.PlaylistSnapshot
import com.dustvalve.next.android.data.storage.folder.TrackSnapshot
import kotlinx.serialization.Serializable

/**
 * Manifest for an exported `.dvplaylist` bundle (a ZIP). When [offline] is true the ZIP also
 * carries `audio/<trackId>.<ext>` and `covers/<albumId>.<ext>` referenced by each [BundleEntry];
 * when false the bundle is metadata-only and tracks re-stream from their online URLs on import.
 */
@Serializable
data class PlaylistBundleManifest(
    val version: Int = 1,
    val offline: Boolean,
    val playlist: PlaylistSnapshot,
    val entries: List<BundleEntry> = emptyList(),
)

@Serializable
data class BundleEntry(
    val track: TrackSnapshot,
    /** Relative path of the audio file inside the ZIP (offline bundles only). */
    val audioFile: String? = null,
    /** Relative path of the cover image inside the ZIP (offline bundles only). */
    val coverFile: String? = null,
    /** AudioFormat.key of the bundled audio (offline bundles only). */
    val format: String? = null,
)
