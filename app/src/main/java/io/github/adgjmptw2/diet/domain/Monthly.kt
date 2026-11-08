package io.github.adgjmptw2.diet.domain

import java.time.YearMonth

/** Last weight in the month minus the last weight before it, or the month's first weight. */
fun monthWeightChange(entries: List<WeightEntry>, month: YearMonth): Double? {
    val start = month.atDay(1)
    val end = month.atEndOfMonth()
    val monthly = entries.filter { it.date in start..end }.distinctBy { it.date }
    val last = monthly.maxByOrNull { it.date } ?: return null
    val previous = entries.filter { it.date < start }.maxByOrNull { it.date }
    if (previous == null && monthly.size < 2) return null
    val baseline = previous ?: monthly.minBy { it.date }
    return last.weightKg - baseline.weightKg
}
