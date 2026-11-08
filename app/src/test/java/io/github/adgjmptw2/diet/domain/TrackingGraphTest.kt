package io.github.adgjmptw2.diet.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TrackingGraphTest {
    private val from = LocalDate.of(2026, 11, 3)
    private val to = from.plusDays(6)

    @Test fun averageIncludesRecordedZeroAndExcludesMissingDays() {
        val graph = trackingGraph(from, to, mapOf(from to 0.0, from.plusDays(2) to 2000.0, to to 2500.0), 2000.0)
        assertEquals(7, graph.days.size)
        assertEquals(3, graph.recordedDays)
        assertEquals(1500.0, graph.average!!, 0.0)
        assertEquals(2, graph.achievedDays)
        assertEquals(0.0, graph.days.first().value!!, 0.0)
        assertNull(graph.days[1].value)
    }
    @Test fun caloriesCountRecordedDaysAtOrBelowTarget() {
        val graph = trackingGraph(from, to, mapOf(from to 0.0, from.plusDays(1) to 1800.0, to to 1900.0), 1800.0, atMostGoal = true)
        assertEquals(2, graph.achievedDays)
        assertEquals(3, graph.recordedDays)
    }
    @Test fun emptyPeriodHasNoAverageOrAchievements() {
        val graph = trackingGraph(from, to, emptyMap(), 8000.0)
        assertNull(graph.average)
        assertEquals(0, graph.achievedDays)
        assertTrue(graph.days.all { it.value == null })
    }
    @Test fun mealsAreSummedByDateAndKeepExplicitZero() {
        val values = dailyMealCalories(listOf(insightMeal(from, 400.0, "a"), insightMeal(from, 650.0, "b"), insightMeal(to, 0.0, "c")))
        assertEquals(1050.0, values[from]!!, 0.0)
        assertEquals(0.0, values[to]!!, 0.0)
        assertFalse(values.containsKey(from.plusDays(1)))
    }
    @Test fun zeroWaterSurvivesLegacySyncTombstoneWithoutCountingMemoOnlyDays() {
        val notes = mapOf(from to DailyNote(from.toEpochDay(), memo = "산책"), to to DailyNote(to.toEpochDay(), waterMl = 2300))
        val zeroDate = from.plusDays(1)
        val values = dailyWaterAmounts(notes, setOf(zeroDate, from.minusDays(1)), from, to)
        assertFalse(values.containsKey(from))
        assertEquals(0.0, values[zeroDate]!!, 0.0)
        assertEquals(2300.0, values[to]!!, 0.0)
        assertEquals(2, values.size)
    }
    @Test fun periodsUseInclusiveCalendarDatesAcrossLeapMonth() {
        val leapDay = LocalDate.of(2024, 2, 29)
        assertEquals(leapDay.minusDays(6), GraphPeriod.WEEK.from(leapDay))
        assertEquals(LocalDate.of(2024, 1, 30), GraphPeriod.MONTH.from(leapDay))
        assertEquals(LocalDate.of(2023, 11, 30), GraphPeriod.THREE_MONTHS.from(leapDay))
    }
    @Test fun recordsOutsideRangeAndInvalidValuesDoNotCount() {
        val graph = trackingGraph(from, to, mapOf(from.minusDays(1) to 2000.0, from to Double.NaN, to to -1.0), 0.0)
        assertNull(graph.average)
        assertEquals(0, graph.achievedDays)
    }
}
