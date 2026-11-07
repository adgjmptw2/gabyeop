package io.github.adgjmptw2.diet.data

import androidx.room.withTransaction
import io.github.adgjmptw2.diet.domain.ActivityLevel
import io.github.adgjmptw2.diet.domain.Profile
import io.github.adgjmptw2.diet.domain.Sex
import io.github.adgjmptw2.diet.domain.WeightEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val db: DietDatabase,
    private val weights: WeightRepository,
    private val clock: Clock,
) {
    private val dao = db.profileDao()

    val profile: Flow<Profile?> = dao.observe().map { it?.toDomain() }

    suspend fun save(profile: Profile) = dao.upsert(profile.toEntity(clock.millis()))

    /** 처음 설정: 프로필과 시작 체중 기록을 한 번에 저장 */
    suspend fun createWithFirstWeight(profile: Profile) {
        db.withTransaction {
            save(profile)
            weights.save(profile.startDate, profile.startWeightKg)
        }
    }
}

@Singleton
class WeightRepository @Inject constructor(
    db: DietDatabase,
    private val clock: Clock,
) {
    private val dao = db.weightDao()

    /** 최신 날짜가 앞 */
    val entries: Flow<List<WeightEntry>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(date: LocalDate, weightKg: Double) {
        val now = clock.millis()
        val existing = dao.findByDay(date.toEpochDay())
        val row = existing?.copy(weightKg = weightKg, updatedAt = now, deleted = false)
            ?: WeightEntryEntity(
                id = UUID.randomUUID().toString(),
                epochDay = date.toEpochDay(),
                weightKg = weightKg,
                createdAt = now,
                updatedAt = now,
            )
        dao.upsert(row)
    }

    suspend fun delete(id: String) = dao.markDeleted(id, clock.millis())
}

private fun ProfileEntity.toDomain() = Profile(
    sex = runCatching { Sex.valueOf(sex) }.getOrDefault(Sex.FEMALE),
    birthYear = birthYear,
    heightCm = heightCm,
    startWeightKg = startWeightKg,
    targetWeightKg = targetWeightKg,
    activity = runCatching { ActivityLevel.valueOf(activity) }.getOrDefault(ActivityLevel.SEDENTARY),
    startDate = LocalDate.ofEpochDay(startEpochDay),
)

private fun Profile.toEntity(now: Long) = ProfileEntity(
    sex = sex.name,
    birthYear = birthYear,
    heightCm = heightCm,
    startWeightKg = startWeightKg,
    targetWeightKg = targetWeightKg,
    activity = activity.name,
    startEpochDay = startDate.toEpochDay(),
    updatedAt = now,
)

private fun WeightEntryEntity.toDomain() = WeightEntry(id, LocalDate.ofEpochDay(epochDay), weightKg)
