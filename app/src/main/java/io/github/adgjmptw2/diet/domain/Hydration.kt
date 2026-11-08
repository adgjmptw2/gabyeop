package io.github.adgjmptw2.diet.domain

import kotlin.math.roundToInt

data class WaterSettings(val glassMl: Int = 250, val targetMl: Int? = null)

fun waterTargetMl(weightKg: Double): Int =
    ((weightKg * 30 / 250).roundToInt() * 250).coerceIn(1500, 3500)
