package io.github.adgjmptw2.diet.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthlyTest {
    private val month = YearMonth.of(2026, 11)
    private fun weight(day: String, kg: Double) = WeightEntry(day, LocalDate.parse(day), kg)

    @Test fun usesLastRecordBeforeMonthInsteadOfFirstRecordInsideMonth() {
        val entries = listOf(weight("2026-10-31", 80.0), weight("2026-11-01", 76.0), weight("2026-11-20", 77.0))
        assertEquals(-3.0, monthWeightChange(entries.reversed(), month)!!, 0.0)
    }

    @Test fun oneMonthlyRecordStillHasChangeWhenThereIsAnEarlierRecord() {
        val entries = listOf(weight("2026-09-01", 81.0), weight("2026-11-08", 78.0))
        assertEquals(-3.0, monthWeightChange(entries, month)!!, 0.0)
    }

    @Test fun fallsBackToFirstMonthlyRecordWhenThereIsNoEarlierRecord() {
        val entries = listOf(weight("2026-11-01", 76.0), weight("2026-11-20", 77.0))
        assertEquals(1.0, monthWeightChange(entries, month)!!, 0.0)
    }

    @Test fun hidesChangeWithoutABaselineOrWithoutAMonthlyRecord() {
        assertNull(monthWeightChange(emptyList(), month))
        assertNull(monthWeightChange(listOf(weight("2026-11-08", 78.0)), month))
        assertNull(monthWeightChange(listOf(weight("2026-10-31", 80.0)), month))
    }

    @Test fun excludesLaterMonthsAndHandlesYearBoundary() {
        val entries = listOf(weight("2025-12-31", 80.0), weight("2026-01-31", 78.0), weight("2026-02-01", 75.0))
        assertEquals(-2.0, monthWeightChange(entries, YearMonth.of(2026, 1))!!, 0.0)
    }
}
