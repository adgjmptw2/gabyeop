package io.github.adgjmptw2.diet.data.sync

import io.github.adgjmptw2.diet.data.MealEntryEntity
import io.github.adgjmptw2.diet.data.ProfileEntity
import io.github.adgjmptw2.diet.data.WeightEntryEntity

/** Equal timestamps keep the device row; incoming timestamps are never rewritten. */
object MergeRules {
    fun profile(local: ProfileEntity?, incoming: ProfileEntity): ProfileEntity? =
        incoming.takeIf { local == null || it.updatedAt > local.updatedAt }

    fun meal(local: MealEntryEntity?, incoming: MealEntryEntity): MealEntryEntity? =
        incoming.takeIf { local == null || it.updatedAt > local.updatedAt }
            ?.copy(photoPath = local?.photoPath)

    data class WeightChange(val upsert: WeightEntryEntity, val deleteId: String? = null)

    fun weight(
        sameId: WeightEntryEntity?, sameDay: WeightEntryEntity?, incoming: WeightEntryEntity,
    ): WeightChange? {
        if (sameId != null && incoming.updatedAt <= sameId.updatedAt) return null
        if (sameDay != null && sameDay.id != incoming.id) {
            if (incoming.updatedAt <= sameDay.updatedAt) return null
            return WeightChange(incoming, sameDay.id)
        }
        return WeightChange(incoming)
    }
}
