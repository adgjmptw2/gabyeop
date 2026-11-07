package io.github.adgjmptw2.diet.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class WeekSummary(
    val start: LocalDate,
    val end: LocalDate,
    val avgKg: Double?,
    val changeKg: Double?,
    val weighDays: Int,
    val avgKcal: Double?,
    val mealDays: Int,
)

/** 월~일. 첫 기록 이후의 빈 주도 포함하며, 오늘 이후의 기록은 제외한다. */
fun weeks(weights: List<WeightEntry>, meals: List<MealEntry>, today: LocalDate): List<WeekSummary> {
    val dailyWeights = weights.filter { it.date <= today }.distinctBy { it.date }
    val dailyMeals = meals.filter { it.date <= today }.groupBy { it.date }
    val firstDay = (dailyWeights.map { it.date } + dailyMeals.keys).minOrNull() ?: return emptyList()
    val firstWeek = firstDay.monday()
    val thisWeek = today.monday()
    val weightsByWeek = dailyWeights.groupBy { it.date.monday() }
    val caloriesByWeek = dailyMeals.entries.groupBy({ it.key.monday() }, { day -> day.value.sumOf { it.kcal } })
    var previousAverage: Double? = null

    return generateSequence(firstWeek) { if (it < thisWeek) it.plusWeeks(1) else null }
        .map { start ->
            val weekWeights = weightsByWeek[start].orEmpty()
            val dailyCalories = caloriesByWeek[start].orEmpty()
            val average = weekWeights.takeIf { it.isNotEmpty() }?.map { it.weightKg }?.average()
            val summary = WeekSummary(
                start = start,
                end = start.plusDays(6),
                avgKg = average,
                changeKg = average?.let { current -> previousAverage?.let { current - it } },
                weighDays = weekWeights.size,
                avgKcal = dailyCalories.takeIf { it.isNotEmpty() }?.average(),
                mealDays = dailyCalories.size,
            )
            if (average != null) previousAverage = average
            summary
        }.toList().asReversed()
}

private fun LocalDate.monday(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
