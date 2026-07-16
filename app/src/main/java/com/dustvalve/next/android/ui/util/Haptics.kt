package com.dustvalve.next.android.ui.util

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

// Expressive haptic shorthands for player interactions. These map to the M3
// Expressive HapticFeedbackType constants (Compose UI maps them to platform
// constants with internal fallback on older devices, so no API-level guard
// is needed).

/** A discrete on/off toggle (shuffle, repeat, favorite, play/pause). */
fun HapticFeedback.toggle(turningOn: Boolean) =
    performHapticFeedback(if (turningOn) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

/** A light segment tick - for skipping tracks and per-step scrub/volume drag feedback. */
fun HapticFeedback.tick() = performHapticFeedback(HapticFeedbackType.SegmentTick)
