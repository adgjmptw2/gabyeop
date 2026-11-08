package io.github.adgjmptw2.diet.data

import io.github.adgjmptw2.diet.domain.MealEntry
import io.github.adgjmptw2.diet.domain.MealType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealRepository @Inject constructor(
    db: DietDatabase,
    private val clock: Clock,
) {
    private val dao = db.mealDao()

    fun day(date: LocalDate): Flow<List<MealEntry>> =
        dao.observeDay(date.toEpochDay()).map { list -> list.map { it.toDomain() } }

    /** from, to 둘 다 포함 */
    fun range(from: LocalDate, to: LocalDate): Flow<List<MealEntry>> =
        dao.observeRange(from.toEpochDay(), to.toEpochDay()).map { list -> list.map { it.toDomain() } }

    /** 식단을 기록한 날(오름차순) */
    val loggedDays: Flow<List<LocalDate>> = dao.observeLoggedDays().map { d -> d.map(LocalDate::ofEpochDay) }

    /** id가 비어 있으면 새로 만든다. 저장된 id를 돌려줌 */
    suspend fun save(entry: MealEntry): String {
        val now = clock.millis()
        val existing = entry.id.takeIf { it.isNotEmpty() }?.let { dao.byId(it) }
        val id = existing?.id ?: UUID.randomUUID().toString()
        dao.upsert(
            MealEntryEntity(
                id = id,
                epochDay = entry.date.toEpochDay(),
                mealType = entry.type.name,
                foodId = entry.foodId,
                name = entry.name.trim(),
                emoji = entry.emoji,
                amount = entry.amount,
                unit = entry.unit,
                kcal = entry.kcal,
                carb = entry.carb,
                prot = entry.prot,
                fat = entry.fat,
                photoPath = entry.photoPath,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                deleted = false,
            )
        )
        return id
    }

    suspend fun delete(id: String) = dao.markDeleted(id, clock.millis())
}

internal fun MealEntryEntity.toDomain() = MealEntry(
    id = id,
    date = LocalDate.ofEpochDay(epochDay),
    type = runCatching { MealType.valueOf(mealType) }.getOrDefault(MealType.SNACK),
    foodId = foodId,
    name = name,
    emoji = emoji,
    amount = amount,
    unit = unit,
    kcal = kcal,
    carb = carb,
    prot = prot,
    fat = fat,
    photoPath = photoPath,
)
