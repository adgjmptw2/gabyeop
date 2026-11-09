package io.github.adgjmptw2.diet.domain

import org.junit.Assert.*
import org.junit.Test

class PastFastingTest {
    private val hour = Fasting.HOUR_MS
    private val now = 100 * Fasting.MAX_DURATION_MS
    private val original = Fast("past", now - 20 * hour, now - 4 * hour, 16, now - hour)

    private fun error(start: Long = original.startAt, end: Long = requireNotNull(original.endAt),
        hours: Int = 16, others: List<Fast> = emptyList()): PastFastError? =
        Fasting.pastError(original.id, start, end, hours, others, now)

    @Test fun endMustBeStrictlyAfterStart() {
        assertEquals(PastFastError.ORDER, error(end = original.startAt))
        assertEquals(PastFastError.ORDER, error(end = original.startAt - 1))
        assertNull(error(end = original.startAt + 1))
    }

    @Test fun endCanBeNowButCannotBeInFuture() {
        assertNull(error(end = now))
        assertEquals(PastFastError.FUTURE, error(end = now + 1))
    }

    @Test fun sevenDaysIsAllowedButOneMoreMillisecondIsNot() {
        val end = requireNotNull(original.endAt)
        assertNull(error(start = end - Fasting.MAX_DURATION_MS))
        assertEquals(PastFastError.DURATION, error(start = end - Fasting.MAX_DURATION_MS - 1))
        assertEquals(PastFastError.DURATION,
            Fasting.pastError("past", Long.MIN_VALUE, Long.MAX_VALUE, 16, emptyList(), Long.MAX_VALUE))
    }

    @Test fun oldHistoryHasNoSevenDayLookbackLimit() {
        val end = now - 30 * 24 * hour
        assertNull(error(start = end - 16 * hour, end = end))
    }

    @Test fun overlapIncludesPartialContainedAndContainingIntervals() {
        val start = original.startAt
        val end = requireNotNull(original.endAt)
        val intervals = listOf(
            (start - hour) to (start + 1),
            (end - 1) to (end + hour),
            (start + hour) to (end - hour),
            (start - hour) to (end + hour),
            start to end,
        )
        intervals.forEach { (otherStart, otherEnd) ->
            val other = Fast("other", otherStart, otherEnd, 16, now)
            assertEquals(PastFastError.OVERLAP, error(others = listOf(other)))
        }
    }

    @Test fun touchingEitherBoundaryDoesNotOverlap() {
        val before = Fast("before", original.startAt - hour, original.startAt, 16, now)
        val after = Fast("after", requireNotNull(original.endAt), now, 16, now)
        assertNull(error(others = listOf(before, after)))
    }

    @Test fun ongoingFastOverlapsUntilItEndsButMayStartAtThePastEnd() {
        val active = Fast("active", requireNotNull(original.endAt) - 1, null, 16, now)
        assertEquals(PastFastError.OVERLAP, error(others = listOf(active)))
        assertNull(error(others = listOf(active.copy(startAt = requireNotNull(original.endAt)))))
        assertEquals(PastFastError.OVERLAP, error(others = listOf(active.copy(startAt = original.startAt - hour))))
    }

    @Test fun selfAndDeletedRecordsDoNotConflict() {
        val deleted = original.copy(id = "deleted", deleted = true)
        val deletedActive = original.copy(id = "deleted-active", endAt = null, deleted = true)
        assertNull(error(others = listOf(original, deleted, deletedActive)))
    }

    @Test fun emptyOrReversedOtherRecordsHaveNoIntervalToOverlap() {
        val middle = original.startAt + hour
        val empty = Fast("empty", middle, middle, 16, now)
        val reversed = empty.copy(id = "reversed", endAt = middle - 1)
        assertNull(error(others = listOf(empty, reversed)))
    }

    @Test fun targetRangeIsOneToSeventyTwoHours() {
        assertNull(error(hours = 1))
        assertNull(error(hours = 72))
        assertEquals(PastFastError.TARGET, error(hours = 0))
        assertEquals(PastFastError.TARGET, error(hours = 73))
    }

    @Test fun deleteKeepsTheRecordAndRaisesTimestampToWallClock() {
        val deleted = requireNotNull(Fasting.deletePast(original, now))
        assertEquals(original.copy(deleted = true, updatedAt = now), deleted)
    }

    @Test fun deleteAndRestoreStayNewerWithEqualOrRegressingClock() {
        listOf(original.updatedAt, original.updatedAt - hour).forEach { clockNow ->
            val deleted = requireNotNull(Fasting.deletePast(original, clockNow))
            assertEquals(original.updatedAt + 1, deleted.updatedAt)
            val restored = requireNotNull(Fasting.restorePast(deleted, deleted, clockNow))
            assertEquals(original.copy(updatedAt = original.updatedAt + 2), restored)
        }
    }

    @Test fun restoreRaisesTimestampToWallClockAndClearsOnlyTombstone() {
        val deleted = requireNotNull(Fasting.deletePast(original, now))
        val later = now + hour
        assertEquals(original.copy(updatedAt = later), Fasting.restorePast(deleted, deleted, later))
    }

    @Test fun restoreRejectsChangesSinceDeletionEvenIfTheRowIsStillDeleted() {
        val deleted = requireNotNull(Fasting.deletePast(original, now))
        assertNull(Fasting.restorePast(deleted.copy(updatedAt = deleted.updatedAt + 1), deleted, now))
        assertNull(Fasting.restorePast(deleted.copy(targetHours = 18), deleted, now))
        assertNull(Fasting.restorePast(deleted.copy(deleted = false), deleted, now))
        assertNull(Fasting.restorePast(deleted.copy(id = "other"), deleted, now))
    }

    @Test fun oldUndoCannotRestoreASecondDeletion() {
        val firstDeletion = requireNotNull(Fasting.deletePast(original, now))
        val restored = requireNotNull(Fasting.restorePast(firstDeletion, firstDeletion, now))
        val secondDeletion = requireNotNull(Fasting.deletePast(restored, now))
        assertNull(Fasting.restorePast(secondDeletion, firstDeletion, now))
        assertNotNull(Fasting.restorePast(secondDeletion, secondDeletion, now))
    }

    @Test fun deleteAndRestoreRejectOngoingOrAlreadyLiveRecords() {
        val active = original.copy(endAt = null)
        assertNull(Fasting.deletePast(active, now))
        assertNull(Fasting.deletePast(original.copy(deleted = true), now))
        assertNull(Fasting.restorePast(original, original, now))
        assertNull(Fasting.restorePast(active.copy(deleted = true), active.copy(deleted = true), now))
    }
}
