package com.orion.helios.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SolarGold,
    secondary = CyanAccent,
    background = DeepCharcoal,
    surface = CardSurface,
    surfaceVariant = CardSurfaceVariant,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = LightText,
    onSurface = LightText,
    onSurfaceVariant = DimText
)

@Composable
fun HeliosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}

