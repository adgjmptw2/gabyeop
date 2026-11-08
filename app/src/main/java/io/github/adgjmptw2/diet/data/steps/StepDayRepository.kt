package io.github.adgjmptw2.diet.data.steps

import io.github.adgjmptw2.diet.data.DietDatabase
import io.github.adgjmptw2.diet.data.StepDayEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepDayRepository @Inject constructor(
    private val db: DietDatabase,
    private val clock: Clock,
) {
    private val dao = db.stepDayDao()

    fun observe(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> =
        dao.observeRange(from.toEpochDay(), to.toEpochDay()).map { rows ->
            rows.associate { LocalDate.ofEpochDay(it.epochDay) to it.steps }
        }

    suspend fun range(from: LocalDate, to: LocalDate): List<StepDayEntity> =
        dao.range(from.toEpochDay(), to.toEpochDay())

    suspend fun save(date: LocalDate, steps: Int, source: String) {
        if (steps < 0) return
        dao.upsert(StepDayEntity(date.toEpochDay(), steps, source, clock.millis()))
    }

    /** Health Connect가 읽은 값은 출처가 달라도 덮어쓴다. 센서 값은 더 클 때만 남긴다. */
    suspend fun merge(date: LocalDate, steps: Int, source: String) {
        if (steps <= 0) return
        val existing = dao.byDay(date.toEpochDay())
        if (existing == null || existing.source != source || steps > existing.steps) save(date, steps, source)
    }

    suspend fun clear() = dao.clear()
}
