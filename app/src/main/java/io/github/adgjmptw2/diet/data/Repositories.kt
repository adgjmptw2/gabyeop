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

    suspend fun save(profile: Profile) {
        db.withTransaction {
            val previous = dao.current()
            val saved = profile.toEntity(clock.millis())
                .copy(startEpochDay = previous?.startEpochDay ?: profile.startDate.toEpochDay())
            dao.upsert(saved)
        }
    }

    suspend fun setCalorieTarget(target: Int?) {
        require(target == null || target in 800..5000)
        db.withTransaction {
            val previous = dao.current() ?: return@withTransaction
            dao.upsert(previous.copy(calorieTarget = target, updatedAt = maxOf(clock.millis(), previous.updatedAt + 1)))
        }
    }

    /** 처음 설정: 프로필과 시작 체중 기록을 한 번에 저장 */
    suspend fun createWithFirstWeight(profile: Profile) {
        db.withTransaction {
            val saved = profile.toEntity(clock.millis())
                .copy(startEpochDay = profile.startDate.toEpochDay())
            dao.upsert(saved)
            weights.save(profile.startDate, profile.startWeightKg)
        }
    }
}

@Singleton
class WeightRepository @Inject constructor(
    private val db: DietDatabase,
    private val clock: Clock,
) {
    private val dao = db.weightDao()

    /** 최신 날짜가 앞 */
    val entries: Flow<List<WeightEntry>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(date: LocalDate, weightKg: Double, bodyFatPct: Double? = null) {
        require(weightKg.isFinite() && weightKg in 30.0..300.0)
        require(bodyFatPct == null || (bodyFatPct.isFinite() && bodyFatPct in 3.0..70.0))
        db.withTransaction {
            val now = clock.millis()
            val existing = dao.findByDay(date.toEpochDay())
            val row = existing?.copy(weightKg = weightKg, bodyFatPct = bodyFatPct,
                updatedAt = maxOf(now, existing.updatedAt + 1), deleted = false)
                ?: WeightEntryEntity(
                    id = UUID.randomUUID().toString(),
                    epochDay = date.toEpochDay(),
                    weightKg = weightKg,
                    createdAt = now,
                    updatedAt = now,
                    bodyFatPct = bodyFatPct,
                )
            dao.upsert(row)
        }
    }

    suspend fun delete(id: String) = db.withTransaction {
        val existing = dao.byId(id) ?: return@withTransaction
        dao.markDeleted(id, maxOf(clock.millis(), existing.updatedAt + 1))
    }
}

private fun ProfileEntity.toDomain() = Profile(
    sex = runCatching { Sex.valueOf(sex) }.getOrDefault(Sex.FEMALE),
    birthYear = birthYear,
    heightCm = heightCm,
    startWeightKg = startWeightKg,
    targetWeightKg = targetWeightKg,
    activity = runCatching { ActivityLevel.valueOf(activity) }.getOrDefault(ActivityLevel.SEDENTARY),
    startDate = LocalDate.ofEpochDay(startEpochDay),
    calorieTarget = calorieTarget,
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
    calorieTarget = calorieTarget,
)

private fun WeightEntryEntity.toDomain() = WeightEntry(id, LocalDate.ofEpochDay(epochDay), weightKg, bodyFatPct)
