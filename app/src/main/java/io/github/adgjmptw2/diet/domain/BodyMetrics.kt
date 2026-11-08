package io.github.adgjmptw2.diet.domain

import kotlin.math.roundToInt

object BodyMetrics {
    fun bmi(weightKg: Double, heightCm: Double): Double = ProfileValidator.bmi(weightKg, heightCm)
    fun category(bmi: Double): String = when {
        bmi < 18.5 -> "저체중"
        bmi < 23 -> "정상"
        bmi < 25 -> "비만 전단계"
        bmi < 30 -> "1단계 비만"
        bmi < 35 -> "2단계 비만"
        else -> "3단계 비만"
    }
    fun estimateBodyFat(bmi: Double, age: Int, sex: Sex): Double =
        1.2 * bmi + 0.23 * age - (if (sex == Sex.MALE) 10.8 else 0.0) - 5.4
    fun parseBodyFat(text: String): Double? {
        val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return value.takeIf { it.isFinite() && it in 3.0..70.0 }?.let { (it * 10).roundToInt() / 10.0 }
    }
}
