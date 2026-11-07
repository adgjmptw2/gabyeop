package io.github.adgjmptw2.diet.ui.meals

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

internal fun Double.mealNumber(): String = NumberFormat.getNumberInstance(Locale.KOREA).apply {
    maximumFractionDigits = 1
}.format(this)

internal fun Double.amountText(): String = BigDecimal.valueOf(this)
    .setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

internal fun mealNumberInput(text: String): String = text
    .filter { it in '0'..'9' || it == '.' || it == ',' }.take(10)

internal fun parseMealAmount(text: String): Double? = text.replace(',', '.').toDoubleOrNull()
    ?.takeIf { it.isFinite() && it > 0.0 && it <= 100_000.0 }

internal fun parseMealKcal(text: String): Double? = text.replace(',', '.').toDoubleOrNull()
    ?.takeIf { it.isFinite() && it >= 0.0 && it <= 100_000.0 }
