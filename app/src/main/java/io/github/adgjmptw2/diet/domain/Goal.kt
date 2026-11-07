package io.github.adgjmptw2.diet.domain

import kotlin.math.roundToLong

data class GoalProgress(
    val currentKg: Double,
    val lostKg: Double,
    val remainingKg: Double,
    /** 0~100. 목표에 닿기 전에는 100이 나오지 않도록 내림. */
    val percent: Int,
    val reached: Boolean,
)

object Goal {
    fun progress(startKg: Double, targetKg: Double, currentKg: Double): GoalProgress {
        val start = startKg.toTenths()
        val target = targetKg.toTenths()
        val current = currentKg.toTenths()
        val total = start - target
        val lost = start - current
        val reached = current <= target
        val percent = when {
            reached -> 100
            total <= 0L -> 0
            else -> (lost * 100 / total).coerceIn(0L, 99L).toInt()
        }
        return GoalProgress(
            currentKg = currentKg,
            lostKg = lost / 10.0,
            remainingKg = (current - target).coerceAtLeast(0L) / 10.0,
            percent = percent,
            reached = reached,
        )
    }

    private fun Double.toTenths(): Long = (this * 10).roundToLong()
}
