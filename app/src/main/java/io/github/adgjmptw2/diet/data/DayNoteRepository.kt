package io.github.adgjmptw2.diet.data

import androidx.room.withTransaction
import io.github.adgjmptw2.diet.domain.DailyNote
import io.github.adgjmptw2.diet.domain.sanitizeMemo
import io.github.adgjmptw2.diet.domain.sanitizeNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DayNoteRepository @Inject constructor(
    private val db: DietDatabase,
    private val clock: Clock,
) {
    private val dao = db.dayNoteDao()

    fun notes(): Flow<Map<LocalDate, DailyNote>> = dao.observeAll().map { rows ->
        rows.associate { row ->
            LocalDate.ofEpochDay(row.epochDay) to row.toDomain()
        }
    }

    fun notes(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, DailyNote>> =
        dao.observeRange(from.toEpochDay(), to.toEpochDay()).map { rows ->
            rows.associate { row ->
                LocalDate.ofEpochDay(row.epochDay) to row.toDomain()
            }
        }

    suspend fun save(date: LocalDate, memo: String, note: String) = db.withTransaction {
        val cleanMemo = sanitizeMemo(memo)
        val cleanNote = sanitizeNote(note)
        // 체중만 고친 날에 빈 삭제 표시를 만들지 않는다.
        val existing = dao.byDay(date.toEpochDay())
        val water = existing?.takeUnless { it.deleted }?.waterMl ?: 0
        val empty = cleanMemo.isBlank() && cleanNote.isBlank() && water == 0
        if (empty && (existing == null || existing.deleted)) return@withTransaction
        dao.upsert(
            DayNoteEntity(
                epochDay = date.toEpochDay(),
                memo = cleanMemo,
                note = cleanNote,
                updatedAt = maxOf(clock.millis(), (existing?.updatedAt ?: 0) + 1),
                deleted = empty,
                waterMl = water,
            )
        )
    }

    suspend fun clear(date: LocalDate) = save(date, "", "")

    suspend fun adjustWater(date: LocalDate, deltaMl: Int) = db.withTransaction {
        val existing = dao.byDay(date.toEpochDay())
        val live = existing?.takeUnless { it.deleted }
        val water = ((live?.waterMl ?: 0) + deltaMl).coerceIn(0, 10_000)
        val memo = live?.memo.orEmpty()
        val note = live?.note.orEmpty()
        val empty = memo.isBlank() && note.isBlank() && water == 0
        if (empty && (existing == null || existing.deleted)) return@withTransaction
        dao.upsert(DayNoteEntity(date.toEpochDay(), memo, note,
            maxOf(clock.millis(), (existing?.updatedAt ?: 0) + 1), empty, water))
    }
}

/** 동기화로 들어온 값도 화면·CSV가 쓰는 길이 규칙을 넘지 않게 맞춘다. */
private fun DayNoteEntity.toDomain() = DailyNote(
    epochDay = epochDay,
    memo = sanitizeMemo(memo),
    note = sanitizeNote(note),
    waterMl = waterMl,
)
