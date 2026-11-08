package io.github.adgjmptw2.diet.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

const val MEMO_MAX_LENGTH = 40
const val NOTE_MAX_LENGTH = 2000

/** A day's notes. [epochDay] is the persistence and sync key; blank notes are allowed. */
data class DailyNote(
    val epochDay: Long,
    val memo: String = "",
    val note: String = "",
) {
    val date: LocalDate
        get() = LocalDate.ofEpochDay(epochDay)

    companion object {
        fun sanitized(epochDay: Long, memo: String = "", note: String = ""): DailyNote =
            DailyNote(epochDay, sanitizeMemo(memo), sanitizeNote(note))

        fun sanitized(date: LocalDate, memo: String = "", note: String = ""): DailyNote =
            sanitized(date.toEpochDay(), memo, note)
    }
}

/** One Monday-to-Sunday cell. Notes are already reduced to a one-line preview for display. */
data class DayCell(
    val date: LocalDate,
    val enabled: Boolean,
    val isToday: Boolean,
    val memoPreview: String? = null,
    val hasDetailNote: Boolean = false,
) {
    val hasNote: Boolean
        get() = memoPreview != null || hasDetailNote
}

fun sanitizeMemo(raw: String): String =
    truncateToCodePoints(collapseWhitespace(raw), MEMO_MAX_LENGTH)

fun sanitizeNote(raw: String): String =
    truncateToCodePoints(
        raw.replace("\r\n", "\n").replace('\r', '\n').trim(),
        NOTE_MAX_LENGTH,
    )

fun truncateToCodePoints(value: String, maxCodePoints: Int): String {
    require(maxCodePoints >= 0) { "maxCodePoints must be non-negative" }
    if (value.codePointCount(0, value.length) <= maxCodePoints) return value
    return value.substring(0, value.offsetByCodePoints(0, maxCodePoints))
}

fun codePointLength(value: String): Int = value.codePointCount(0, value.length)

fun weekCells(today: LocalDate, notes: List<DailyNote> = emptyList()): List<DayCell> {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val cells = (0..6).map { offset ->
        val date = monday.plusDays(offset.toLong())
        DayCell(
            date = date,
            enabled = !date.isAfter(today),
            isToday = date == today,
        )
    }
    return combineNotes(cells, notes)
}

fun combineNotes(cells: List<DayCell>, notes: List<DailyNote>): List<DayCell> {
    val notesByDay = notes
        .filterNot { isDaySummaryEmpty(it) }
        .associateBy { it.epochDay }

    return cells.map { cell ->
        val dailyNote = notesByDay[cell.date.toEpochDay()]
        cell.copy(
            memoPreview = dailyNote?.memo
                ?.let(::sanitizeMemo)
                ?.takeIf { it.isNotEmpty() },
            hasDetailNote = dailyNote?.note
                ?.let(::sanitizeNote)
                ?.isNotEmpty() == true,
        )
    }
}

fun isDaySummaryEmpty(dailyNote: DailyNote?): Boolean =
    dailyNote == null || (dailyNote.memo.isBlank() && dailyNote.note.isBlank())

fun isDaySummaryEmpty(memo: String, note: String): Boolean =
    memo.isBlank() && note.isBlank()

private fun collapseWhitespace(value: String): String {
    val result = StringBuilder(value.length)
    var pendingSpace = false
    for (char in value) {
        if (char.isWhitespace()) {
            pendingSpace = result.isNotEmpty()
        } else {
            if (pendingSpace) result.append(' ')
            result.append(char)
            pendingSpace = false
        }
    }
    return result.toString()
}
