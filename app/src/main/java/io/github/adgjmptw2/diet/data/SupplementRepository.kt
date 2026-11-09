package io.github.adgjmptw2.diet.data

import androidx.room.withTransaction
import io.github.adgjmptw2.diet.domain.Supplement
import io.github.adgjmptw2.diet.domain.hasDuplicateSupplementName
import io.github.adgjmptw2.diet.domain.parseSupplementName
import io.github.adgjmptw2.diet.domain.supplementMove
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupplementRepository @Inject constructor(
    private val db: DietDatabase,
    private val clock: Clock,
) {
    private val dao = db.supplementDao()

    fun observeSupplements(): Flow<List<Supplement>> = dao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    fun observeDay(date: LocalDate): Flow<Set<String>> =
        dao.observeDayIds(date.toEpochDay()).map { it.toSet() }

    /** from, to 둘 다 포함. 기록이 없는 날은 맵에 없다. */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Set<String>>> =
        dao.observeRange(from.toEpochDay(), to.toEpochDay()).map { rows ->
            rows.groupBy { LocalDate.ofEpochDay(it.epochDay) }
                .mapValues { (_, dayRows) -> dayRows.mapTo(mutableSetOf()) { it.supplementId } }
        }

    suspend fun add(name: String) = db.withTransaction {
        val clean = requireNotNull(parseSupplementName(name)) { "이름을 확인해 주세요" }
        val existing = dao.all()
        require(existing.size < MAX_SUPPLEMENTS) { "영양제는 20개까지 등록할 수 있어요" }
        require(!hasDuplicateSupplementName(clean, existing.map { it.name })) { "같은 이름의 영양제가 있어요" }
        val now = clock.millis()
        val order = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        dao.insert(SupplementEntity(UUID.randomUUID().toString(), clean, order, now, now))
    }

    suspend fun rename(id: String, name: String) = db.withTransaction {
        val clean = requireNotNull(parseSupplementName(name)) { "이름을 확인해 주세요" }
        val row = dao.byId(id)?.takeUnless { it.deleted } ?: return@withTransaction
        val others = dao.all().filter { it.id != id }
        require(!hasDuplicateSupplementName(clean, others.map { it.name })) { "같은 이름의 영양제가 있어요" }
        dao.upsert(row.copy(name = clean, updatedAt = maxOf(clock.millis(), row.updatedAt + 1)))
    }

    /** 삭제 전 행을 돌려준다. 체크 기록은 지우지 않아 되돌리면 그대로 살아난다. */
    suspend fun delete(id: String): SupplementEntity? = db.withTransaction {
        val row = dao.byId(id)?.takeUnless { it.deleted } ?: return@withTransaction null
        dao.markDeleted(id, maxOf(clock.millis(), row.updatedAt + 1))
        row
    }

    suspend fun undoDelete(row: SupplementEntity) = db.withTransaction {
        val current = dao.byId(row.id)?.takeIf { it.deleted } ?: return@withTransaction
        val active = dao.all()
        require(active.size < MAX_SUPPLEMENTS) { "영양제는 20개까지 등록할 수 있어요" }
        require(!hasDuplicateSupplementName(current.name, active.map { it.name })) {
            "같은 이름의 영양제가 있어요"
        }
        val order = active.maxOfOrNull { it.sortOrder }?.let { it + 1 } ?: row.sortOrder
        dao.upsert(current.copy(deleted = false, sortOrder = order,
            updatedAt = maxOf(clock.millis(), current.updatedAt + 1)))
    }

    suspend fun move(id: String, up: Boolean) = db.withTransaction {
        val rows = dao.all()
        val move = supplementMove(rows.map { it.id }, id, if (up) -1 else 1)
            ?: return@withTransaction
        val now = clock.millis()
        val first = rows.first { it.id == id }
        val second = rows.first { it.id == move.id }
        val low = if (first.sortOrder <= second.sortOrder) first else second
        val high = if (first.sortOrder <= second.sortOrder) second else first
        dao.updateSortOrder(low.id, high.sortOrder, maxOf(now, low.updatedAt + 1))
        dao.updateSortOrder(high.id, low.sortOrder, maxOf(now, high.updatedAt + 1))
    }

    suspend fun toggle(date: LocalDate, id: String) = db.withTransaction {
        val row = dao.byId(id)?.takeUnless { it.deleted } ?: return@withTransaction
        val epochDay = date.toEpochDay()
        if (dao.logAt(epochDay, id) == null) {
            dao.insertLog(SupplementLogEntity(epochDay, id, clock.millis()))
        } else {
            dao.deleteLog(epochDay, id)
        }
    }

    private fun SupplementEntity.toDomain() = Supplement(id, name, sortOrder, createdAt, updatedAt, deleted)

    companion object {
        const val MAX_SUPPLEMENTS = 20
    }
}
