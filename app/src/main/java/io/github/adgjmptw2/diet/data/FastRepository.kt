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
        require(targetHours in 1..72)
        if (db.fastDao().active() != null) return@withTransaction
        val now = clock.millis()
        db.fastDao().upsert(FastEntity(UUID.randomUUID().toString(), now, null, targetHours, now))
    }
    suspend fun finish() = db.withTransaction {
        val row = db.fastDao().active() ?: return@withTransaction
        val now = clock.millis()
        db.fastDao().upsert(row.copy(endAt = now.coerceIn(row.startAt, row.startAt + Fasting.MAX_DURATION_MS),
            updatedAt = maxOf(now, row.updatedAt + 1)))
    }
    suspend fun changeStart(startAt: Long) = db.withTransaction {
        val row = db.fastDao().active() ?: return@withTransaction
        val now = clock.millis()
        require(startAt in (now - Fasting.MAX_DURATION_MS)..now)
        db.fastDao().upsert(row.copy(startAt = startAt, updatedAt = maxOf(now, row.updatedAt + 1)))
    }
}
