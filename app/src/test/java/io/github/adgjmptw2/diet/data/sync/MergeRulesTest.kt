package io.github.adgjmptw2.diet.data.sync

import io.github.adgjmptw2.diet.data.MealEntryEntity
import io.github.adgjmptw2.diet.data.DayNoteEntity
import io.github.adgjmptw2.diet.data.ProfileEntity
import io.github.adgjmptw2.diet.data.WeightEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MergeRulesTest {
    private fun weight(id: String = "a", time: Long = 10, day: Long = 100, deleted: Boolean = false) =
        WeightEntryEntity(id, day, 65.0, 1, time, deleted)
    private fun meal(time: Long, photo: String? = null, deleted: Boolean = false) =
        MealEntryEntity("meal", 100, "LUNCH", null, "밥", "🍚", 100.0, "g", 150.0,
            null, null, null, photo, 1, time, deleted)
    private fun profile(time: Long) = ProfileEntity(0, "FEMALE", 1990, 160.0, 65.0, 60.0, "LIGHT", 100, time)
    private fun note(time: Long, memo: String = "local", deleted: Boolean = false) =
        DayNoteEntity(100, memo, "detail", time, deleted)

    @Test fun equalOrOlderProfileKeepsLocal() {
        assertNull(MergeRules.profile(profile(10), profile(10)))
        assertNull(MergeRules.profile(profile(10), profile(9)))
    }
    @Test fun newProfilePreservesTimestamp() {
        assertEquals(profile(20), MergeRules.profile(profile(10), profile(20)))
        assertEquals(profile(20), MergeRules.profile(null, profile(20)))
    }
    @Test fun updatedMealPreservesDevicePhotoAndServerTimestamp() {
        val result = MergeRules.meal(meal(10, "/local/photo.jpg"), meal(20))
        assertEquals("/local/photo.jpg", result?.photoPath)
        assertEquals(20L, result?.updatedAt)
    }
    @Test fun mealDeletionAlsoPreservesDevicePhoto() {
        assertEquals(meal(20, "/photo", true), MergeRules.meal(meal(10, "/photo"), meal(20, deleted = true)))
    }
    @Test fun newMealHasNoRemotePhotoPath() {
        assertNull(MergeRules.meal(null, meal(20, "/remote/photo"))?.photoPath)
    }
    @Test fun equalOrOlderMealKeepsLocal() {
        assertNull(MergeRules.meal(meal(10), meal(10)))
        assertNull(MergeRules.meal(meal(10), meal(9)))
    }
    @Test fun sameWeightIdRequiresStrictlyNewerTimestamp() {
        assertNull(MergeRules.weight(weight(), weight(), weight(time = 10)))
        assertNull(MergeRules.weight(weight(), weight(), weight(time = 9)))
        assertEquals(weight(time = 20), MergeRules.weight(weight(), weight(), weight(time = 20))?.upsert)
    }
    @Test fun newerWeightForSameDayReplacesOtherId() {
        val result = MergeRules.weight(null, weight(), weight("b", 20))
        assertEquals("a", result?.deleteId)
        assertEquals(weight("b", 20), result?.upsert)
    }
    @Test fun equalOrOlderWeightForSameDayIsIgnored() {
        assertNull(MergeRules.weight(null, weight(), weight("b", 10)))
        assertNull(MergeRules.weight(null, weight(), weight("b", 9)))
    }
    @Test fun tombstoneUsesTheSameTimestampRules() {
        assertEquals(weight(time = 20, deleted = true),
            MergeRules.weight(weight(), weight(), weight(time = 20, deleted = true))?.upsert)
        assertNull(MergeRules.weight(null, weight(), weight("b", 9, deleted = true)))
    }
    @Test fun movingAnExistingIdDoesNotOverwriteANewerDayConflict() {
        assertNull(MergeRules.weight(weight(day = 99), weight("b", 30), weight(time = 20)))
    }
    @Test fun newerNoteReplacesTheLocalDay() {
        val incoming = note(20, memo = "remote")
        assertEquals(incoming, MergeRules.note(note(10), incoming))
    }
    @Test fun equalOrOlderNoteKeepsTheLocalDay() {
        assertNull(MergeRules.note(note(10), note(10, memo = "remote")))
        assertNull(MergeRules.note(note(10), note(9, memo = "remote")))
    }
    @Test fun newNoteIsAcceptedWithoutALocalRow() {
        val incoming = note(20, memo = "remote")
        assertEquals(incoming, MergeRules.note(null, incoming))
    }
    @Test fun noteTombstoneKeepsTheRemovalFlag() {
        val incoming = note(20, memo = "", deleted = true)
        assertEquals(incoming, MergeRules.note(note(10), incoming))
    }
}
