package io.github.adgjmptw2.diet.ui.home

import io.github.adgjmptw2.diet.domain.ActivityLevel
import io.github.adgjmptw2.diet.domain.Profile
import io.github.adgjmptw2.diet.domain.Sex
import io.github.adgjmptw2.diet.domain.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HomeStateBuildTest {

    private val today = LocalDate.of(2026, 11, 8)

    private val profile = Profile(
        sex = Sex.MALE,
        birthYear = 1996,
        heightCm = 180.0,
        startWeightKg = 80.0,
        targetWeightKg = 70.0,
        activity = ActivityLevel.SEDENTARY,
        startDate = LocalDate.of(2026, 1, 1),
    )

    private fun entry(date: LocalDate, kg: Double) = WeightEntry(
        id = date.toString(),
        date = date,
        weightKg = kg,
    )

    @Test
    fun `기록이 없으면 시작 체중으로 계산한다`() {
        val state = HomeViewModel.build(profile, emptyList(), today)

        assertEquals(80.0, state.progress.currentKg, 0.0)
        assertEquals(0, state.progress.percent)
        assertFalse(state.progress.reached)
        assertNull(state.todayEntry)
    }

    @Test
    fun `오늘 기록을 찾고 최신 기록을 현재 체중으로 쓴다`() {
        val entries = listOf(
            entry(today, 77.0),
            entry(today.minusDays(1), 78.0),
            entry(today.minusDays(2), 79.0),
        )

        val state = HomeViewModel.build(profile, entries, today)

        assertEquals(77.0, state.progress.currentKg, 0.0)
        assertEquals(77.0, state.todayEntry?.weightKg ?: 0.0, 0.0)
    }

    @Test
    fun `오늘 기록이 없으면 todayEntry는 null이다`() {
        val entries = listOf(
            entry(today.minusDays(1), 78.0),
            entry(today.minusDays(2), 79.0),
        )

        val state = HomeViewModel.build(profile, entries, today)

        assertNull(state.todayEntry)
    }
}
