package io.github.adgjmptw2.diet.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class FastingGraphTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 9)
    private val now = at("2026-11-09T18:00:00+09:00")

    private fun at(value: String) = ZonedDateTime.parse(value).toInstant().toEpochMilli()

    private fun finished(id: String, start: String, end: String, deleted: Boolean = false): Fast =
        Fast(id, at(start), at(end), 16, at(end), deleted)

    @Test fun overnightFastBelongsOnlyToItsEndDateInTheRequestedZone() {
        val fast = finished("overnight", "2026-11-07T20:00:00+09:00", "2026-11-08T00:20:00+09:00")
        val days = dailyFastingDurations(listOf(fast), now, zone)
        assertEquals(setOf(today.minusDays(1)), days.keys)
        assertEquals(4.3, days.getValue(today.minusDays(1)).hours, 0.0)
        assertEquals(4 * Fasting.HOUR_MS + 20 * 60_000, days.getValue(today.minusDays(1)).durationMillis)
        assertEquals(setOf(today.minusDays(2)), dailyFastingDurations(listOf(fast), now, ZoneId.of("UTC")).keys)
    }

    @Test fun sumsSeveralFastsBeforeRoundingAndExcludesDeletedRows() {
        val a = finished("a", "2026-11-08T00:00:00+09:00", "2026-11-08T08:02:00+09:00")
        val b = finished("b", "2026-11-08T09:00:00+09:00", "2026-11-08T17:02:00+09:00")
        val deleted = finished("deleted", "2026-11-07T22:00:00+09:00", "2026-11-08T18:00:00+09:00", deleted = true)
        val graph = fastingGraph(listOf(a, b, deleted), GraphPeriod.WEEK, 16, now, zone)
        val bar = graph.bars.first { it.date == today.minusDays(1) }
        assertEquals(16.1, bar.hours!!, 0.0)
        assertEquals(16.1, graph.averageHours!!, 0.0)
        assertEquals(1, graph.fastingDays)
        assertEquals(1, graph.achievedDays)
        assertNull(graph.bars.last().hours)
    }

    @Test fun ongoingTimeIsAddedToTodayAndKeptSeparateFromCompletedTime() {
        val finished = finished("done", "2026-11-09T00:00:00+09:00", "2026-11-09T02:00:00+09:00")
        val active = Fast("active", at("2026-11-09T03:00:00+09:00"), null, 16, now)
        val graph = fastingGraph(listOf(finished, active), GraphPeriod.WEEK, 16, now, zone)
        val bar = graph.bars.last()
        assertEquals(today, bar.date)
        assertEquals(2.0 * Fasting.HOUR_MS, bar.completedMillis!!, 0.0)
        assertEquals(15.0 * Fasting.HOUR_MS, bar.ongoingMillis, 0.0)
        assertEquals(17.0, bar.hours!!, 0.0)
        assertTrue(bar.ongoing)
        assertEquals(1, graph.achievedDays)
    }

    @Test fun roundingHoursDoesNotTurnAShortFastIntoAGoalAchievement() {
        val short = Fast("short", now - 16 * Fasting.HOUR_MS + 60_000, now, 16, now)
        val graph = fastingGraph(listOf(short), GraphPeriod.WEEK, 16, now, zone)
        assertEquals(16.0, graph.bars.last().hours!!, 0.0)
        assertEquals(0, graph.achievedDays)
        val half = Fast("half", now - Fasting.HOUR_MS - 15 * 60_000, now, 16, now)
        assertEquals(1.3, dailyFastingDurations(listOf(half), now, zone).getValue(today).hours, 0.0)
    }

    @Test fun onlyLatestLiveActiveFastCountsAfterSyncConflict() {
        val older = Fast("older", now - 20 * Fasting.HOUR_MS, null, 16, now - 1)
        val latest = Fast("latest", now - 2 * Fasting.HOUR_MS, null, 16, now)
        val deleted = Fast("deleted", now - 30 * Fasting.HOUR_MS, null, 16, now + 1, deleted = true)
        val day = dailyFastingDurations(listOf(older, latest, deleted), now, zone).getValue(today)
        assertEquals(2 * Fasting.HOUR_MS, day.ongoingMillis)
    }

    @Test fun eachFastIsCappedAtSevenDaysAndDailyTotalMayExceedTwentyFourHours() {
        val completed = Fast("done", now - 9 * 24 * Fasting.HOUR_MS, now - Fasting.HOUR_MS, 16, now)
        val active = Fast("active", now - 10 * 24 * Fasting.HOUR_MS, null, 16, now)
        val day = dailyFastingDurations(listOf(completed, active), now, zone).getValue(today)
        assertEquals(Fasting.MAX_DURATION_MS, day.completedMillis)
        assertEquals(Fasting.MAX_DURATION_MS, day.ongoingMillis)
        assertEquals(336.0, day.hours, 0.0)
    }

    @Test fun monthlyBarsAverageDailyTotalsAndExcludeDaysWithoutFasting() {
        val rows = listOf(
            finished("a", "2026-10-03T00:00:00+09:00", "2026-10-03T04:00:00+09:00"),
            finished("b", "2026-10-03T05:00:00+09:00", "2026-10-03T13:00:00+09:00"),
            finished("c", "2026-10-12T00:00:00+09:00", "2026-10-12T18:00:00+09:00"),
            finished("deleted", "2026-10-13T00:00:00+09:00", "2026-10-13T20:00:00+09:00", deleted = true),
            finished("outside", "2025-11-30T00:00:00+09:00", "2025-11-30T22:00:00+09:00"),
        )
        val graph = fastingGraph(rows, GraphPeriod.YEAR, 16, now, zone)
        assertEquals(12, graph.bars.size)
        assertEquals(LocalDate.of(2025, 12, 1), graph.bars.first().date)
        assertEquals(today.withDayOfMonth(1), graph.bars.last().date)
        assertEquals(15.0, graph.bars.first { it.date == LocalDate.of(2026, 10, 1) }.hours!!, 0.0)
        assertNull(graph.bars.last().hours)
        assertEquals(15.0, graph.averageHours!!, 0.0)
        assertEquals(2, graph.fastingDays)
        assertEquals(1, graph.achievedDays)
    }

    @Test fun monthlyAverageIncludesOngoingTimeOnlyInCurrentMonth() {
        val completed = finished("done", "2026-11-08T00:00:00+09:00", "2026-11-08T10:00:00+09:00")
        val active = Fast("active", now - 16 * Fasting.HOUR_MS, null, 16, now)
        val graph = fastingGraph(listOf(completed, active), GraphPeriod.YEAR, 16, now, zone)
        assertEquals(13.0, graph.bars.last().hours!!, 0.0)
        assertEquals(5.0 * Fasting.HOUR_MS, graph.bars.last().completedMillis!!, 0.0)
        assertEquals(8.0 * Fasting.HOUR_MS, graph.bars.last().ongoingMillis, 0.0)
        assertEquals(2, graph.fastingDays)
        assertTrue(graph.bars.last().ongoing)
    }

    @Test fun periodsKeepSevenOrThirtyOneDaysIncludingTodayAcrossLeapDay() {
        val leapNow = at("2024-03-01T10:00:00+09:00")
        val week = fastingGraph(emptyList(), GraphPeriod.WEEK, 16, leapNow, zone)
        val month = fastingGraph(emptyList(), GraphPeriod.MONTH, 16, leapNow, zone)
        assertEquals(7, week.bars.size)
        assertEquals(31, month.bars.size)
        assertEquals(LocalDate.of(2024, 1, 31), month.bars.first().date)
        assertEquals(LocalDate.of(2024, 3, 1), month.bars.last().date)
        assertTrue(month.bars.all { it.hours == null })
        assertNull(month.averageHours)
        assertEquals(0, month.fastingDays)
    }

    @Test fun futureAndReversedRecordsDoNotCount() {
        val reversed = Fast("reversed", now, now - 1, 16, now)
        val future = Fast("future", now, now + Fasting.HOUR_MS, 16, now)
        val futureActive = Fast("futureActive", now + Fasting.HOUR_MS, null, 16, now)
        assertTrue(dailyFastingDurations(listOf(reversed, future, futureActive), now, zone).isEmpty())
    }

    @Test fun axisStartsAtTwentyFourAndRoundsValuesAndGoalsUpToSixHourTicks() {
        assertEquals(24.0, fastingAxisMaximum(emptyList(), 16.0), 0.0)
        assertEquals(24.0, fastingAxisMaximum(listOf(null, 0.0, 24.0), 16.0), 0.0)
        assertEquals(30.0, fastingAxisMaximum(listOf(24.01), 16.0), 0.0)
        assertEquals(48.0, fastingAxisMaximum(listOf(42.1), 16.0), 0.0)
        assertEquals(72.0, fastingAxisMaximum(listOf(16.0), 72.0), 0.0)
        assertEquals(336.0, fastingAxisMaximum(listOf(336.0), 16.0), 0.0)
        assertEquals(24.0, fastingAxisMaximum(listOf(Double.NaN, Double.POSITIVE_INFINITY, -10.0), Double.NaN), 0.0)
    }
}
