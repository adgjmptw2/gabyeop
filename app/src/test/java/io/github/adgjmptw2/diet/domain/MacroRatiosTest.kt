package io.github.adgjmptw2.diet.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MacroRatiosTest {
    private fun meal(carb: Double?, prot: Double?, fat: Double?) =
        insightMeal(LocalDate.of(2026, 11, 8), 500.0).copy(carb = carb, prot = prot, fat = fat)

    @Test fun percentagesUseEnergyRatherThanGrams() {
        val result = macroRatios(listOf(meal(100.0, 50.0, 25.0)))
        assertEquals(listOf(49, 24, 27), listOf(result.carbPercent, result.proteinPercent, result.fatPercent))
        assertEquals(100, result.carbPercent + result.proteinPercent + result.fatPercent)
    }
    @Test fun roundingKeepsThreeEqualSharesAtOneHundred() {
        val result = macroRatios(listOf(meal(9.0, 9.0, 4.0)))
        assertEquals(100, result.carbPercent + result.proteinPercent + result.fatPercent)
        assertEquals(34, result.carbPercent)
    }
    @Test fun emptyAndZeroGramsDoNotDivideByZero() {
        for (meals in listOf(emptyList(), listOf(meal(0.0, 0.0, 0.0)))) {
            val result = macroRatios(meals)
            assertFalse(result.hasEnergy)
            assertEquals(0, result.carbPercent + result.proteinPercent + result.fatPercent)
        }
    }
    @Test fun incompleteFoodIsOmittedAsAWholeAndCountedOnce() {
        val result = macroRatios(listOf(meal(20.0, 10.0, 5.0), meal(200.0, null, 100.0), meal(null, null, null)))
        assertEquals(2, result.missingCount)
        assertEquals(20.0, result.carbGrams, 0.0)
        assertEquals(10.0, result.proteinGrams, 0.0)
        assertEquals(5.0, result.fatGrams, 0.0)
        assertEquals(100, result.carbPercent + result.proteinPercent + result.fatPercent)
    }
}
