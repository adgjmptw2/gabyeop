package io.github.adgjmptw2.diet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SupplementsTest {

    @Test
    fun `이름은 앞뒤 공백을 빼고 남긴다`() {
        assertEquals("비타민D", parseSupplementName("  비타민D  "))
    }

    @Test
    fun `빈 이름은 받지 않는다`() {
        assertNull(parseSupplementName("   "))
        assertNull(parseSupplementName(""))
    }

    @Test
    fun `이름은 1자부터 20자까지 받는다`() {
        assertEquals("가", parseSupplementName("가"))
        assertEquals("가".repeat(20), parseSupplementName("가".repeat(20)))
    }

    @Test
    fun `20자를 넘는 이름은 받지 않는다`() {
        assertNull(parseSupplementName("가".repeat(21)))
    }

    @Test
    fun `이모지도 글자 수로 센다`() {
        val emoji = "\uD83D\uDE00"
        assertEquals(emoji.repeat(20), parseSupplementName(emoji.repeat(20)))
        assertNull(parseSupplementName(emoji.repeat(21)))
    }

    @Test
    fun `대소문자와 공백만 다른 이름은 중복이다`() {
        assertTrue(hasDuplicateSupplementName("Vitamin D", listOf("vitamin  d")))
        assertTrue(hasDuplicateSupplementName(" 비타민D ", listOf("비타민D")))
        assertFalse(hasDuplicateSupplementName("비타민D", listOf("오메가3")))
    }

    @Test
    fun `위로 이동하면 바로 앞 항목과 자리를 바꾼다`() {
        val ids = listOf("a", "b", "c")
        assertEquals(SupplementMove("b", 1), supplementMove(ids, "c", -1))
        assertEquals(SupplementMove("b", -1), supplementMove(ids, "a", 1))
    }

    @Test
    fun `맨 위에서 올리거나 맨 아래에서 내리면 이동하지 않는다`() {
        val ids = listOf("a", "b", "c")
        assertNull(supplementMove(ids, "a", -1))
        assertNull(supplementMove(ids, "c", 1))
        assertNull(supplementMove(ids, "missing", 1))
    }

    @Test
    fun `중복 검사는 이미 있는 이름도 같은 규칙으로 본다`() {
        assertTrue(hasDuplicateSupplementName("오메가 3", listOf("오메가3", "오메가 3")))
        assertTrue(hasDuplicateSupplementName("VITAMIN D", listOf("Vitamin d")))
    }

    @Test
    fun `때 비트는 아침 점심 저녁을 더하고 뺀다`() {
        assertEquals(1, SUPPLEMENT_SLOT_MORNING)
        assertEquals(2, SUPPLEMENT_SLOT_LUNCH)
        assertEquals(4, SUPPLEMENT_SLOT_EVENING)
        assertEquals(7, SUPPLEMENT_SLOTS_ALL)
        assertEquals(3, toggleSupplementSlot(SUPPLEMENT_SLOT_MORNING, SUPPLEMENT_SLOT_LUNCH))
        assertEquals(1, toggleSupplementSlot(3, SUPPLEMENT_SLOT_LUNCH))
        assertEquals(1, toggleSupplementSlot(5, SUPPLEMENT_SLOT_EVENING))
    }

    @Test
    fun `마지막 한 때는 끄지 않는다`() {
        assertEquals(2, toggleSupplementSlot(2, 2))
        assertEquals(4, toggleSupplementSlot(4, 4))
        assertEquals(2, toggleSupplementSlot(3, 1))
        assertNotEquals(0, toggleSupplementSlot(1, 1))
    }

    @Test
    fun `잘못된 때 값은 받지 않는다`() {
        assertEquals(1, normalizeSupplementSlots(1))
        assertEquals(7, normalizeSupplementSlots(7))
        assertEquals(3, normalizeSupplementSlots(3 or 8))
        assertTrue(runCatching { normalizeSupplementSlots(0) }.isFailure)
        assertTrue(runCatching { normalizeSupplementSlots(8) }.isFailure)
    }

    @Test
    fun `나누기 켬 카운트는 때별 체크 수와 전체 때별 수다`() {
        val supplements = listOf(
            supplement("a", 3),
            supplement("b", 4),
        )
        val checks = setOf(
            SupplementCheck("a", 1),
            SupplementCheck("b", 4),
        )

        assertEquals(SupplementProgress(2, 3), supplementProgress(supplements, checks, splitByTime = true))
        assertEquals(SupplementProgress(3, 3),
            supplementProgress(supplements, checks + SupplementCheck("a", 2), splitByTime = true))
    }

    @Test
    fun `나누기 끔 카운트는 모든 때를 채운 영양제 수다`() {
        val supplements = listOf(
            supplement("a", 3),
            supplement("b", 1),
            supplement("c", 7),
        )
        val checks = setOf(
            SupplementCheck("a", 1),
            SupplementCheck("a", 2),
            SupplementCheck("b", 1),
            SupplementCheck("c", 1),
            SupplementCheck("c", 4),
        )

        assertEquals(SupplementProgress(2, 3), supplementProgress(supplements, checks, splitByTime = false))
    }

    @Test
    fun `통합 토글은 빠진 때를 모두 채운다`() {
        val supplement = supplement("a", 5)
        val plan = supplementToggleAllPlan(supplement, setOf(SupplementCheck("a", 4)))

        assertEquals(setOf(1), plan.slotsToAdd)
        assertFalse(plan.removeAll)
    }

    @Test
    fun `통합 토글은 모두 체크돼 있으면 그날 기록을 지운다`() {
        val supplement = supplement("a", 5)
        val checks = setOf(SupplementCheck("a", 1), SupplementCheck("a", 4))
        val plan = supplementToggleAllPlan(supplement, checks)

        assertTrue(plan.slotsToAdd.isEmpty())
        assertTrue(plan.removeAll)
    }

    @Test
    fun `통합 토글은 아무 때도 없으면 그날 모든 때를 채운다`() {
        val supplement = supplement("오메가3", 2)
        val plan = supplementToggleAllPlan(supplement, emptySet())

        assertEquals(setOf(2), plan.slotsToAdd)
        assertFalse(plan.removeAll)
    }

    @Test
    fun `같은 날 같은 때의 기록은 하나로 센다`() {
        val day = LocalDate.of(2026, 11, 9)
        val map = supplementTakenByDay(listOf(
            SupplementLog(day.toEpochDay(), "a", 1, 10),
            SupplementLog(day.toEpochDay(), "a", 1, 20),
            SupplementLog(day.toEpochDay(), "a", 2, 30),
        ))

        assertEquals(setOf(SupplementCheck("a", 1), SupplementCheck("a", 2)), map[day])
    }

    @Test
    fun `날짜별 체크 맵은 기록이 있는 날만 만든다`() {
        val day = LocalDate.of(2026, 11, 9)
        val logs = listOf(
            SupplementLog(day.toEpochDay(), "b", 2, 2),
            SupplementLog(day.toEpochDay(), "a", 1, 3),
            SupplementLog(day.minusDays(1).toEpochDay(), "a", 1, 4),
        )

        val map = supplementTakenByDay(logs)

        assertEquals(setOf(SupplementCheck("a", 1), SupplementCheck("b", 2)), map[day])
        assertEquals(setOf(SupplementCheck("a", 1)), map[day.minusDays(1)])
        assertNull(map[day.plusDays(1)])
    }

    private fun supplement(id: String, slots: Int) = Supplement(
        id = id, name = id, slots = slots, sortOrder = 0, createdAt = 0, updatedAt = 0,
    )
}
