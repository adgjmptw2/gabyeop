package io.github.adgjmptw2.diet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CaloriesTest {

    private val today = LocalDate.of(2026, 11, 8)

    private fun profile(
        sex: Sex,
        birthYear: Int,
        heightCm: Double,
        activity: ActivityLevel,
    ) = Profile(
        sex = sex,
        birthYear = birthYear,
        heightCm = heightCm,
        startWeightKg = 80.0,
        targetWeightKg = 70.0,
        activity = activity,
        startDate = LocalDate.of(2026, 1, 1),
    )

    @Test
    fun `남성 기초대사량은 Mifflin St Jeor 손계산과 같다`() {
        val bmr = Calories.bmr(Sex.MALE, weightKg = 80.0, heightCm = 180.0, age = 30)

        assertEquals(1780.0, bmr, 0.0)
    }

    @Test
    fun `여성 기초대사량은 Mifflin St Jeor 손계산과 같다`() {
        val bmr = Calories.bmr(Sex.FEMALE, weightKg = 60.0, heightCm = 165.0, age = 25)

        assertEquals(1345.25, bmr, 0.0)
    }

    @Test
    fun `남성 최소 섭취량은 1500이고 여성은 1200`() {
        assertEquals(1500, Calories.minimum(Sex.MALE))
        assertEquals(1200, Calories.minimum(Sex.FEMALE))
    }

    @Test
    fun `남성 감량 목표는 활동량을 곱하고 500을 뺀 뒤 10단위로 반올림`() {
        val target = Calories.target(
            profile = profile(Sex.MALE, 1996, 180.0, ActivityLevel.LIGHT),
            currentKg = 80.0,
            today = today,
            goalReached = false,
        )

        assertEquals(1780, target.bmr)
        assertEquals(2448, target.tdee)
        assertEquals(1950, target.target)
        assertFalse(target.raisedToMinimum)
    }

    @Test
    fun `여성 감량 목표는 최소 섭취량 아래면 1200으로 올린다`() {
        val target = Calories.target(
            profile = profile(Sex.FEMALE, 2001, 165.0, ActivityLevel.SEDENTARY),
            currentKg = 60.0,
            today = today,
            goalReached = false,
        )

        assertEquals(1345, target.bmr)
        assertEquals(1614, target.tdee)
        assertEquals(1200, target.target)
        assertTrue(target.raisedToMinimum)
    }

    @Test
    fun `목표 달성 뒤에는 500을 빼지 않는다`() {
        val target = Calories.target(
            profile = profile(Sex.MALE, 1996, 180.0, ActivityLevel.SEDENTARY),
            currentKg = 80.0,
            today = today,
            goalReached = true,
        )

        assertEquals(2136, target.tdee)
        assertEquals(2140, target.target)
        assertFalse(target.raisedToMinimum)
    }

    @Test
    fun `목표 달성 뒤에도 10단위로 반올림`() {
        val target = Calories.target(
            profile = profile(Sex.FEMALE, 2001, 165.0, ActivityLevel.SEDENTARY),
            currentKg = 60.0,
            today = today,
            goalReached = true,
        )

        assertEquals(1614, target.tdee)
        assertEquals(1610, target.target)
        assertFalse(target.raisedToMinimum)
    }

    @Test
    fun `남성도 최소 섭취량 1500 아래면 1500으로 올린다`() {
        val target = Calories.target(
            profile = profile(Sex.MALE, 1996, 165.0, ActivityLevel.SEDENTARY),
            currentKg = 75.0,
            today = today,
            goalReached = false,
        )

        assertEquals(1636, target.bmr)
        assertEquals(1964, target.tdee)
        assertEquals(1500, target.target)
        assertTrue(target.raisedToMinimum)
    }

    @Test
    fun `나이는 출생 연도와 오늘 연도의 차이로 계산한다`() {
        val target = Calories.target(
            profile = profile(Sex.MALE, 1996, 180.0, ActivityLevel.SEDENTARY),
            currentKg = 80.0,
            today = today,
            goalReached = false,
        )

        assertEquals(1780, target.bmr)
    }

    @Test
    fun `반올림 결과가 최소 섭취량보다 낮아지면 최소값을 지킨다`() {
        val target = Calories.target(
            profile = profile(Sex.FEMALE, 2001, 165.0, ActivityLevel.LIGHT),
            currentKg = 60.0,
            today = today,
            goalReached = false,
        )

        assertEquals(1850, target.tdee)
        assertEquals(1350, target.target)
        assertFalse(target.raisedToMinimum)
    }
}