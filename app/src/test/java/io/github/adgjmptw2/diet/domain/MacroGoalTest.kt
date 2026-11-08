package io.github.adgjmptw2.diet.domain

import org.junit.Assert.*
import org.junit.Test

class MacroGoalTest {
    @Test fun balancedUsesFourFourNineKcalPerGram() {
        assertEquals(MacroGoal(225, 90, 60), macroGoal(1800, MacroPreset.BALANCED))
        assertEquals(1800, macroGoal(1800, MacroPreset.BALANCED).energyKcal)
    }
    @Test fun presetsRoundEachNutrientIndependently() {
        assertEquals(MacroGoal(180, 135, 60), macroGoal(1800, MacroPreset.PROTEIN))
        assertEquals(MacroGoal(113, 158, 80), macroGoal(1800, MacroPreset.LOW_CARB))
        assertEquals(1804, macroGoal(1800, MacroPreset.LOW_CARB).energyKcal)
        assertEquals(MacroGoal(188, 75, 50), macroGoal(1500, MacroPreset.BALANCED))
    }
    @Test fun missingGoalsAndExplicitZeroAreDifferent() {
        assertTrue(MacroGoal().isEmpty)
        assertFalse(MacroGoal(carbGrams = 0).isEmpty)
        assertEquals(400, MacroGoal(proteinGrams = 100).energyKcal)
        assertEquals(MacroGoal(0, 0, 0), macroGoal(0, MacroPreset.BALANCED))
    }
    @Test fun presetsStayInsideEditableRange() {
        MacroPreset.entries.forEach { preset ->
            val goal = macroGoal(5000, preset)
            assertTrue(listOf(goal.carbGrams, goal.proteinGrams, goal.fatGrams).all { it != null && it in 0..1000 })
        }
    }

    @Test fun absentGoalsAlwaysResolveToBalancedRecommendation() {
        assertEquals(MacroGoal(225, 90, 60), MacroGoal().resolved(1800))
        assertEquals(MacroGoal(138, 55, 37), MacroGoal().resolved(1100))
        assertEquals(MacroGoal(1, 0, 0), macroGoal(4)) // 탄수화물 0.5 g은 1 g으로 반올림
    }

    @Test fun directGoalsOverrideRecommendationsIncludingZeroAndLimits() {
        val direct = MacroGoal(0, 1000, 85)
        assertEquals(direct, direct.resolved(1800))
        assertEquals(direct, direct.resolved(2400))
    }

    @Test fun partialStoredGoalsOnlyFillMissingNutrients() {
        val direct = MacroGoal(proteinGrams = 120)
        assertEquals(MacroGoal(225, 120, 60), direct.resolved(1800))
        assertEquals(MacroGoal(300, 120, 80), direct.resolved(2400))
    }

    @Test fun clearingDirectValuesReturnsToCurrentCalorieRecommendation() {
        val direct = MacroGoal(175, 145, 70)
        assertEquals(direct, direct.resolved(1800))
        val cleared = MacroGoal()
        assertTrue(cleared.isEmpty)
        assertEquals(macroGoal(1800), cleared.resolved(1800))
        assertEquals(macroGoal(2400), cleared.resolved(2400))
    }

    @Test fun savingRecommendedNumbersMakesThemExplicitUntilReset() {
        val saved = MacroGoal().resolved(1800)
        assertEquals(MacroGoal(225, 90, 60), saved.resolved(2400))
        assertEquals(MacroGoal(300, 120, 80), MacroGoal().resolved(2400))
    }
}
