package io.github.adgjmptw2.diet.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

data class WeightAverage(val date: LocalDate, val avgKg: Double?)

/** 각 날짜와 그 전 6일의 기록만 평균낸다. 7일 모두 비었으면 null로 선을 끊는다. */
fun sevenDayAverages(weights: List<WeightEntry>, today: LocalDate): List<WeightAverage> {
    val entries = weights.filter { it.date <= today }.distinctBy { it.date }.sortedBy { it.date }
    if (entries.isEmpty()) return emptyList()
    val result = mutableListOf<WeightAverage>()
    var left = 0
    var right = 0
    var sum = 0.0
    var date = entries.first().date
    while (date <= today) {
        while (right < entries.size && entries[right].date <= date) {
            sum += entries[right++].weightKg
        }
        val firstIncluded = date.minusDays(6)
        while (left < right && entries[left].date < firstIncluded) {
            sum -= entries[left++].weightKg
        }
        val count = right - left
        result += WeightAverage(date, if (count == 0) null else sum / count)
        date = date.plusDays(1)
    }
    return result
}

/**
 * 오늘을 포함한 최근 28일의 실제 기록으로 최소제곱 직선을 구한다.
 * 예측은 올림한 날짜이며, 직선이 이미 목표를 지났다면 오늘을 반환한다.
 */
fun expectedGoalDate(weights: List<WeightEntry>, targetKg: Double, today: LocalDate): LocalDate? {
    if (!targetKg.isFinite()) return null
    val entries = weights.filter { it.date >= today.minusDays(27) && it.date <= today }
        .distinctBy { it.date }
    if (entries.size < 4 || entries.any { !it.weightKg.isFinite() }) return null

    // 오늘을 0으로 두어 큰 epochDay 값에 의한 정밀도 손실을 피한다.
    val xs = entries.map { ChronoUnit.DAYS.between(today, it.date).toDouble() }
    val meanX = xs.average()
    val meanY = entries.map { it.weightKg }.average()
    val denominator = xs.sumOf { (it - meanX) * (it - meanX) }
    if (denominator == 0.0) return null
    val slope = entries.indices.sumOf { (xs[it] - meanX) * (entries[it].weightKg - meanY) } / denominator
    if (!slope.isFinite() || slope >= 0.0) return null
    val fittedToday = meanY - slope * meanX
    val days = ceil((targetKg - fittedToday) / slope).coerceAtLeast(0.0)
    val limit = ChronoUnit.DAYS.between(today, today.plusYears(2))
    if (!days.isFinite() || days > limit.toDouble()) return null
    return today.plusDays(days.toLong())
}
