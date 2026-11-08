package io.github.adgjmptw2.diet.data.sync

import io.github.adgjmptw2.diet.data.MealEntryEntity
import io.github.adgjmptw2.diet.data.DayNoteEntity
import io.github.adgjmptw2.diet.data.FastEntity
import io.github.adgjmptw2.diet.data.ProfileEntity
import io.github.adgjmptw2.diet.data.WeightEntryEntity

/** Equal timestamps keep the device row, except new fields restored during a protocol upgrade. */
object MergeRules {
    fun fast(local: FastEntity?, incoming: FastEntity): FastEntity? =
        incoming.takeIf { local == null || it.updatedAt > local.updatedAt }

    fun profile(local: ProfileEntity?, incoming: ProfileEntity, restoreNewFields: Boolean = false): ProfileEntity? {
        if (local != null && restoreNewFields && incoming.updatedAt == local.updatedAt) {
            return local.copy(calorieTarget = incoming.calorieTarget).takeIf { it != local }
        }
        return incoming.takeIf { local == null || it.updatedAt > local.updatedAt }
    }

    fun meal(local: MealEntryEntity?, incoming: MealEntryEntity): MealEntryEntity? =
        incoming.takeIf { local == null || it.updatedAt > local.updatedAt }
            ?.copy(photoPath = local?.photoPath)

    fun note(local: DayNoteEntity?, incoming: DayNoteEntity, restoreNewFields: Boolean = false): DayNoteEntity? {
        if (local != null && restoreNewFields && incoming.updatedAt == local.updatedAt) {
            return local.copy(waterMl = incoming.waterMl, deleted = incoming.deleted).takeIf { it != local }
        }
        return incoming.takeIf { local == null || it.updatedAt > local.updatedAt }
    }

    data class WeightChange(val upsert: WeightEntryEntity, val deleteId: String? = null)

    fun weight(
        sameId: WeightEntryEntity?, sameDay: WeightEntryEntity?, incoming: WeightEntryEntity,
        restoreNewFields: Boolean = false,
    ): WeightChange? {
        if (sameId != null && restoreNewFields && incoming.updatedAt == sameId.updatedAt) {
            return sameId.copy(bodyFatPct = incoming.bodyFatPct).takeIf { it != sameId }?.let { WeightChange(it) }
        }
        if (sameId != null && incoming.updatedAt <= sameId.updatedAt) return null
        if (sameDay != null && sameDay.id != incoming.id) {
            if (incoming.updatedAt <= sameDay.updatedAt) return null
            return WeightChange(incoming, sameDay.id)
        }
        return WeightChange(incoming)
    }
}
