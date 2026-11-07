package io.github.adgjmptw2.diet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF2E7D5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F0D2),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4D6357),
    secondaryContainer = Color(0xFFD0E8D9),
    onSecondaryContainer = Color(0xFF0A1F15),
    tertiary = Color(0xFFB4533A),
    background = Color(0xFFF6FBF7),
    surface = Color(0xFFF6FBF7),
    surfaceContainer = Color(0xFFEAF0EC),
    surfaceContainerHigh = Color(0xFFE4EAE6),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9BD4B6),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF115139),
    onPrimaryContainer = Color(0xFFB7F0D2),
    secondary = Color(0xFFB4CCBD),
    secondaryContainer = Color(0xFF364B40),
    onSecondaryContainer = Color(0xFFD0E8D9),
    tertiary = Color(0xFFFFB4A0),
    background = Color(0xFF0F1512),
    surface = Color(0xFF0F1512),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
)

/** 체중 증가 표시용 (감량은 primary) */
val GainColor @Composable get() = MaterialTheme.colorScheme.tertiary

@Composable
fun DietTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
