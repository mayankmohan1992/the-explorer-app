package com.explorer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Custom Neon & Space colors
val DeepSpaceBackground = Color(0xFF0B0D19)
val GlassSurface = Color(0x66161C33) // Translucent deep card background
val GlassBorderCyan = Color(0x9900F2FE)
val GlassBorderPurple = Color(0x997F00FF)
val GlassBorderPink = Color(0x99FF0844)

val NeonCyan = Color(0xFF00F2FE)
val NeonPurple = Color(0xFF7F00FF)
val NeonPink = Color(0xFFFF0844)

val TextPrimary = Color(0xFFF0F2F8)
val TextSecondary = Color(0xFF909BB0)

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = NeonPurple,
    tertiary = NeonPink,
    background = DeepSpaceBackground,
    surface = GlassSurface,
    onPrimary = DeepSpaceBackground,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = NeonCyan,
    secondary = NeonPurple,
    tertiary = NeonPink,
    background = DeepSpaceBackground, // Force dark mode/deep space default
    surface = GlassSurface,
    onPrimary = DeepSpaceBackground,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
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
