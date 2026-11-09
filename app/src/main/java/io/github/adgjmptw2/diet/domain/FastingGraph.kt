package io.github.adgjmptw2.diet.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToLong

val fastingGraphPeriods = listOf(GraphPeriod.WEEK, GraphPeriod.MONTH, GraphPeriod.YEAR)

data class FastingGraphDay(
    val date: LocalDate,
    val completedMillis: Long,
    val ongoingMillis: Long = 0,
) {
    val durationMillis: Long get() = completedMillis + ongoingMillis
    val hours: Double get() = fastingHours(durationMillis.toDouble())
}

/** 년 보기의 date는 해당 월의 첫날, 시간은 단식한 날의 평균이다. */
data class FastingGraphBar(
    val date: LocalDate,
    val completedMillis: Double?,
    val ongoingMillis: Double = 0.0,
) {
    val durationMillis: Double? get() = completedMillis?.plus(ongoingMillis)
    val hours: Double? get() = durationMillis?.let(::fastingHours)
    val ongoing: Boolean get() = ongoingMillis > 0
}

data class FastingGraphSummary(
    val period: GraphPeriod,
    val bars: List<FastingGraphBar>,
    val averageHours: Double?,
    val achievedDays: Int,
    val fastingDays: Int,
)

private fun fastingHours(millis: Double): Double = (millis / Fasting.HOUR_MS * 10).roundToLong() / 10.0

/** 종료한 날짜에 전 시간을 합산한다. 진행 중인 기록은 가장 최근 한 건만 오늘에 더한다. */
fun dailyFastingDurations(fasts: List<Fast>, now: Long, zone: ZoneId): Map<LocalDate, FastingGraphDay> {
    val live = fasts.filterNot { it.deleted }
    val completed = live.filter { it.endAt != null && it.endAt >= it.startAt && it.endAt <= now }
        .groupBy { Instant.ofEpochMilli(requireNotNull(it.endAt)).atZone(zone).toLocalDate() }
        .mapValues { (date, rows) ->
            FastingGraphDay(date, rows.sumOf {
                Fasting.elapsedMillis(it.startAt, now, it.endAt).coerceAtMost(Fasting.MAX_DURATION_MS)
            })
        }.toMutableMap()
    val active = live.filter { it.endAt == null && it.startAt <= now }
        .maxWithOrNull(compareBy<Fast> { it.updatedAt }.thenBy { it.id })
    if (active != null) {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = completed[today] ?: FastingGraphDay(today, 0)
        completed[today] = day.copy(ongoingMillis = Fasting.elapsedMillis(active.startAt, now)
            .coerceAtMost(Fasting.MAX_DURATION_MS))
    }
    return completed
}

fun fastingGraph(fasts: List<Fast>, period: GraphPeriod, targetHours: Int,
    now: Long, zone: ZoneId): FastingGraphSummary {
    require(period in fastingGraphPeriods)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val from = when (period) {
        GraphPeriod.WEEK -> today.minusDays(6)
        GraphPeriod.MONTH -> today.minusDays(30)
        GraphPeriod.YEAR -> today.minusMonths(11).withDayOfMonth(1)
        GraphPeriod.THREE_MONTHS -> error("Unsupported fasting period")
    }
    val daily = dailyFastingDurations(fasts, now, zone).filterKeys { it in from..today }
    val bars = if (period == GraphPeriod.YEAR) {
        val monthly = daily.values.groupBy { YearMonth.from(it.date) }
        (0L..11L).map { offset ->
            val date = from.plusMonths(offset)
            val days = monthly[YearMonth.from(date)].orEmpty()
            FastingGraphBar(date, days.takeIf { it.isNotEmpty() }?.map { it.completedMillis.toDouble() }?.average(),
                days.takeIf { it.isNotEmpty() }?.map { it.ongoingMillis.toDouble() }?.average() ?: 0.0)
        }
    } else (0L..ChronoUnit.DAYS.between(from, today)).map { offset ->
        val date = from.plusDays(offset)
        val day = daily[date]
        FastingGraphBar(date, day?.completedMillis?.toDouble(), day?.ongoingMillis?.toDouble() ?: 0.0)
    }
    val average = daily.values.takeIf { it.isNotEmpty() }?.map { it.durationMillis.toDouble() }?.average()
    val goal = targetHours.coerceIn(Fasting.TARGET_HOURS) * Fasting.HOUR_MS
    return FastingGraphSummary(period, bars, average?.let(::fastingHours),
        daily.values.count { it.durationMillis >= goal }, daily.size)
}

/** 24시간을 기본으로, 값과 목표를 모두 담는 6시간 눈금까지 올린다. */
fun fastingAxisMaximum(values: Iterable<Double?>, targetHours: Double): Double {
    val maximum = maxOf(24.0, values.filterNotNull().filter { it.isFinite() && it >= 0 }.maxOrNull() ?: 0.0,
        targetHours.takeIf { it.isFinite() && it >= 0 } ?: 0.0)
    return ceil(maximum / 6) * 6
}
