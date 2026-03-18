package com.dustvalve.next.android.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DustvalveNextTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    oledBlack: Boolean = false,
    albumSeedColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        albumSeedColor != null -> {
            rememberDynamicColorScheme(
                seedColor = albumSeedColor,
                isDark = darkTheme,
                style = PaletteStyle.TonalSpot,
            )
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val finalColorScheme = if (oledBlack && darkTheme) {
        colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF0A0A0A),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF050505),
            surfaceContainer = Color(0xFF0A0A0A),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF2A2A2A),
        )
    } else {
        colorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = finalColorScheme.animated(),
        typography = AppTypography,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(),
        content = content,
    )
}

@Composable
private fun ColorScheme.animated(): ColorScheme {
    // Spring spec matching M3E fastEffectsSpec (for color/opacity transitions)
    val spec = spring<Color>(dampingRatio = 0.9f, stiffness = 1400f)

    return copy(
        primary = animateColorAsState(primary, spec).value,
        onPrimary = animateColorAsState(onPrimary, spec).value,
        primaryContainer = animateColorAsState(primaryContainer, spec).value,
        onPrimaryContainer = animateColorAsState(onPrimaryContainer, spec).value,
        inversePrimary = animateColorAsState(inversePrimary, spec).value,
        secondary = animateColorAsState(secondary, spec).value,
        onSecondary = animateColorAsState(onSecondary, spec).value,
        secondaryContainer = animateColorAsState(secondaryContainer, spec).value,
        onSecondaryContainer = animateColorAsState(onSecondaryContainer, spec).value,
        tertiary = animateColorAsState(tertiary, spec).value,
        onTertiary = animateColorAsState(onTertiary, spec).value,
        tertiaryContainer = animateColorAsState(tertiaryContainer, spec).value,
        onTertiaryContainer = animateColorAsState(onTertiaryContainer, spec).value,
        background = animateColorAsState(background, spec).value,
        onBackground = animateColorAsState(onBackground, spec).value,
        surface = animateColorAsState(surface, spec).value,
        onSurface = animateColorAsState(onSurface, spec).value,
        surfaceVariant = animateColorAsState(surfaceVariant, spec).value,
        onSurfaceVariant = animateColorAsState(onSurfaceVariant, spec).value,
        surfaceTint = animateColorAsState(surfaceTint, spec).value,
        inverseSurface = animateColorAsState(inverseSurface, spec).value,
        inverseOnSurface = animateColorAsState(inverseOnSurface, spec).value,
        error = animateColorAsState(error, spec).value,
        onError = animateColorAsState(onError, spec).value,
        errorContainer = animateColorAsState(errorContainer, spec).value,
        onErrorContainer = animateColorAsState(onErrorContainer, spec).value,
        outline = animateColorAsState(outline, spec).value,
        outlineVariant = animateColorAsState(outlineVariant, spec).value,
        scrim = animateColorAsState(scrim, spec).value,
        surfaceBright = animateColorAsState(surfaceBright, spec).value,
        surfaceDim = animateColorAsState(surfaceDim, spec).value,
        surfaceContainer = animateColorAsState(surfaceContainer, spec).value,
        surfaceContainerHigh = animateColorAsState(surfaceContainerHigh, spec).value,
        surfaceContainerHighest = animateColorAsState(surfaceContainerHighest, spec).value,
        surfaceContainerLow = animateColorAsState(surfaceContainerLow, spec).value,
        surfaceContainerLowest = animateColorAsState(surfaceContainerLowest, spec).value,
    )
}
