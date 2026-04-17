package com.dustvalve.next.android.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.ui.graphics.Color

// Seed color from Dustvalve branding
val DustvalveTeal = Color(0xFF629AA9)

// ----- Light scheme teal-seeded palette -----
// Primary: teal tones derived from seed
val md_theme_light_primary = Color(0xFF006879)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFA8EDFB)
val md_theme_light_onPrimaryContainer = Color(0xFF001F26)

// Secondary: desaturated teal
val md_theme_light_secondary = Color(0xFF4A6268)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFCDE7EE)
val md_theme_light_onSecondaryContainer = Color(0xFF051F24)

// Tertiary: blue-violet accent
val md_theme_light_tertiary = Color(0xFF545C7E)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFDBE0FF)
val md_theme_light_onTertiaryContainer = Color(0xFF101937)

// Error
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onErrorContainer = Color(0xFF410002)

// ----- Dark scheme teal-seeded palette -----
// Primary: lighter teal for dark backgrounds
val md_theme_dark_primary = Color(0xFF52D7EE)
val md_theme_dark_onPrimary = Color(0xFF003640)
val md_theme_dark_primaryContainer = Color(0xFF004E5B)
val md_theme_dark_onPrimaryContainer = Color(0xFFA8EDFB)

// Secondary: desaturated lighter teal
val md_theme_dark_secondary = Color(0xFFB1CBD2)
val md_theme_dark_onSecondary = Color(0xFF1C3439)
val md_theme_dark_secondaryContainer = Color(0xFF334A50)
val md_theme_dark_onSecondaryContainer = Color(0xFFCDE7EE)

// Tertiary: light blue-violet
val md_theme_dark_tertiary = Color(0xFFBCC3EB)
val md_theme_dark_onTertiary = Color(0xFF262E4D)
val md_theme_dark_tertiaryContainer = Color(0xFF3D4565)
val md_theme_dark_onTertiaryContainer = Color(0xFFDBE0FF)

// Error
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)

// Deep teal-tinted surfaces
val md_theme_dark_background = Color(0xFF191C1D)
val md_theme_dark_onBackground = Color(0xFFE1E3E4)
val md_theme_dark_surface = Color(0xFF191C1D)
val md_theme_dark_onSurface = Color(0xFFE1E3E4)
val md_theme_dark_surfaceVariant = Color(0xFF3F484B)
val md_theme_dark_onSurfaceVariant = Color(0xFFBFC8CB)
val md_theme_dark_outline = Color(0xFF899295)
val md_theme_dark_outlineVariant = Color(0xFF3F484B)
val md_theme_dark_inverseSurface = Color(0xFFE1E3E4)
val md_theme_dark_inverseOnSurface = Color(0xFF2E3132)
val md_theme_dark_inversePrimary = Color(0xFF006879)
val md_theme_dark_surfaceTint = Color(0xFF52D7EE)
val md_theme_dark_scrim = Color(0xFF000000)

// Light scheme: use M3 Expressive as base, override with Dustvalve teal brand colors.
// This gives us optimized surface/container colors while preserving brand identity.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val LightColorScheme = expressiveLightColorScheme().copy(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
)

// Dark scheme: M3 1.5.0-alpha17 exposes expressiveLightColorScheme() only; no dark equivalent yet.
// Stick with darkColorScheme() (which already populates M3 surface-container roles) until the
// expressive dark variant ships.
val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant,
    inverseSurface = md_theme_dark_inverseSurface,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceTint = md_theme_dark_surfaceTint,
    scrim = md_theme_dark_scrim,
)
