package io.github.adgjmptw2.diet.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class GraphMetric(val label: String) { WATER("물"), CALORIES("칼로리"), STEPS("걸음") }
enum class GraphPeriod(val label: String) {
    WEEK("1주"), MONTH("1달"), THREE_MONTHS("3달");
    fun from(to: LocalDate): LocalDate = when (this) {
        WEEK -> to.minusDays(6)
        MONTH -> to.minusMonths(1).plusDays(1)
        THREE_MONTHS -> to.minusMonths(3).plusDays(1)
    }
}

data class GraphDay(val date: LocalDate, val value: Double?)
data class GraphSummary(val days: List<GraphDay>, val average: Double?, val achievedDays: Int) {
    val recordedDays: Int get() = days.count { it.value != null }
}

/** 평균의 분모는 기록한 날. null은 빈칸이고 명시한 0은 평균에 포함한다. */
fun trackingGraph(from: LocalDate, to: LocalDate, values: Map<LocalDate, Double>, goal: Double,
    atMostGoal: Boolean = false): GraphSummary {
    require(from <= to)
    val days = (0L..ChronoUnit.DAYS.between(from, to)).map { offset ->
        val date = from.plusDays(offset)
        GraphDay(date, values[date]?.takeIf { it.isFinite() && it >= 0 })
    }
    val recorded = days.mapNotNull { it.value }
    val achieved = if (goal <= 0) 0 else recorded.count { if (atMostGoal) it <= goal else it >= goal }
    return GraphSummary(days, recorded.takeIf { it.isNotEmpty() }?.average(), achieved)
}

fun dailyMealCalories(meals: List<MealEntry>): Map<LocalDate, Double> =
    meals.groupBy { it.date }.mapValues { (_, entries) -> entries.sumOf { it.kcal } }

fun dailyWaterAmounts(notes: Map<LocalDate, DailyNote>, recordedDays: Set<LocalDate>,
    from: LocalDate, to: LocalDate): Map<LocalDate, Double> {
    val dates = (recordedDays + notes.filterValues { it.waterMl > 0 }.keys).filter { it in from..to }
    return dates.associateWith { notes[it]?.waterMl?.toDouble() ?: 0.0 }
}
