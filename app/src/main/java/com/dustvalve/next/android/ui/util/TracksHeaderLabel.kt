package com.dustvalve.next.android.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.dustvalve.next.android.R

/**
 * Builds a header like "5 tracks · 23 min" for an album / playlist screen.
 *
 * - Hides the track count if [trackCount] <= 0 (renders just "Tracks").
 * - Hides the duration if [totalDurationSec] <= 0 (some sources don't expose
 *   per-track durations until the user opens the track itself).
 * - Pluralises track count and uses minute pluralization. Hours are folded in
 *   when total >= 1 h ("1 hr 5 min").
 */
@Composable
fun tracksHeaderLabel(trackCount: Int, totalDurationSec: Long): String {
    val countPart = when {
        trackCount <= 0 -> stringResource(R.string.detail_tracks_label)
        else -> pluralStringResource(R.plurals.track_count, trackCount, trackCount)
    }
    if (totalDurationSec <= 0L) return countPart
    val totalMinutes = ((totalDurationSec + 30L) / 60L).toInt()  // round to nearest minute
    val durationPart = if (totalMinutes >= 60) {
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        if (mins == 0) stringResource(R.string.detail_duration_hours_only, hours)
        else stringResource(R.string.detail_duration_hours_minutes, hours, mins)
    } else {
        // For sub-minute totals (e.g. a 30 s skit), still show "1 min" so the
        // dot-separator pattern stays uniform.
        stringResource(R.string.detail_duration_minutes, totalMinutes.coerceAtLeast(1))
    }
    return "$countPart  ·  $durationPart"
}
