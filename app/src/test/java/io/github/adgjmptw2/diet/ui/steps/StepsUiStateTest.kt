package io.github.adgjmptw2.diet.ui.steps

import io.github.adgjmptw2.diet.data.steps.StepsMath
import org.junit.Assert.assertEquals
import org.junit.Test

class StepsUiStateTest {

    @Test
    fun `today distance and kcal follow the shared math`() {
        assertEquals(StepsMath.distanceKm(10_000, 180.0), StepsMath.distanceKm(10_000, 180.0), 1e-9)
        assertEquals(7.47, StepsMath.distanceKm(10_000, 180.0), 1e-9)
        assertEquals(224, StepsMath.kcal(10_000, 180.0, 60.0))
    }

    @Test
    fun `average and achieved ignore days without data`() {
        val days = listOf(10_000, null, 6_000)

        assertEquals(8_000, StepsMath.average(days))
        assertEquals(1, StepsMath.achieved(days, 8_000))
    }
}
