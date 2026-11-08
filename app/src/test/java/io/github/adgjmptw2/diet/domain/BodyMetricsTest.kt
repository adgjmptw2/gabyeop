package io.github.adgjmptw2.diet.domain

import org.junit.Assert.*
import org.junit.Test

class BodyMetricsTest {
    @Test fun bmiAndDeurenbergUseHeightAgeAndSex() {
        val bmi = BodyMetrics.bmi(70.0, 175.0)
        assertEquals(22.857142, bmi, 0.00001)
        assertEquals(BodyMetrics.estimateBodyFat(bmi, 30, Sex.FEMALE) - 10.8,
            BodyMetrics.estimateBodyFat(bmi, 30, Sex.MALE), 0.00001)
    }
    @Test fun koreanBmiCategoryBoundaries() {
        listOf(18.49 to "저체중", 18.5 to "정상", 23.0 to "비만 전단계",
            25.0 to "1단계 비만", 30.0 to "2단계 비만", 35.0 to "3단계 비만").forEach { (value, label) ->
            assertEquals(label, BodyMetrics.category(value))
        }
    }
    @Test fun bodyFatAllowsOnlyManualRangeAndOneDecimal() {
        assertEquals(3.0, BodyMetrics.parseBodyFat("3")!!, 0.0)
        assertEquals(70.0, BodyMetrics.parseBodyFat("70")!!, 0.0)
        assertEquals(28.5, BodyMetrics.parseBodyFat("28,46")!!, 0.0)
        listOf("", "NaN", "2.9", "70.1", "Infinity").forEach { assertNull(BodyMetrics.parseBodyFat(it)) }
    }
    @Test fun waterTargetRoundsToAGlassAndHasBounds() {
        assertEquals(1500, waterTargetMl(30.0))
        assertEquals(2000, waterTargetMl(65.0))
        assertEquals(2250, waterTargetMl(75.0))
        assertEquals(3500, waterTargetMl(200.0))
    }
}
