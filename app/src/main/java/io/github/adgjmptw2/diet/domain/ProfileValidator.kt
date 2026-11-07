package io.github.adgjmptw2.diet.domain

import java.time.LocalDate
import kotlin.math.roundToInt

enum class Field { SEX, BIRTH_YEAR, HEIGHT, START_WEIGHT, TARGET_WEIGHT, ACTIVITY }

data class ProfileInput(
    val sex: Sex? = null,
    val birthYear: String = "",
    val heightCm: String = "",
    val startWeightKg: String = "",
    val targetWeightKg: String = "",
    val activity: ActivityLevel? = null,
)

data class ValidProfile(
    val sex: Sex,
    val birthYear: Int,
    val heightCm: Double,
    val startWeightKg: Double,
    val targetWeightKg: Double,
    val activity: ActivityLevel,
)

data class ValidationResult(val errors: Map<Field, String>, val value: ValidProfile?)

object WeightRules {
    const val MIN_KG = 30.0
    const val MAX_KG = 300.0

    /** "72,45" 같은 입력도 받고 소수 첫째 자리로 맞춤. 범위 밖이면 null */
    fun parse(text: String): Double? {
        val v = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (!v.isFinite() || v !in MIN_KG..MAX_KG) return null
        return (v * 10).roundToInt() / 10.0
    }
}

object ProfileValidator {
    private const val MIN_TARGET_BMI = 17.0

    fun validate(input: ProfileInput, today: LocalDate): ValidationResult {
        val errors = mutableMapOf<Field, String>()

        if (input.sex == null) errors[Field.SEX] = "성별을 골라 주세요"
        if (input.activity == null) errors[Field.ACTIVITY] = "활동량을 골라 주세요"

        val year = input.birthYear.trim().toIntOrNull()
        if (year == null || year !in (today.year - 100)..(today.year - 14)) {
            errors[Field.BIRTH_YEAR] = "출생 연도를 확인해 주세요"
        }

        val height = input.heightCm.trim().replace(',', '.').toDoubleOrNull()
        if (height == null || !height.isFinite() || height !in 100.0..250.0) errors[Field.HEIGHT] = "키를 확인해 주세요 (cm)"

        val start = WeightRules.parse(input.startWeightKg)
        if (start == null) errors[Field.START_WEIGHT] = "체중을 확인해 주세요 (kg)"

        val target = WeightRules.parse(input.targetWeightKg)
        when {
            target == null -> errors[Field.TARGET_WEIGHT] = "체중을 확인해 주세요 (kg)"
            start != null && target >= start -> errors[Field.TARGET_WEIGHT] = "시작 체중보다 낮게 정해 주세요"
            height != null && Field.HEIGHT !in errors && bmi(target, height) < MIN_TARGET_BMI ->
                errors[Field.TARGET_WEIGHT] = "목표 체중이 너무 낮아요"
        }

        if (errors.isNotEmpty()) return ValidationResult(errors, null)
        return ValidationResult(
            emptyMap(),
            ValidProfile(input.sex!!, year!!, height!!, start!!, target!!, input.activity!!),
        )
    }

    fun bmi(weightKg: Double, heightCm: Double): Double {
        val m = heightCm / 100
        return weightKg / (m * m)
    }
}
