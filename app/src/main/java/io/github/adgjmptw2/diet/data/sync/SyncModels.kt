package io.github.adgjmptw2.diet.data.sync

import io.github.adgjmptw2.diet.data.MealEntryEntity
import io.github.adgjmptw2.diet.data.DayNoteEntity
import io.github.adgjmptw2.diet.data.FastEntity
import io.github.adgjmptw2.diet.data.ProfileEntity
import io.github.adgjmptw2.diet.data.WeightEntryEntity
import kotlinx.serialization.Serializable

@Serializable
data class SyncRequest(
    val since: Long,
    val profile: SyncProfile? = null,
    val weights: List<SyncWeight> = emptyList(),
    val meals: List<SyncMeal> = emptyList(),
    val notes: List<SyncNote> = emptyList(),
    val fasts: List<SyncFast> = emptyList(),
)

@Serializable
data class SyncResponse(
    val cursor: Long,
    val profile: SyncProfile? = null,
    val weights: List<SyncWeight> = emptyList(),
    val meals: List<SyncMeal> = emptyList(),
    val notes: List<SyncNote> = emptyList(),
    val more: Boolean = false,
    val rejected: List<String> = emptyList(),
    val fasts: List<SyncFast> = emptyList(),
    val protocol: Int = 1,
)

internal fun SyncResponse.requireCurrentProtocol() {
    if (protocol < 2) throw ServerUpdateRequiredException()
}

class ServerUpdateRequiredException : java.io.IOException("서버 업데이트를 기다리는 중이에요")

@Serializable
data class SyncProfile(
    val sex: String, val birthYear: Int, val heightCm: Double,
    val startWeightKg: Double, val targetWeightKg: Double, val activity: String,
    val startEpochDay: Long, val updatedAt: Long,
    val calorieTarget: Int? = null,
) {
    fun entity() = ProfileEntity(0, sex, birthYear, heightCm, startWeightKg, targetWeightKg, activity, startEpochDay, updatedAt, calorieTarget)
}

@Serializable
data class SyncWeight(
    val id: String, val epochDay: Long, val weightKg: Double,
    val createdAt: Long, val updatedAt: Long, val deleted: Boolean,
    val bodyFatPct: Double? = null,
) {
    fun entity() = WeightEntryEntity(id, epochDay, weightKg, createdAt, updatedAt, deleted, bodyFatPct)
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

@Serializable
data class SyncNote(
    val epochDay: Long, val memo: String, val note: String,
    val updatedAt: Long, val deleted: Boolean,
    val waterMl: Int = 0,
) {
    fun entity() = DayNoteEntity(epochDay, memo, note, updatedAt,
        deleted || (memo.isBlank() && note.isBlank() && waterMl == 0), waterMl)
}

@Serializable
data class SyncFast(
    val id: String, val startAt: Long, val endAt: Long? = null, val targetHours: Int,
    val updatedAt: Long, val deleted: Boolean,
) {
    fun entity() = FastEntity(id, startAt, endAt, targetHours, updatedAt, deleted)
}

internal fun ProfileEntity.wire() = SyncProfile(sex, birthYear, heightCm, startWeightKg, targetWeightKg, activity, startEpochDay, updatedAt, calorieTarget)
internal fun WeightEntryEntity.wire() = SyncWeight(id, epochDay, weightKg, createdAt, updatedAt, deleted, bodyFatPct)
internal fun MealEntryEntity.wire() = SyncMeal(id, epochDay, mealType, foodId, name, emoji, amount, unit,
    kcal, carb, prot, fat, createdAt, updatedAt, deleted)
internal fun DayNoteEntity.wire() = SyncNote(epochDay, memo, note, updatedAt, deleted, waterMl)
internal fun FastEntity.wire() = SyncFast(id, startAt, endAt, targetHours, updatedAt, deleted)
