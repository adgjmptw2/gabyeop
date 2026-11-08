package io.github.adgjmptw2.diet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class DayNotesTest {
    @Test fun waterOnlyDayIsNotEmptyAndDoesNotAddAMemoDot() {
        val day = LocalDate.of(2026, 11, 8)
        val note = DailyNote(day.toEpochDay(), waterMl = 250)
        assertFalse(isDaySummaryEmpty(note))
        assertFalse(weekCells(day, listOf(note)).last().hasNote)
    }

    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `week starts on Monday and contains seven consecutive dates`() {
        val cells = weekCells(monday.plusDays(2))

        assertEquals(7, cells.size)
        assertEquals(DayOfWeek.MONDAY, cells.first().date.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, cells.last().date.dayOfWeek)
        assertEquals((0L..6L).map { monday.plusDays(it) }, cells.map { it.date })
    }

    @Test
    fun `Sunday keeps the previous Monday to Sunday week and enables every day`() {
        val sunday = monday.plusDays(6)
        val cells = weekCells(sunday)

        assertEquals(monday, cells.first().date)
        assertEquals(sunday, cells.last().date)
        assertTrue(cells.all { it.enabled })
        assertEquals(listOf(sunday), cells.filter { it.isToday }.map { it.date })
    }

    @Test
    fun `January first stays in the week that began in the previous year`() {
        val newYear = LocalDate.of(2027, 1, 1)
        val cells = weekCells(newYear)

        assertEquals(LocalDate.of(2026, 12, 28), cells.first().date)
        assertEquals(LocalDate.of(2027, 1, 3), cells.last().date)
        assertEquals(
            listOf(true, true, true, true, true, false, false),
            cells.map { it.enabled },
        )
        assertEquals(newYear, cells.single { it.isToday }.date)
    }

    @Test
    fun `when today is Monday only Monday is enabled`() {
        val cells = weekCells(monday)

        assertEquals(
            listOf(true, false, false, false, false, false, false),
            cells.map { it.enabled },
        )
        assertEquals(monday, cells.single { it.isToday }.date)
    }

    @Test
    fun `future days are disabled`() {
        val today = monday.plusDays(2)
        val cells = weekCells(today)

        assertTrue(cells.take(3).all { it.enabled })
        assertTrue(cells.drop(3).none { it.enabled })
        assertTrue(cells.drop(3).none { it.isToday })
        assertEquals(today, cells.single { it.isToday }.date)
    }

    @Test
    fun `memo sanitization collapses lines and extra whitespace`() {
        assertEquals("first second third", sanitizeMemo("  first\r\nsecond\t\t third  "))
        assertEquals("", sanitizeMemo(" \r\n\t "))
    }

    @Test
    fun `memo truncates at forty Unicode code points`() {
        val forty = "a".repeat(MEMO_MAX_LENGTH)
        val emoji = "\uD83D\uDE00"

        assertEquals(forty, sanitizeMemo(forty))
        assertEquals(forty, sanitizeMemo(forty + "b"))
        assertEquals(1, codePointLength(emoji))
        assertEquals(emoji.repeat(MEMO_MAX_LENGTH), sanitizeMemo(emoji.repeat(MEMO_MAX_LENGTH) + "x"))
        val truncated = sanitizeMemo(emoji.repeat(MEMO_MAX_LENGTH + 1))
        assertEquals(MEMO_MAX_LENGTH, truncated.codePointCount(0, truncated.length))
    }

    @Test
    fun `detail note normalizes CRLF and trims without collapsing line breaks`() {
        assertEquals(
            "first\n\nsecond line",
            sanitizeNote(" \r\nfirst\r\n\r\nsecond line\r  "),
        )
        assertEquals("first   line\n second", sanitizeNote("  first   line\n second  "))
        assertEquals("", sanitizeNote(" \r\n\t "))
    }

    @Test
    fun `detail note truncates safely at two thousand code points`() {
        val emoji = "\uD83D\uDE00"

        val truncated = sanitizeNote(emoji.repeat(NOTE_MAX_LENGTH + 1))
        assertEquals(NOTE_MAX_LENGTH, truncated.codePointCount(0, truncated.length))
        assertEquals(emoji.repeat(NOTE_MAX_LENGTH), sanitizeNote(emoji.repeat(NOTE_MAX_LENGTH) + "x"))
    }

    @Test
    fun `sanitized model stores epoch day and normalized text`() {
        val note = DailyNote.sanitized(
            date = monday,
            memo = "  one\r\nline  ",
            note = " first\r\nsecond ",
        )

        assertEquals(monday.toEpochDay(), note.epochDay)
        assertEquals(monday, note.date)
        assertEquals("one line", note.memo)
        assertEquals("first\nsecond", note.note)
    }

    @Test
    fun `combining notes adds memo preview and detail flag to matching cells`() {
        val today = monday.plusDays(2)
        val notes = listOf(
            DailyNote(monday.toEpochDay(), memo = "  one\r\nline  ", note = "details"),
            DailyNote(monday.plusDays(1).toEpochDay(), memo = " \t ", note = "\r\n"),
            DailyNote(today.toEpochDay(), note = "detail only"),
        )

        val cells = combineNotes(weekCells(today), notes)

        assertEquals("one line", cells[0].memoPreview)
        assertTrue(cells[0].hasDetailNote)
        assertTrue(cells[0].hasNote)
        assertNull(cells[1].memoPreview)
        assertFalse(cells[1].hasNote)
        assertNull(cells[2].memoPreview)
        assertTrue(cells[2].hasDetailNote)
        assertNull(cells[3].memoPreview)
        assertFalse(cells[3].hasNote)
    }

    @Test
    fun `empty day summary treats null and blank notes as empty`() {
        assertTrue(isDaySummaryEmpty(null as DailyNote?))
        assertTrue(isDaySummaryEmpty(DailyNote(monday.toEpochDay())))
        assertTrue(isDaySummaryEmpty(DailyNote(monday.toEpochDay(), memo = " \r\n", note = "\t")))
        assertTrue(isDaySummaryEmpty("  ", "\r\n"))
        assertFalse(isDaySummaryEmpty("memo", ""))
        assertFalse(isDaySummaryEmpty("", "detail"))
    }
}
