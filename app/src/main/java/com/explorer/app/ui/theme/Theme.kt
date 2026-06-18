package com.explorer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// OnePlus OxygenOS 16 Color Palette
val OxygenOSDarkBackground = Color(0xFF08090E)
val OxygenOSDarkSurface = Color(0xFF16171E)
val OxygenOSLightBackground = Color(0xFFF7F9FC)
val OxygenOSLightSurface = Color(0xFFFFFFFF)

// Primary Accent & Brand colors
val OxygenOSBlue = Color(0xFF0B86F4)       // System toggle blue
val OnePlusRed = Color(0xFFEB0029)         // OnePlus Brand Red
val OxygenOSGray = Color(0xFF8E8E93)       // Neutral grey

// Text colors
val DarkTextPrimary = Color(0xFFF5F5F7)
val DarkTextSecondary = Color(0xFF86868B)
val LightTextPrimary = Color(0xFF1D1D1F)
val LightTextSecondary = Color(0xFF6E6E73)

// Aliases for compatibility with existing files, mapped to OxygenOS colors
val DeepSpaceBackground = OxygenOSDarkBackground
val GlassSurface = Color(0x1A8E8E93)      // Smooth translucent container
val GlassBorderCyan = Color(0x330B86F4)
val GlassBorderPurple = Color(0x228E8E93)
val GlassBorderPink = Color(0x33EB0029)

val NeonCyan = OxygenOSBlue
val NeonPurple = OxygenOSGray
val NeonPink = OnePlusRed

val TextPrimary = DarkTextPrimary
val TextSecondary = DarkTextSecondary

private val DarkColorScheme = darkColorScheme(
    primary = OxygenOSBlue,
    secondary = OnePlusRed,
    tertiary = OxygenOSGray,
    background = OxygenOSDarkBackground,
    surface = OxygenOSDarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = OxygenOSBlue,
    secondary = OnePlusRed,
    tertiary = OxygenOSGray,
    background = OxygenOSLightBackground,
    surface = OxygenOSLightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary
)

@Composable
fun TheExplorerAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
