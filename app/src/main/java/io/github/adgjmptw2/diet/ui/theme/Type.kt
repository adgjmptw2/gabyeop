package io.github.adgjmptw2.diet.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.adgjmptw2.diet.R

private val AppSans = FontFamily(
    Font(R.font.app_sans_regular, FontWeight.Normal),
    Font(R.font.app_sans_semibold, FontWeight.SemiBold),
    Font(R.font.app_sans_bold, FontWeight.Bold),
)

private fun appText(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = AppSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
)

internal val AppTypography = Typography(
    displayLarge = appText(56, 64, FontWeight.Bold),
    displayMedium = appText(48, 56, FontWeight.Bold),
    displaySmall = appText(40, 48, FontWeight.Bold),
    // Large title → headlineLarge, title 1/2/3 → headlineMedium/Small, titleLarge.
    headlineLarge = appText(34, 41, FontWeight.Bold),
    headlineMedium = appText(28, 34, FontWeight.Bold),
    headlineSmall = appText(22, 28, FontWeight.Bold),
    titleLarge = appText(20, 26, FontWeight.SemiBold),
    titleMedium = appText(17, 24, FontWeight.SemiBold),
    titleSmall = appText(15, 21, FontWeight.SemiBold),
    bodyLarge = appText(17, 25),
    bodyMedium = appText(16, 24),
    bodySmall = appText(13, 19),
    labelLarge = appText(17, 24, FontWeight.SemiBold),
    labelMedium = appText(13, 18, FontWeight.SemiBold),
    labelSmall = appText(12, 16),
)

internal fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")
