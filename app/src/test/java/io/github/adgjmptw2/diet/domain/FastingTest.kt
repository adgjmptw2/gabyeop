package io.github.adgjmptw2.diet.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class FastingTest {
    @Test fun stagesChangeExactlyAtEachBoundary() {
        val boundaries = listOf(4, 8, 12, 18, 24)
        boundaries.forEachIndexed { index, hour ->
            val millis = hour * Fasting.HOUR_MS
            assertEquals(index, Fasting.stageIndex(millis - 1))
            assertEquals(index + 1, Fasting.stageIndex(millis))
        }
        assertEquals(0, Fasting.stageIndex(-1))
    }
    @Test fun midnightDoesNotResetElapsedTime() {
        val start = ZonedDateTime.parse("2026-11-07T20:30:00+09:00").toInstant().toEpochMilli()
        val end = ZonedDateTime.parse("2026-11-08T12:40:00+09:00").toInstant().toEpochMilli()
        val elapsed = Fasting.elapsedMillis(start, end)
        assertEquals("16시간 10분", Fasting.durationText(elapsed))
        assertEquals(3, Fasting.stageIndex(elapsed))
        assertEquals(1f, Fasting.progress(elapsed, 16), 0f)
    }
    @Test fun finishedFastKeepsItsDurationAndProgressStopsAtGoal() {
        assertEquals(16 * Fasting.HOUR_MS, Fasting.elapsedMillis(0, 30 * Fasting.HOUR_MS, 16 * Fasting.HOUR_MS))
        assertEquals(1f, Fasting.progress(20 * Fasting.HOUR_MS, 16), 0f)
        assertEquals(0f, Fasting.progress(-1, 16), 0f)
        assertEquals(0L, Fasting.elapsedMillis(100, 0))
    }
}
