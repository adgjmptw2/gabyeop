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
}