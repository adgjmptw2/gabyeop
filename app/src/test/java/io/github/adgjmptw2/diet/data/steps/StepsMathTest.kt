package io.github.adgjmptw2.diet.data.steps

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StepsMathTest {

    @Test
    fun `height is centimeters and stride is meters`() {
        // 180cm × 0.415 = 74.7cm = 0.747m. 1,000걸음 ≈ 0.747km
        assertEquals(0.747, StepsMath.distanceKm(1_000, 180.0), 1e-9)
        assertEquals(7.47, StepsMath.distanceKm(10_000, 180.0), 1e-9)
    }

    @Test
    fun `missing or invalid height falls back to 0_comma_7m`() {
        assertEquals(0.7, StepsMath.distanceKm(1_000, null), 1e-9)
        assertEquals(0.7, StepsMath.distanceKm(1_000, 20.0), 1e-9)
    }

    @Test
    fun `kcal is weight times distance times 0_comma_5`() {
        // 60kg × 7.47km × 0.5 ≈ 224kcal
        assertEquals(224, StepsMath.kcal(10_000, 180.0, 60.0))
        // 체중이 없으면 60kg로 어림한다
        assertEquals(224, StepsMath.kcal(10_000, 180.0, null))
    }

    @Test
    fun `zero steps produce zero distance and kcal`() {
        assertEquals(0.0, StepsMath.distanceKm(0, 180.0), 1e-9)
        assertEquals(0, StepsMath.kcal(0, 180.0, 70.0))
    }

    @Test
    fun `average and achieved ignore days without data`() {
        val days = listOf(10_000, null, 6_000)

        assertEquals(8_000, StepsMath.average(days))
        assertEquals(1, StepsMath.achieved(days, 8_000))
        assertEquals(0, StepsMath.average(emptyList()))
        assertEquals(0, StepsMath.achieved(listOf(null, 0), 8_000))
    }

    @Test
    fun `day list ends today and keeps missing days as null`() {
        val today = LocalDate.of(2026, 11, 9)
        val days = StepsMath.dayList(today, 7, mapOf(today to 1_200))

        assertEquals(7, days.size)
        assertEquals(today, days.last().first)
        assertEquals(1_200, days.last().second)
        assertEquals(today.minusDays(6), days.first().first)
        assertEquals(null, days.first().second)
    }

    @Test
    fun `fallback counts only the busiest app per day`() {
        val day = LocalDate.of(2026, 11, 24)
        val next = day.plusDays(1)
        val entries = listOf(
            StepsMath.Entry("com.sec.android.app.shealth", day, 4_000),
            StepsMath.Entry("com.sec.android.app.shealth", day, 3_000),
            StepsMath.Entry("com.google.android.apps.fitness", day, 6_500),
            StepsMath.Entry("com.google.android.apps.fitness", next, 900),
            StepsMath.Entry("com.sec.android.app.shealth", next, 0),
        )

        assertEquals(mapOf(day to 7_000, next to 900), StepsMath.busiestOrigin(entries))
        assertEquals(emptyMap<LocalDate, Int>(), StepsMath.busiestOrigin(emptyList()))
    }

    @Test
    fun `reads thirty days again until older records exist`() {
        val today = LocalDate.of(2026, 11, 25)
        val oldest = today.minusDays(30)

        assertEquals(oldest, StepsMath.pullFrom(today, oldest, null, false))
        // 오늘 이미 읽었어도 비어 있었으면(삼성 헬스를 나중에 연결) 다시 30일
        assertEquals(oldest, StepsMath.pullFrom(today, oldest, today, false))
        assertEquals(today.minusDays(1), StepsMath.pullFrom(today, oldest, today, true))
        // 날이 바뀌면 다시 30일
        assertEquals(oldest, StepsMath.pullFrom(today, oldest, today.minusDays(1), true))
    }

    @Test
    fun `stale sensor baseline does not pour old steps into today`() {
        val today = LocalDate.of(2026, 11, 25)

        assertEquals(true, StepsMath.countsForToday(today, today))
        assertEquals(true, StepsMath.countsForToday(today.minusDays(1), today))
        assertEquals(false, StepsMath.countsForToday(today.minusDays(2), today))
    }
}
