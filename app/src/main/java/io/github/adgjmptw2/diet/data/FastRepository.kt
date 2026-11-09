package io.github.adgjmptw2.diet.data

import androidx.room.withTransaction
import io.github.adgjmptw2.diet.domain.Fast
import io.github.adgjmptw2.diet.domain.Fasting
import io.github.adgjmptw2.diet.domain.PastFastError
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FastRepository @Inject constructor(private val db: DietDatabase, private val clock: Clock) {
    val entries = db.fastDao().observeAll().map { rows ->
        rows.map { it.fast() }
    }
    /** startAt이 없으면 지금. 지금 이후·7일 넘게 전이면 지금으로 */
    suspend fun start(targetHours: Int, startAt: Long? = null) = db.withTransaction {
        require(targetHours in Fasting.TARGET_HOURS)
        if (db.fastDao().active() != null) return@withTransaction
        val now = clock.millis()
        val begin = startAt?.takeIf { Fasting.validStart(it, now) } ?: now
        db.fastDao().upsert(FastEntity(UUID.randomUUID().toString(), begin, null, targetHours, now))
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

    suspend fun updatePast(id: String, startAt: Long, endAt: Long, targetHours: Int): PastFastError? = db.withTransaction {
        val row = db.fastDao().byId(id)?.takeIf { !it.deleted && it.endAt != null }
            ?: return@withTransaction PastFastError.CHANGED
        val now = clock.millis()
        // 기존 DAO로 완료 기록과 진행 중 기록을 모두 읽어 저장 직전에 검사한다.
        val others = (db.fastDao().exportRows() + db.fastDao().activeRows()).map { it.fast() }
        Fasting.pastError(id, startAt, endAt, targetHours, others, now)?.let { return@withTransaction it }
        db.fastDao().upsert(row.copy(startAt = startAt, endAt = endAt, targetHours = targetHours,
            updatedAt = maxOf(now, row.updatedAt + 1)))
        null
    }

    /** 되돌리기에는 삭제 전 행이 아니라 저장한 tombstone을 넘긴다. */
    suspend fun delete(id: String): Fast? = db.withTransaction {
        val row = db.fastDao().byId(id) ?: return@withTransaction null
        val deleted = Fasting.deletePast(row.fast(), clock.millis()) ?: return@withTransaction null
        db.fastDao().upsert(row.copy(deleted = deleted.deleted, updatedAt = deleted.updatedAt))
        deleted
    }

    suspend fun restore(fast: Fast): Boolean = db.withTransaction {
        val row = db.fastDao().byId(fast.id) ?: return@withTransaction false
        val restored = Fasting.restorePast(row.fast(), fast, clock.millis()) ?: return@withTransaction false
        db.fastDao().upsert(row.copy(deleted = restored.deleted, updatedAt = restored.updatedAt))
        true
    }
}

private fun FastEntity.fast() = Fast(id, startAt, endAt, targetHours, updatedAt, deleted)
