package io.github.adgjmptw2.diet.data.sync

import io.github.adgjmptw2.diet.data.MealEntryEntity
import io.github.adgjmptw2.diet.data.ProfileEntity
import io.github.adgjmptw2.diet.data.WeightEntryEntity
import kotlinx.serialization.Serializable

@Serializable
data class SyncRequest(
    val since: Long,
    val profile: SyncProfile? = null,
    val weights: List<SyncWeight> = emptyList(),
    val meals: List<SyncMeal> = emptyList(),
)

@Serializable
data class SyncResponse(
    val cursor: Long,
    val profile: SyncProfile? = null,
    val weights: List<SyncWeight> = emptyList(),
    val meals: List<SyncMeal> = emptyList(),
    val more: Boolean = false,
)

@Serializable
data class SyncProfile(
    val sex: String, val birthYear: Int, val heightCm: Double,
    val startWeightKg: Double, val targetWeightKg: Double, val activity: String,
    val startEpochDay: Long, val updatedAt: Long,
) {
    fun entity() = ProfileEntity(0, sex, birthYear, heightCm, startWeightKg, targetWeightKg, activity, startEpochDay, updatedAt)
}

@Serializable
data class SyncWeight(
    val id: String, val epochDay: Long, val weightKg: Double,
    val createdAt: Long, val updatedAt: Long, val deleted: Boolean,
) {
    fun entity() = WeightEntryEntity(id, epochDay, weightKg, createdAt, updatedAt, deleted)
}

@Serializable
data class SyncMeal(
    val id: String, val epochDay: Long, val mealType: String, val foodId: String? = null,
    val name: String, val emoji: String, val amount: Double, val unit: String, val kcal: Double,
    val carb: Double? = null, val prot: Double? = null, val fat: Double? = null,
    val createdAt: Long, val updatedAt: Long, val deleted: Boolean,
) {
    fun entity() = MealEntryEntity(id, epochDay, mealType, foodId, name, emoji, amount, unit, kcal,
        carb, prot, fat, null, createdAt, updatedAt, deleted)
}

internal fun ProfileEntity.wire() = SyncProfile(sex, birthYear, heightCm, startWeightKg, targetWeightKg, activity, startEpochDay, updatedAt)
internal fun WeightEntryEntity.wire() = SyncWeight(id, epochDay, weightKg, createdAt, updatedAt, deleted)
internal fun MealEntryEntity.wire() = SyncMeal(id, epochDay, mealType, foodId, name, emoji, amount, unit,
    kcal, carb, prot, fat, createdAt, updatedAt, deleted)
