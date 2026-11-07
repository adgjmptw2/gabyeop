package io.github.adgjmptw2.diet.domain

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
        val total = startKg - targetKg
        val lost = startKg - currentKg
        val reached = currentKg <= targetKg
        val ratio = when {
            reached -> 1.0
            total <= 0.0 -> 0.0
            else -> (lost / total).coerceIn(0.0, 1.0)
        }
        return GoalProgress(
            currentKg = currentKg,
            lostKg = lost,
            remainingKg = (currentKg - targetKg).coerceAtLeast(0.0),
            percent = (ratio * 100).toInt(),
            reached = reached,
        )
    }
}
