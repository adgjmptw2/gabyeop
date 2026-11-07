package io.github.adgjmptw2.diet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProfileValidatorTest {

    private val today = LocalDate.of(2026, 11, 8)

    private fun input(
        sex: Sex? = Sex.MALE,
        birthYear: String = "1996",
        heightCm: String = "180",
        startWeightKg: String = "80",
        targetWeightKg: String = "70",
        activity: ActivityLevel? = ActivityLevel.SEDENTARY,
    ) = ProfileInput(
        sex = sex,
        birthYear = birthYear,
        heightCm = heightCm,
        startWeightKg = startWeightKg,
        targetWeightKg = targetWeightKg,
        activity = activity,
    )

    @Test
    fun `올바른 입력은 값으로 돌려준다`() {
        val result = ProfileValidator.validate(input(), today)

        assertTrue(result.errors.isEmpty())
        assertNotNull(result.value)
        assertEquals(1996, result.value!!.birthYear)
        assertEquals(180.0, result.value!!.heightCm, 0.0)
        assertEquals(80.0, result.value!!.startWeightKg, 0.0)
        assertEquals(70.0, result.value!!.targetWeightKg, 0.0)
    }

    @Test
    fun `성별과 활동량이 없으면 오류`() {
        val result = ProfileValidator.validate(input(sex = null, activity = null), today)

        assertTrue(Field.SEX in result.errors)
        assertTrue(Field.ACTIVITY in result.errors)
        assertNull(result.value)
    }

    @Test
    fun `출생 연도는 14세부터 100세까지 허용한다`() {
        assertNull(ProfileValidator.validate(input(birthYear = "1926"), today).errors[Field.BIRTH_YEAR])
        assertNull(ProfileValidator.validate(input(birthYear = "2012"), today).errors[Field.BIRTH_YEAR])
    }

    @Test
    fun `출생 연도 경계 밖과 미래는 오류`() {
        assertNotNull(ProfileValidator.validate(input(birthYear = "1925"), today).errors[Field.BIRTH_YEAR])
        assertNotNull(ProfileValidator.validate(input(birthYear = "2013"), today).errors[Field.BIRTH_YEAR])
        assertNotNull(ProfileValidator.validate(input(birthYear = "2027"), today).errors[Field.BIRTH_YEAR])
    }

    @Test
    fun `키는 쉼표와 앞뒤 공백을 받고 100과 250은 허용한다`() {
        assertNull(ProfileValidator.validate(input(heightCm = " 180,5 "), today).errors[Field.HEIGHT])
        assertNull(ProfileValidator.validate(input(heightCm = "100"), today).errors[Field.HEIGHT])
        assertNull(ProfileValidator.validate(input(heightCm = "250"), today).errors[Field.HEIGHT])
    }

    @Test
    fun `키가 범위 밖이거나 유한하지 않은 숫자면 오류`() {
        assertNotNull(ProfileValidator.validate(input(heightCm = "99.9"), today).errors[Field.HEIGHT])
        assertNotNull(ProfileValidator.validate(input(heightCm = "250.1"), today).errors[Field.HEIGHT])
        assertNotNull(ProfileValidator.validate(input(heightCm = "NaN"), today).errors[Field.HEIGHT])
        assertNotNull(ProfileValidator.validate(input(heightCm = "Infinity"), today).errors[Field.HEIGHT])
    }

    @Test
    fun `시작 체중과 목표 체중이 같으면 오류`() {
        val result = ProfileValidator.validate(input(startWeightKg = "80", targetWeightKg = "80"), today)

        assertTrue(Field.TARGET_WEIGHT in result.errors)
        assertNull(result.value)
    }

    @Test
    fun `목표 체중이 시작 체중보다 높으면 오류`() {
        val result = ProfileValidator.validate(input(startWeightKg = "70", targetWeightKg = "80"), today)

        assertTrue(Field.TARGET_WEIGHT in result.errors)
    }

    @Test
    fun `목표 BMI 17 미만이면 오류`() {
        val result = ProfileValidator.validate(input(heightCm = "180", targetWeightKg = "55"), today)

        assertTrue(Field.TARGET_WEIGHT in result.errors)
    }

    @Test
    fun `목표 BMI가 정확히 17이면 허용`() {
        val result = ProfileValidator.validate(input(heightCm = "180", targetWeightKg = "55.08"), today)

        assertFalse(Field.TARGET_WEIGHT in result.errors)
    }

    @Test
    fun `시작 체중이 범위 밖이면 시작 체중 오류`() {
        val result = ProfileValidator.validate(input(startWeightKg = "29.9"), today)

        assertTrue(Field.START_WEIGHT in result.errors)
        assertNull(result.value)
    }
}