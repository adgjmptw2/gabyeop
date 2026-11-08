package io.github.adgjmptw2.diet.data

import androidx.room.withTransaction
import io.github.adgjmptw2.diet.domain.Fast
import io.github.adgjmptw2.diet.domain.Fasting
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FastRepository @Inject constructor(private val db: DietDatabase, private val clock: Clock) {
    val entries = db.fastDao().observeAll().map { rows ->
        rows.map { Fast(it.id, it.startAt, it.endAt, it.targetHours, it.updatedAt, it.deleted) }
    }
    suspend fun start(targetHours: Int) = db.withTransaction {
        require(targetHours in Fasting.TARGET_HOURS)
        if (db.fastDao().active() != null) return@withTransaction
        val now = clock.millis()
        db.fastDao().upsert(FastEntity(UUID.randomUUID().toString(), now, null, targetHours, now))
    }
    suspend fun finish() = db.withTransaction {
        val row = db.fastDao().active() ?: return@withTransaction null
        val now = clock.millis()
        val finished = row.copy(endAt = now.coerceIn(row.startAt, row.startAt + Fasting.MAX_DURATION_MS),
            updatedAt = maxOf(now, row.updatedAt + 1))
        db.fastDao().upsert(finished)
        Fast(finished.id, finished.startAt, finished.endAt, finished.targetHours, finished.updatedAt)
    }
    suspend fun undoFinish(fast: Fast) = db.withTransaction {
        // 다른 기기에서 바뀐 기록이나 새로 시작한 단식은 덮어쓰지 않는다.
        if (db.fastDao().active() != null) return@withTransaction false
        val row = db.fastDao().byId(fast.id) ?: return@withTransaction false
        if (row.deleted || row.updatedAt != fast.updatedAt || row.endAt != fast.endAt) return@withTransaction false
        db.fastDao().upsert(row.copy(endAt = null, updatedAt = maxOf(clock.millis(), row.updatedAt + 1)))
        true
    }
    suspend fun changeTarget(hours: Int) = db.withTransaction {
        require(hours in Fasting.TARGET_HOURS)
        val row = db.fastDao().active() ?: return@withTransaction
        db.fastDao().upsert(row.copy(targetHours = hours, updatedAt = maxOf(clock.millis(), row.updatedAt + 1)))
    }
    suspend fun changeStart(startAt: Long) = db.withTransaction {
        val row = db.fastDao().active() ?: return@withTransaction
        val now = clock.millis()
        require(startAt in (now - Fasting.MAX_DURATION_MS)..now)
        db.fastDao().upsert(row.copy(startAt = startAt, updatedAt = maxOf(now, row.updatedAt + 1)))
    }
}
