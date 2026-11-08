package io.github.adgjmptw2.diet.data

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

    suspend fun save(date: LocalDate, memo: String, note: String) {
        val cleanMemo = sanitizeMemo(memo)
        val cleanNote = sanitizeNote(note)
        val empty = cleanMemo.isBlank() && cleanNote.isBlank()
        // 체중만 고친 날에 빈 삭제 표시를 만들지 않는다.
        val existing = dao.byDay(date.toEpochDay())
        if (empty && (existing == null || existing.deleted)) return
        dao.upsert(
            DayNoteEntity(
                epochDay = date.toEpochDay(),
                memo = cleanMemo,
                note = cleanNote,
                updatedAt = clock.millis(),
                deleted = empty,
            )
        )
    }

    suspend fun clear(date: LocalDate) = save(date, "", "")
}

/** 동기화로 들어온 값도 화면·CSV가 쓰는 길이 규칙을 넘지 않게 맞춘다. */
private fun DayNoteEntity.toDomain() = DailyNote(
    epochDay = epochDay,
    memo = sanitizeMemo(memo),
    note = sanitizeNote(note),
)
