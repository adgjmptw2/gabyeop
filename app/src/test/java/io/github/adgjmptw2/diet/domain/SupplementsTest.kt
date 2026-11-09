package io.github.adgjmptw2.diet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `같은 날 같은 영양제 기록은 하나로 센다`() {
        val day = LocalDate.of(2026, 11, 9)
        val map = supplementTakenByDay(listOf(
            SupplementLog(day.toEpochDay(), "a", 1),
            SupplementLog(day.toEpochDay(), "a", 2),
        ))

        assertEquals(setOf("a"), map[day])
    }

    @Test
    fun `날짜별 체크 맵은 기록이 있는 날만 만든다`() {
        val day = LocalDate.of(2026, 11, 9)
        val logs = listOf(
            SupplementLog(day.toEpochDay(), "b", 2),
            SupplementLog(day.toEpochDay(), "a", 1),
            SupplementLog(day.minusDays(1).toEpochDay(), "a", 0),
        )

        val map = supplementTakenByDay(logs)

        assertEquals(setOf("a", "b"), map[day])
        assertEquals(setOf("a"), map[day.minusDays(1)])
        assertNull(map[day.plusDays(1)])
    }
}
