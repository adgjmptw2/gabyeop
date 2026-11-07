package io.github.adgjmptw2.diet.domain

import java.time.LocalDate
import kotlin.math.roundToInt

data class CalorieTarget(
    val bmr: Int,
    val tdee: Int,
    val target: Int,
    /** 감량분을 빼면 최소 섭취량보다 낮아서 최소값으로 올렸는지 */
    val raisedToMinimum: Boolean,
)

object Calories {
    const val DAILY_DEFICIT = 500

    fun minimum(sex: Sex): Int = if (sex == Sex.MALE) 1500 else 1200

    /** Mifflin-St Jeor 공식 */
    fun bmr(sex: Sex, weightKg: Double, heightCm: Double, age: Int): Double {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * age
        return if (sex == Sex.MALE) base + 5 else base - 161
    }

    fun target(profile: Profile, currentKg: Double, today: LocalDate, goalReached: Boolean): CalorieTarget {
        val age = today.year - profile.birthYear
        val bmr = bmr(profile.sex, currentKg, profile.heightCm, age)
        val tdee = bmr * profile.activity.factor
        val min = minimum(profile.sex)
        val wanted = if (goalReached) tdee else tdee - DAILY_DEFICIT
        val raised = wanted < min
        return CalorieTarget(
            bmr = bmr.roundToInt(),
            tdee = tdee.roundToInt(),
            target = roundTo10(if (raised) min.toDouble() else wanted),
            raisedToMinimum = raised,
        )
    }

    private fun roundTo10(v: Double): Int = (v / 10).roundToInt() * 10
}
