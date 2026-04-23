package com.dustvalve.next.android.domain.model

import androidx.compose.runtime.Immutable

/**
 * A downloaded track that can be exported, with the format/quality metadata
 * needed by the Export Tracks UI.
 */
@Immutable
data class ExportableTrack(
    val track: Track,
    val format: AudioFormat,
    val sizeBytes: Long,
    val qualityLabel: String,
)
