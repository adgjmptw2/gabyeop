package io.github.adgjmptw2.diet.domain

import org.junit.Assert.*
import org.junit.Test

class HydrationTest {
    @Test fun newInstallKeepsOne250MlGlassAndAutomaticGoal() {
        assertEquals(WaterSettings(250, null), waterSettingsFromStored())
        assertEquals(WaterSettings(), waterSettingsFromStored())
    }
    @Test fun legacyGlassAndManualTargetSurviveUpgrade() {
        listOf(150, 200, 250, 300, 500).forEach { old ->
            assertEquals(WaterSettings(old, 2750), waterSettingsFromStored(glassMl = old, targetMl = 2750))
        }
    }
    @Test fun arbitraryGlassSizesKeepTheSameStorageContract() {
        listOf(50, 51, 175, 850, 1999, 2000).forEach { ml ->
            assertEquals(WaterSettings(ml, 2200), waterSettingsFromStored(ml, 2200))
        }
    }
    @Test fun outOfRangeStoredSizesAreClamped() {
        assertEquals(50, waterSettingsFromStored(glassMl = 49).glassMl)
        assertEquals(2000, waterSettingsFromStored(glassMl = 2001).glassMl)
    }
    @Test fun automaticGoalKeepsExistingWeightRule() {
        assertEquals(1500, waterTargetMl(30.0))
        assertEquals(2000, waterTargetMl(65.0))
        assertEquals(3500, waterTargetMl(200.0))
    }
}
