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
    val version: Int = SUPPORTED_VERSION,
    val offline: Boolean,
    val playlist: PlaylistSnapshot,
    val entries: List<BundleEntry> = emptyList(),
) {
    companion object {
        /**
         * Newest bundle format version this build can read. Import rejects
         * manifests with a higher [version] (produced by a newer app) instead
         * of silently mis-parsing them; bump when the format changes
         * incompatibly.
         */
        const val SUPPORTED_VERSION = 1
    }
}

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
