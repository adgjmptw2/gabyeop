package io.github.adgjmptw2.diet.data.sync

import io.github.adgjmptw2.diet.data.FastEntity
import io.github.adgjmptw2.diet.data.DayNoteEntity
import io.github.adgjmptw2.diet.data.WeightEntryEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SyncCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @Test fun oldPayloadsUseNullableAndEmptyDefaults() {
        val response = json.decodeFromString<SyncResponse>("""{"cursor":1,"weights":[{"id":"w","epochDay":1,"weightKg":65.0,"createdAt":1,"updatedAt":1,"deleted":false}],"notes":[{"epochDay":1,"memo":"","note":"","updatedAt":1,"deleted":false}]}""")
        assertEquals(1, response.protocol)
        assertNull(response.weights.single().bodyFatPct)
        assertEquals(0, response.notes.single().waterMl)
        assertTrue(response.fasts.isEmpty())
        val profile = json.decodeFromString<SyncProfile>("""{"sex":"FEMALE","birthYear":1990,"heightCm":165.0,"startWeightKg":70.0,"targetWeightKg":60.0,"activity":"LIGHT","startEpochDay":1,"updatedAt":1}""")
        assertNull(profile.calorieTarget)
    }
    @Test fun refusesLegacyServerResponsesBeforeTheyCanBeAppliedOrAcknowledged() {
        val legacy = json.decodeFromString<SyncResponse>("""{"cursor":999}""")
        val failure = runCatching { legacy.requireCurrentProtocol() }.exceptionOrNull()
        assertTrue(failure is ServerUpdateRequiredException)
        assertEquals("서버 업데이트를 기다리는 중이에요", failure?.message)
        json.decodeFromString<SyncResponse>("""{"protocol":2,"cursor":999}""").requireCurrentProtocol()
    }
    @Test fun upgradePullRestoresNewFieldsAtEqualRevisionsWithoutReplacingOldFields() {
        val profile = SyncProfile("FEMALE", 1990, 165.0, 70.0, 60.0, "LIGHT", 1, 10).entity()
        val remoteProfile = profile.copy(calorieTarget = 1500, targetWeightKg = 55.0)
        assertNull(MergeRules.profile(profile, remoteProfile))
        assertEquals(profile.copy(calorieTarget = 1500), MergeRules.profile(profile, remoteProfile, restoreNewFields = true))

        val weight = WeightEntryEntity("w", 1, 65.0, 1, 10)
        val remoteWeight = weight.copy(bodyFatPct = 28.5, weightKg = 60.0)
        assertNull(MergeRules.weight(weight, weight, remoteWeight))
        assertEquals(weight.copy(bodyFatPct = 28.5),
            MergeRules.weight(weight, weight, remoteWeight, restoreNewFields = true)?.upsert)

        val note = DayNoteEntity(1, "", "", 10, deleted = true)
        val remoteNote = note.copy(waterMl = 1250, deleted = false)
        assertNull(MergeRules.note(note, remoteNote))
        assertEquals(remoteNote, MergeRules.note(note, remoteNote, restoreNewFields = true))
    }

    @Test fun upgradePullStillKeepsNewerLocalEdits() {
        val local = WeightEntryEntity("w", 1, 65.0, 1, 20, bodyFatPct = 30.0)
        assertNull(MergeRules.weight(local, local, local.copy(updatedAt = 10, bodyFatPct = 28.5), restoreNewFields = true))
    }
    @Test fun newFieldsRoundTripThroughWireAndRoomModels() {
        val fast = FastEntity("f", 1, 20, 16, 30, false)
        val request = SyncRequest(0,
            profile = SyncProfile("FEMALE", 1990, 165.0, 70.0, 60.0, "LIGHT", 1, 2, 1500),
            weights = listOf(SyncWeight("w", 1, 65.0, 1, 2, false, 28.5)),
            notes = listOf(SyncNote(1, "", "", 2, false, 1250)), fasts = listOf(fast.wire()))
        val restored = json.decodeFromString<SyncRequest>(json.encodeToString(request))
        assertEquals(fast, restored.fasts.single().entity())
        assertEquals(1500, restored.profile!!.entity().calorieTarget)
        assertEquals(28.5, restored.weights.single().entity().bodyFatPct!!, 0.0)
        assertEquals(1250, restored.notes.single().entity().waterMl)
        assertFalse(restored.notes.single().entity().deleted)
        assertTrue(SyncNote(1, "", "", 3, false).entity().deleted)
    }
    @Test fun fastMergeKeepsEqualTimestampAndAcceptsEndOrDeletion() {
        val local = FastEntity("f", 1, null, 16, 10)
        assertNull(MergeRules.fast(local, local.copy(endAt = 20)))
        assertEquals(20L, MergeRules.fast(local, local.copy(endAt = 20, updatedAt = 11))?.endAt)
        assertTrue(MergeRules.fast(local, local.copy(updatedAt = 11, deleted = true))!!.deleted)
    }
}
