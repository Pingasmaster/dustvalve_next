package com.dustvalve.next.android.ui.screens.player

/**
 * Shared-element keys for the mini-player <-> full-player container transform.
 *
 * The collapsed mini bar and the expanded full player are rendered as the two
 * states of a single [androidx.compose.animation.core.Transition] driven by a
 * [androidx.compose.animation.core.SeekableTransitionState], wrapped in a
 * [androidx.compose.animation.SharedTransitionLayout]. These keys tie the two
 * states together so the surface grows from the bar's bounds (a true container
 * transform) and the album art stays continuous across the morph.
 *
 * Both [MiniPlayer] and [FullPlayer] must use the SAME constants for the morph
 * to match.
 */
internal const val PLAYER_SURFACE_KEY = "player_surface"
internal const val PLAYER_ART_KEY = "player_art"

/** Downward fling velocity (px/s) past which a flick commits regardless of distance. */
internal const val PLAYER_FLING_VELOCITY = 1200f
