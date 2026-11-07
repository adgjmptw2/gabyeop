package io.github.adgjmptw2.diet.domain

import java.time.LocalDate

data class BadgeState(
    val id: String,
    val name: String,
    val description: String,
    val earnedOn: LocalDate?,
    val progress: Double,
)

fun badges(
    profile: Profile,
    weights: List<WeightEntry>,
    mealDays: List<LocalDate>,
    today: LocalDate,
): List<BadgeState> {
    val entries = weights.filter { it.date <= today }.distinctBy { it.date }.sortedBy { it.date }
    val days = entries.map { it.date }
    val meals = mealDays.filter { it <= today }.distinct().sorted()
    val bestLoss = entries.maxOfOrNull { profile.startWeightKg - it.weightKg }?.coerceAtLeast(0.0) ?: 0.0
    val goalLoss = profile.startWeightKg - profile.targetWeightKg

    fun lossBadge(id: String, name: String, description: String, loss: Double): BadgeState {
        // 목표 절반은 감량 목표가 있을 때만 계산한다.
        if (loss <= 0.0) return BadgeState(id, name, description, null, 0.0)
        val earned = entries.firstOrNull { profile.startWeightKg - it.weightKg >= loss - 1e-9 }?.date
        return BadgeState(id, name, description, earned, if (earned != null) 1.0 else (bestLoss / loss).coerceIn(0.0, 1.0))
    }

    fun streakBadge(length: Int, id: String): BadgeState {
        var streak = 0
        var previous: LocalDate? = null
        var earned: LocalDate? = null
        for (day in days) {
            streak = if (previous?.plusDays(1) == day) streak + 1 else 1
            if (streak >= length && earned == null) earned = day
            previous = day
        }
        // 어제까지 이어진 기록은 오늘 기록할 수 있으므로 진행 중으로 본다.
        val current = if (days.lastOrNull()?.let { it >= today.minusDays(1) } == true) streak else 0
        return BadgeState(
            id, "${length}일 연속 기록", "체중을 ${length}일 연속 기록",
            earned, if (earned != null) 1.0 else (current.toDouble() / length).coerceIn(0.0, 1.0),
        )
    }

    val reachedOn = entries.firstOrNull { it.weightKg <= profile.targetWeightKg }?.date
    return listOf(
        BadgeState("first_record", "첫 기록", "첫 체중 기록", days.firstOrNull(), if (days.isEmpty()) 0.0 else 1.0),
        streakBadge(7, "streak_7"),
        streakBadge(30, "streak_30"),
        lossBadge("loss_1kg", "첫 1kg 감량", "시작 체중에서 1kg 감량", 1.0),
        lossBadge("loss_5_percent", "5% 감량", "시작 체중의 5% 감량", profile.startWeightKg * 0.05),
        lossBadge("loss_10_percent", "10% 감량", "시작 체중의 10% 감량", profile.startWeightKg * 0.1),
        lossBadge("halfway", "목표 절반", "목표 감량의 절반 달성", goalLoss / 2),
        BadgeState(
            "goal", "목표 달성", "목표 체중에 도달",
            reachedOn, if (reachedOn != null) 1.0 else if (goalLoss > 0.0) (bestLoss / goalLoss).coerceIn(0.0, 1.0) else 0.0,
        ),
        BadgeState("meals_7", "식단 7일 기록", "서로 다른 7일에 식단 기록", meals.getOrNull(6), (meals.size / 7.0).coerceIn(0.0, 1.0)),
    )
}
