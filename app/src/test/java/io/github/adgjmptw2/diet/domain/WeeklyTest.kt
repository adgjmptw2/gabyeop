package io.github.adgjmptw2.diet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeeklyTest {
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun noRecordsReturnsEmptyList() {
        assertTrue(weeks(emptyList(), emptyList(), monday).isEmpty())
    }

    @Test
    fun weeksRunMondayToSundayAndNewestComesFirst() {
        val summaries = weeks(listOf(insightWeight(monday, 80.0)), emptyList(), monday.plusDays(13))
        assertEquals(listOf(monday.plusWeeks(1), monday), summaries.map { it.start })
        summaries.forEach {
            assertEquals(DayOfWeek.MONDAY, it.start.dayOfWeek)
            assertEquals(DayOfWeek.SUNDAY, it.end.dayOfWeek)
            assertEquals(it.start.plusDays(6), it.end)
        }
    }

    @Test
    fun aWeekCrossesTheYearBoundaryWithoutSplitting() {
        val newYear = LocalDate.of(2027, 1, 1)
        val summary = weeks(listOf(insightWeight(newYear, 75.0)), emptyList(), newYear).single()
        assertEquals(LocalDate.of(2026, 12, 28), summary.start)
        assertEquals(LocalDate.of(2027, 1, 3), summary.end)
    }

    @Test
    fun weightAverageCountsEachRecordedDayOnce() {
        val entries = listOf(insightWeight(monday, 80.0), insightWeight(monday.plusDays(3), 78.0), insightWeight(monday, 90.0))
        val summary = weeks(entries, emptyList(), monday.plusDays(6)).single()
        assertEquals(79.0, summary.avgKg!!, 1e-9)
        assertEquals(2, summary.weighDays)
        assertNull(summary.changeKg)
    }

    @Test
    fun changeSkipsBothEmptyWeeksAndMealOnlyWeeks() {
        val entries = listOf(insightWeight(monday, 80.0), insightWeight(monday.plusDays(22), 77.0))
        val summaries = weeks(entries, listOf(insightMeal(monday.plusDays(8), 1000.0)), monday.plusDays(25))
        assertEquals(4, summaries.size)
        assertEquals(-3.0, summaries.first().changeKg!!, 1e-9)
        assertNull(summaries[1].avgKg)
        assertNull(summaries[1].changeKg)
        assertEquals(1, summaries[2].mealDays)
    }

    @Test
    fun emptyWeeksContinueThroughThisWeek() {
        val summaries = weeks(listOf(insightWeight(monday, 80.0)), emptyList(), monday.plusDays(21))
        assertEquals(4, summaries.size)
        val empty = summaries.first()
        assertNull(empty.avgKg)
        assertNull(empty.avgKcal)
        assertEquals(0, empty.weighDays)
        assertEquals(0, empty.mealDays)
    }

    @Test
    fun caloriesAreAveragedAcrossLoggedDaysRatherThanMealsOrSevenDays() {
        val meals = listOf(
            insightMeal(monday, 500.0, "breakfast"),
            insightMeal(monday, 1000.0, "dinner"),
            insightMeal(monday.plusDays(2), 900.0),
        )
        val summary = weeks(emptyList(), meals, monday.plusDays(6)).single()
        assertEquals(1200.0, summary.avgKcal!!, 1e-9)
        assertEquals(2, summary.mealDays)
        assertNull(summary.avgKg)
        assertNull(summary.changeKg)
    }

    @Test
    fun absentMealsHaveNoAverageRatherThanZero() {
        val summary = weeks(listOf(insightWeight(monday, 80.0)), emptyList(), monday).single()
        assertNull(summary.avgKcal)
        assertEquals(0, summary.mealDays)
    }

    @Test
    fun zeroCalorieMealStillCountsAsALoggedDay() {
        val summary = weeks(emptyList(), listOf(insightMeal(monday, 0.0)), monday).single()
        assertEquals(1, summary.mealDays)
        assertEquals(0.0, summary.avgKcal!!, 1e-9)
    }

    @Test
    fun futureWeightsAndMealsAreExcludedEvenInThisWeek() {
        val entries = listOf(insightWeight(monday, 80.0), insightWeight(monday.plusDays(1), 60.0))
        val meals = listOf(insightMeal(monday.plusDays(1), 2000.0))
        val summary = weeks(entries, meals, monday).single()
        assertEquals(80.0, summary.avgKg!!, 1e-9)
        assertEquals(0, summary.mealDays)
        assertTrue(weeks(entries.drop(1), meals, monday).isEmpty())
    }

    @Test
    fun firstWeekCanComeFromMealsBeforeAnyWeight() {
        val summaries = weeks(
            listOf(insightWeight(monday.plusDays(7), 79.0)),
            listOf(insightMeal(monday.minusDays(7), 1000.0)),
            monday.plusDays(7),
        )
        assertEquals(monday.minusDays(7), summaries.last().start)
        assertEquals(3, summaries.size)
        assertNull(summaries.first().changeKg)
    }

    @Test
    fun inputOrderingDoesNotChangeWeeklyResults() {
        val entries = listOf(insightWeight(monday, 80.0), insightWeight(monday.plusDays(2), 78.0), insightWeight(monday.plusDays(7), 77.0))
        val meals = listOf(insightMeal(monday, 500.0), insightMeal(monday.plusDays(8), 1500.0))
        val today = monday.plusDays(13)
        assertEquals(weeks(entries, meals, today), weeks(entries.reversed(), meals.reversed(), today))
    }
}
