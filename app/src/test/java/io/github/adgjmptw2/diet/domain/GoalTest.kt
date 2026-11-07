package io.github.adgjmptw2.diet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalTest {

    @Test
    fun `목표를 향해 절반 줄면 50퍼센트`() {
        val progress = Goal.progress(startKg = 80.0, targetKg = 70.0, currentKg = 75.0)

        assertEquals(50, progress.percent)
        assertEquals(5.0, progress.lostKg, 0.0)
        assertEquals(5.0, progress.remainingKg, 0.0)
        assertFalse(progress.reached)
    }

    @Test
    fun `목표를 넘기면 100퍼센트이고 남은 무게는 0`() {
        val progress = Goal.progress(startKg = 80.0, targetKg = 70.0, currentKg = 69.9)

        assertEquals(100, progress.percent)
        assertTrue(progress.reached)
        assertEquals(10.1, progress.lostKg, 0.0)
        assertEquals(0.0, progress.remainingKg, 0.0)
    }

    @Test
    fun `목표까지 1kg 남으면 90퍼센트`() {
        val progress = Goal.progress(startKg = 80.0, targetKg = 70.0, currentKg = 71.0)

        assertEquals(90, progress.percent)
        assertFalse(progress.reached)
    }

    @Test
    fun `목표 직전 0_1kg 남으면 99퍼센트이고 100은 아니다`() {
        val progress = Goal.progress(startKg = 80.0, targetKg = 70.0, currentKg = 70.1)

        assertEquals(99, progress.percent)
        assertEquals(0.1, progress.remainingKg, 0.0)
        assertFalse(progress.reached)
    }

    @Test
    fun `소수 체중도 오차 없이 내림한다`() {
        val progress = Goal.progress(startKg = 77.1, targetKg = 67.1, currentKg = 74.2)

        assertEquals(29, progress.percent)
        assertEquals(2.9, progress.lostKg, 0.0)
        assertEquals(7.1, progress.remainingKg, 0.0)
    }

    @Test
    fun `체중이 늘어 감량이 음수여도 진행률은 0`() {
        val progress = Goal.progress(startKg = 80.0, targetKg = 70.0, currentKg = 85.0)

        assertEquals(0, progress.percent)
        assertEquals(-5.0, progress.lostKg, 0.0)
        assertEquals(15.0, progress.remainingKg, 0.0)
        assertFalse(progress.reached)
    }

    @Test
    fun `목표에 정확히 닿으면 100퍼센트`() {
        val progress = Goal.progress(startKg = 80.0, targetKg = 70.0, currentKg = 70.0)

        assertEquals(100, progress.percent)
        assertTrue(progress.reached)
    }

    @Test
    fun `시작 체중과 목표가 같아도 나누기 오류 없이 0퍼센트`() {
        val progress = Goal.progress(startKg = 70.0, targetKg = 70.0, currentKg = 71.0)

        assertEquals(0, progress.percent)
        assertFalse(progress.reached)
    }
}