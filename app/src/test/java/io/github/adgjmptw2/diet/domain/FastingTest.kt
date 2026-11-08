package io.github.adgjmptw2.diet.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class FastingTest {
    @Test fun everyTargetFromOneToSeventyTwoHoursHasCorrectProgress() {
        assertTrue(1 in Fasting.TARGET_HOURS)
        assertTrue(72 in Fasting.TARGET_HOURS)
        assertFalse(0 in Fasting.TARGET_HOURS)
        assertFalse(73 in Fasting.TARGET_HOURS)
        Fasting.TARGET_HOURS.forEach { hours ->
            val target = hours * Fasting.HOUR_MS
            assertEquals(0.5f, Fasting.progress(target / 2, hours), 0.0001f)
            assertEquals(1f, Fasting.progress(target, hours), 0f)
            assertEquals(1f, Fasting.progress(target + Fasting.HOUR_MS, hours), 0f)
        }
        assertEquals("72시간 0분", Fasting.durationText(72 * Fasting.HOUR_MS))
        assertEquals(5, Fasting.stageIndex(72 * Fasting.HOUR_MS))
    }
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
    @Test fun startTimeMustBeInPastSevenDays() {
        val now = 10 * Fasting.MAX_DURATION_MS
        assertTrue(Fasting.validStart(now, now))
        assertTrue(Fasting.validStart(now - 3 * Fasting.HOUR_MS - 25 * 60_000, now))
        assertTrue(Fasting.validStart(now - Fasting.MAX_DURATION_MS, now))
        assertFalse(Fasting.validStart(now + 60_000, now))
        assertFalse(Fasting.validStart(now - Fasting.MAX_DURATION_MS - 1, now))
    }
}
