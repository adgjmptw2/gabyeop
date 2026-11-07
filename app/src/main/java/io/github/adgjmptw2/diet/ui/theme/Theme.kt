package io.github.adgjmptw2.diet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary = Color(0xFF2E7D5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1EFE7),
    onPrimaryContainer = Color(0xFF174D35),
    secondary = Color(0xFF56635C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9EEE9),
    onSecondaryContainer = Color(0xFF263C2F),
    tertiary = Color(0xFFA65335),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF9E5DB),
    onTertiaryContainer = Color(0xFF6D321E),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFCE5E1),
    onErrorContainer = Color(0xFF7D201A),
    background = Color(0xFFF3F4F3),
    onBackground = Color(0xFF1C211E),
    surface = Color.White,
    onSurface = Color(0xFF1C211E),
    onSurfaceVariant = Color(0xFF606963),
    outline = Color(0xFF7B847E),
    outlineVariant = Color(0xFFDFE4DF),
    surfaceDim = Color(0xFFE4E7E4),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FAF9),
    surfaceContainer = Color(0xFFECEFEC),
    surfaceContainerHigh = Color(0xFFE5E9E5),
    surfaceContainerHighest = Color(0xFFDAE0DA),
    inverseSurface = Color(0xFF2C322D),
    inverseOnSurface = Color(0xFFF0F3F0),
    inversePrimary = Color(0xFF98D4B2),
    surfaceTint = Color.Transparent,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF98D4B2),
    onPrimary = Color(0xFF113D29),
    primaryContainer = Color(0xFF254936),
    onPrimaryContainer = Color(0xFFBDECCF),
    secondary = Color(0xFFBAC6BD),
    onSecondary = Color(0xFF24372A),
    secondaryContainer = Color(0xFF344239),
    onSecondaryContainer = Color(0xFFDCE7DE),
    tertiary = Color(0xFFE9AA8E),
    onTertiary = Color(0xFF542B1A),
    tertiaryContainer = Color(0xFF643A27),
    onTertiaryContainer = Color(0xFFFFDBCA),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF7D2420),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101211),
    onBackground = Color(0xFFE8EDE8),
    surface = Color(0xFF1C201D),
    onSurface = Color(0xFFE8EDE8),
    onSurfaceVariant = Color(0xFFA6B0A8),
    outline = Color(0xFF6E7A71),
    outlineVariant = Color(0xFF353D37),
    surfaceDim = Color(0xFF101211),
    surfaceBright = Color(0xFF353D37),
    surfaceContainerLowest = Color(0xFF0B0D0C),
    surfaceContainerLow = Color(0xFF171A18),
    surfaceContainer = Color(0xFF222723),
    surfaceContainerHigh = Color(0xFF2B312D),
    surfaceContainerHighest = Color(0xFF353D37),
    inverseSurface = Color(0xFFE8EDE8),
    inverseOnSurface = Color(0xFF2C322D),
    inversePrimary = Color(0xFF2E7D5B),
    surfaceTint = Color.Transparent,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** 체중 증가 표시용 (감량은 primary) */
val GainColor @Composable get() = MaterialTheme.colorScheme.tertiary

@Composable
fun DietTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
