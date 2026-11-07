package io.github.adgjmptw2.diet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BadgesTest {
    private val today = LocalDate.of(2026, 11, 8)
    private val profile = insightProfile()

    private fun states(weights: List<WeightEntry> = emptyList(), meals: List<LocalDate> = emptyList()) =
        badges(profile, weights, meals, today).associateBy { it.id }

    @Test
    fun noRecordsEarnNothingAndStartWithZeroProgress() {
        states().values.forEach {
            assertNull(it.earnedOn)
            assertEquals(0.0, it.progress, 1e-9)
        }
    }

    @Test
    fun allNineBadgesHaveStableUniqueIds() {
        assertEquals(
            setOf("first_record", "streak_7", "streak_30", "loss_1kg", "loss_5_percent", "loss_10_percent", "halfway", "goal", "meals_7"),
            states().keys,
        )
    }

    @Test
    fun firstRecordUsesChronologicalDate() {
        val first = today.minusDays(5)
        val result = states(listOf(insightWeight(today, 78.0), insightWeight(first, 80.0)))
        assertEquals(first, result.getValue("first_record").earnedOn)
    }

    @Test
    fun sevenDayStreakIsEarnedOnSeventhDay() {
        val first = today.minusDays(9)
        val entries = (0L..9L).map { insightWeight(first.plusDays(it), 80.0) }.reversed()
        assertEquals(first.plusDays(6), states(entries).getValue("streak_7").earnedOn)
    }

    @Test
    fun thirtyDayStreakIsEarnedOnThirtiethDay() {
        val first = today.minusDays(34)
        val entries = (0L..34L).map { insightWeight(first.plusDays(it), 80.0) }
        assertEquals(first.plusDays(29), states(entries).getValue("streak_30").earnedOn)
    }

    @Test
    fun aMissingCalendarDayResetsStreak() {
        val entries = listOf(0L, 1L, 2L, 4L, 5L, 6L, 7L).map { insightWeight(today.minusDays(it), 80.0) }
        val badge = states(entries).getValue("streak_7")
        assertNull(badge.earnedOn)
        assertEquals(3.0 / 7, badge.progress, 1e-9)
    }

    @Test
    fun duplicateDatesDoNotExtendStreak() {
        val entries = (0L..5L).map { insightWeight(today.minusDays(it), 80.0) }
        val badge = states(entries + entries.first()).getValue("streak_7")
        assertNull(badge.earnedOn)
        assertEquals(6.0 / 7, badge.progress, 1e-9)
    }

    @Test
    fun streakThroughYesterdayCanStillContinueToday() {
        val entries = (1L..3L).map { insightWeight(today.minusDays(it), 80.0) }
        assertEquals(3.0 / 7, states(entries).getValue("streak_7").progress, 1e-9)
    }

    @Test
    fun expiredUnfinishedStreakHasZeroProgress() {
        val entries = (2L..7L).map { insightWeight(today.minusDays(it), 80.0) }
        val badge = states(entries).getValue("streak_7")
        assertNull(badge.earnedOn)
        assertEquals(0.0, badge.progress, 1e-9)
    }

    @Test
    fun earnedStreakRemainsEarnedAfterLongGap() {
        val first = today.minusDays(30)
        val entries = (0L..6L).map { insightWeight(first.plusDays(it), 80.0) }
        val badge = states(entries).getValue("streak_7")
        assertEquals(first.plusDays(6), badge.earnedOn)
        assertEquals(1.0, badge.progress, 1e-9)
    }

    @Test
    fun oneKgBadgeUsesFirstThresholdCrossingDespiteRebound() {
        val entries = listOf(
            insightWeight(today, 78.0),
            insightWeight(today.minusDays(1), 79.5),
            insightWeight(today.minusDays(2), 79.0),
            insightWeight(today.minusDays(3), 79.4),
        )
        assertEquals(today.minusDays(2), states(entries).getValue("loss_1kg").earnedOn)
    }

    @Test
    fun percentageBadgesUseStartingWeight() {
        val entries = listOf(insightWeight(today.minusDays(2), 76.0), insightWeight(today.minusDays(1), 72.0), insightWeight(today, 78.0))
        val result = states(entries)
        assertEquals(today.minusDays(2), result.getValue("loss_5_percent").earnedOn)
        assertEquals(today.minusDays(1), result.getValue("loss_10_percent").earnedOn)
    }

    @Test
    fun halfwayUsesHalfOfTheActualGoalDistance() {
        val entries = listOf(insightWeight(today.minusDays(1), 71.0), insightWeight(today, 70.0))
        val badge = states(entries).getValue("halfway")
        assertEquals(today, badge.earnedOn)
        assertEquals(1.0, badge.progress, 1e-9)
    }

    @Test
    fun goalUsesEarliestDateAtOrBelowTarget() {
        val entries = listOf(insightWeight(today, 59.0), insightWeight(today.minusDays(1), 65.0), insightWeight(today.minusDays(2), 60.0))
        assertEquals(today.minusDays(2), states(entries).getValue("goal").earnedOn)
    }

    @Test
    fun unearnedLossProgressReflectsBestRecordedLoss() {
        val entries = listOf(insightWeight(today.minusDays(1), 78.0), insightWeight(today, 79.0))
        val badge = states(entries).getValue("loss_5_percent")
        assertNull(badge.earnedOn)
        assertEquals(0.5, badge.progress, 1e-9)
    }

    @Test
    fun mealBadgeCountsDistinctNonconsecutiveDaysAndUsesSeventhDate() {
        val days = (0L..7L).map { today.minusDays(20).plusDays(it * 2) }
        val badge = states(meals = days.reversed() + days.first()).getValue("meals_7")
        assertEquals(days[6], badge.earnedOn)
        assertEquals(1.0, badge.progress, 1e-9)
    }

    @Test
    fun futureRecordsDoNotEarnWeightOrMealBadges() {
        val future = today.plusDays(1)
        val result = states(listOf(insightWeight(future, 50.0)), (0L..6L).map { future.plusDays(it) })
        result.values.forEach { assertNull(it.earnedOn) }
        assertEquals(0.0, result.getValue("meals_7").progress, 1e-9)
    }

    @Test
    fun progressIsAlwaysWithinZeroAndOne() {
        val gaining = states(listOf(insightWeight(today, 100.0)))
        val reached = states(listOf(insightWeight(today, 50.0)), (0L..20L).map { today.minusDays(it) })
        (gaining.values + reached.values).forEach { assertTrue(it.progress in 0.0..1.0) }
    }

    @Test
    fun zeroGoalDistanceDoesNotDivideByZeroOrAwardHalfway() {
        val result = badges(insightProfile(targetKg = 80.0), listOf(insightWeight(today, 80.0)), emptyList(), today).associateBy { it.id }
        assertNull(result.getValue("halfway").earnedOn)
        assertEquals(0.0, result.getValue("halfway").progress, 1e-9)
        assertNotNull(result.getValue("goal").earnedOn)
        result.values.forEach { assertTrue(it.progress.isFinite()) }
    }

    @Test
    fun profileStartingWeightAloneIsNotARecordedWeight() {
        val result = badges(insightProfile(targetKg = 80.0), emptyList(), listOf(today), today).associateBy { it.id }
        assertNull(result.getValue("first_record").earnedOn)
        assertNull(result.getValue("goal").earnedOn)
        assertEquals(1.0 / 7, result.getValue("meals_7").progress, 1e-9)
    }
}
