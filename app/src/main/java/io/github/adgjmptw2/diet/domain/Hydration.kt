package io.github.adgjmptw2.diet.domain

import kotlin.math.roundToInt

data class WaterSettings(val glassMl: Int = 250, val targetMl: Int? = null)

/** 기존 water_glass_ml과 water_target_ml 값을 그대로 읽는다. */
fun waterSettingsFromStored(
    glassMl: Int? = null, targetMl: Int? = null,
): WaterSettings = WaterSettings(
    (glassMl ?: 250).coerceIn(50, 2000),
    targetMl?.coerceIn(250, 10_000),
)

fun waterTargetMl(weightKg: Double): Int =
    ((weightKg * 30 / 250).roundToInt() * 250).coerceIn(1500, 3500)
