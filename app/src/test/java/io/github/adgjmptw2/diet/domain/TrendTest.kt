package io.github.adgjmptw2.diet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class TrendTest {
    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun noWeightsHaveNoMovingAverage() {
        assertTrue(sevenDayAverages(emptyList(), today).isEmpty())
    }

    @Test
    fun calendarWindowIncludesDaySixButExcludesDaySeven() {
        val entries = listOf(
            insightWeight(today.minusDays(7), 100.0),
            insightWeight(today.minusDays(6), 80.0),
            insightWeight(today, 70.0),
        )
        assertEquals(75.0, sevenDayAverages(entries, today).last().avgKg!!, 1e-9)
    }

    @Test
    fun sparseRecordsAreNotTreatedAsSevenRecentEntries() {
        val entries = (0L..6L).map { insightWeight(today.minusDays(it * 7), 70.0 + it) }
        assertEquals(70.0, sevenDayAverages(entries, today).last().avgKg!!, 1e-9)
    }

    @Test
    fun emptyCalendarWindowBreaksTheLineBeforeNextRecord() {
        val first = today.minusDays(10)
        val averages = sevenDayAverages(listOf(insightWeight(first, 80.0), insightWeight(today, 70.0)), today).associateBy { it.date }
        assertEquals(80.0, averages.getValue(first.plusDays(6)).avgKg!!, 1e-9)
        assertNull(averages.getValue(first.plusDays(7)).avgKg)
        assertNull(averages.getValue(today.minusDays(1)).avgKg)
        assertEquals(70.0, averages.getValue(today).avgKg!!, 1e-9)
    }

    @Test
    fun movingAverageIsAlsoCalculatedOnUnrecordedDays() {
        val entries = listOf(insightWeight(today.minusDays(3), 80.0), insightWeight(today.minusDays(1), 78.0))
        val averages = sevenDayAverages(entries, today)
        assertEquals(4, averages.size)
        assertEquals(80.0, averages[1].avgKg!!, 1e-9)
        assertEquals(79.0, averages.last().avgKg!!, 1e-9)
    }

    @Test
    fun movingAverageIgnoresFutureAndDuplicateDays() {
        val entries = listOf(insightWeight(today, 70.0), insightWeight(today, 100.0), insightWeight(today.plusDays(1), 50.0))
        val average = sevenDayAverages(entries, today).single()
        assertEquals(today, average.date)
        assertEquals(70.0, average.avgKg!!, 1e-9)
    }

    @Test
    fun fewerThanFourDistinctDaysCannotPredictGoal() {
        val entries = (0L..2L).map { insightWeight(today.minusDays(it), 70.0 + it) }
        assertNull(expectedGoalDate(entries + entries.first(), 65.0, today))
    }

    @Test
    fun increasingWeightCannotPredictGoal() {
        val entries = (0L..3L).map { insightWeight(today.minusDays(it), 70.0 - it) }
        assertNull(expectedGoalDate(entries, 65.0, today))
    }

    @Test
    fun flatWeightCannotPredictGoal() {
        val entries = (0L..3L).map { insightWeight(today.minusDays(it), 70.0) }
        assertNull(expectedGoalDate(entries, 65.0, today))
    }

    @Test
    fun weightsOlderThanTwentyEightDaysDoNotSupplyFourthRecord() {
        val entries = listOf(0L, 1L, 2L, 28L).map { insightWeight(today.minusDays(it), 70.0 + it) }
        assertNull(expectedGoalDate(entries, 65.0, today))
    }

    @Test
    fun oldestDayInsideTheWindowIsIncluded() {
        val entries = listOf(0L, 1L, 2L, 27L).map { insightWeight(today.minusDays(it), 70.0 + it) }
        assertEquals(today.plusDays(5), expectedGoalDate(entries, 65.0, today))
    }

    @Test
    fun regressionUsesCalendarDistancesRatherThanRecordPositions() {
        val entries = listOf(0L, 2L, 9L, 20L).map { insightWeight(today.minusDays(it), 70.0 + it * 0.5) }
        assertEquals(today.plusDays(10), expectedGoalDate(entries.reversed(), 65.0, today))
    }

    @Test
    fun leastSquaresUsesEveryRecordRatherThanOnlyTheEndpoints() {
        val entries = listOf(70.0, 72.0, 71.0, 74.0).mapIndexed { index, kg ->
            insightWeight(today.minusDays(index.toLong()), kg)
        }
        // 회귀선은 오늘 70.1kg, 하루 -1.1kg이므로 65kg까지 5일.
        assertEquals(today.plusDays(5), expectedGoalDate(entries, 65.0, today))
    }

    @Test
    fun fractionalArrivalDayRoundsUp() {
        val entries = (0L..3L).map { insightWeight(today.minusDays(it), 70.0 + it) }
        assertEquals(today.plusDays(2), expectedGoalDate(entries, 68.5, today))
    }

    @Test
    fun arrivalBeyondTwoYearsIsHidden() {
        val entries = (0L..3L).map { insightWeight(today.minusDays(it), 80.0 + it / 128.0) }
        assertNull(expectedGoalDate(entries, 60.0, today))
    }

    @Test
    fun arrivalExactlyTwoYearsAwayIsAllowed() {
        val days = ChronoUnit.DAYS.between(today, today.plusYears(2))
        val entries = (0L..3L).map { insightWeight(today.minusDays(it), 80.0 + it / 64.0) }
        assertEquals(today.plusYears(2), expectedGoalDate(entries, 80.0 - days / 64.0, today))
    }

    @Test
    fun aRegressionThatAlreadyPassedGoalReturnsToday() {
        val entries = (0L..3L).map { insightWeight(today.minusDays(it), 70.0 + it) }
        assertEquals(today, expectedGoalDate(entries, 71.0, today))
    }

    @Test
    fun predictionIgnoresFutureRecords() {
        val entries = (0L..3L).map { insightWeight(today.minusDays(it), 70.0 + it) }
        val future = insightWeight(today.plusDays(1), 120.0)
        assertEquals(expectedGoalDate(entries, 65.0, today), expectedGoalDate(entries + future, 65.0, today))
    }
}
