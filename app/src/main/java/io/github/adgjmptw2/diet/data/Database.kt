package io.github.adgjmptw2.diet.data

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** 프로필은 한 사람만 쓰므로 id 0 한 줄 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 0,
    val sex: String,
    val birthYear: Int,
    val heightCm: Double,
    val startWeightKg: Double,
    val targetWeightKg: Double,
    val activity: String,
    val startEpochDay: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "NULL") val calorieTarget: Int? = null,
)

/**
 * 하루에 한 줄. 나중에 서버 동기화를 붙일 때를 대비해
 * id는 UUID, 수정 시각(updatedAt), 삭제는 표시만(deleted) 한다.
 */
@Entity(tableName = "weight_entry", indices = [Index(value = ["epochDay"], unique = true)])
data class WeightEntryEntity(
    @PrimaryKey val id: String,
    val epochDay: Long,
    val weightKg: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val bodyFatPct: Double? = null,
)

/** 공공 음식 데이터를 다시 넣어도 살아 있도록 food 행 밖에 둔다. */
@Entity(tableName = "favorite_food")
data class FavoriteFoodEntity(
    @PrimaryKey val foodId: String,
    val createdAt: Long,
)

/** 체중 행과 별개. 비우면 deleted=1로 남겨 동기화에서 삭제를 알린다. */
@Entity(tableName = "day_note")
data class DayNoteEntity(
    @PrimaryKey val epochDay: Long,
    @ColumnInfo(defaultValue = "''")
    val memo: String = "",
    @ColumnInfo(defaultValue = "''")
    val note: String = "",
    val updatedAt: Long,
    val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "0") val waterMl: Int = 0,
)

@Entity(tableName = "step_day")
data class StepDayEntity(
    @PrimaryKey val epochDay: Long,
    val steps: Int,
    val source: String,
    val updatedAt: Long,
)

@Dao
interface StepDayDao {
    @Query("SELECT * FROM step_day WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    fun observeRange(from: Long, to: Long): Flow<List<StepDayEntity>>

    @Query("SELECT * FROM step_day WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    suspend fun range(from: Long, to: Long): List<StepDayEntity>

    @Query("SELECT * FROM step_day WHERE epochDay = :epochDay LIMIT 1")
    suspend fun byDay(epochDay: Long): StepDayEntity?

    @Upsert
    suspend fun upsert(row: StepDayEntity)

    @Query("DELETE FROM step_day")
    suspend fun clear()
}

@Entity(tableName = "fast")
data class FastEntity(
    @PrimaryKey val id: String,
    val startAt: Long,
    val endAt: Long?,
    val targetHours: Int,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Dao
interface FastDao {
    @Query("SELECT * FROM fast WHERE deleted = 0 ORDER BY startAt DESC, id")
    fun observeAll(): Flow<List<FastEntity>>

    @Query("SELECT * FROM fast WHERE deleted = 0 AND endAt IS NULL ORDER BY updatedAt DESC, id DESC LIMIT 1")
    suspend fun active(): FastEntity?

    @Query("SELECT * FROM fast WHERE deleted = 0 AND endAt IS NULL ORDER BY updatedAt DESC, id DESC")
    suspend fun activeRows(): List<FastEntity>

    @Query("SELECT * FROM fast WHERE id = :id")
    suspend fun byId(id: String): FastEntity?

    @Query("SELECT * FROM fast WHERE updatedAt > :since ORDER BY updatedAt, id")
    suspend fun changedSince(since: Long): List<FastEntity>

    @Query("SELECT * FROM fast WHERE deleted = 0 AND endAt IS NOT NULL ORDER BY endAt")
    suspend fun exportRows(): List<FastEntity>

    @Upsert
    suspend fun upsert(row: FastEntity)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 0")
    suspend fun current(): ProfileEntity?

    @Query("SELECT * FROM profile WHERE id = 0 AND updatedAt > :since")
    suspend fun changedSince(since: Long): ProfileEntity?

    @Query("SELECT * FROM profile WHERE id = 0")
    fun observe(): Flow<ProfileEntity?>

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_entry WHERE updatedAt > :since ORDER BY updatedAt, id")
    suspend fun changedSince(since: Long): List<WeightEntryEntity>

    @Query("SELECT * FROM weight_entry WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): WeightEntryEntity?

    @Query("DELETE FROM weight_entry WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM weight_entry WHERE deleted = 0 ORDER BY epochDay")
    suspend fun exportRows(): List<WeightEntryEntity>

    @Query("SELECT * FROM weight_entry WHERE deleted = 0 ORDER BY epochDay DESC")
    fun observeAll(): Flow<List<WeightEntryEntity>>

    /** 삭제 표시된 줄도 찾는다(같은 날 다시 기록하면 되살림) */
    @Query("SELECT * FROM weight_entry WHERE epochDay = :epochDay LIMIT 1")
    suspend fun findByDay(epochDay: Long): WeightEntryEntity?

    @Upsert
    suspend fun upsert(entry: WeightEntryEntity)

    @Query("UPDATE weight_entry SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)
}

@Dao
interface FavoriteFoodDao {
    @Query("SELECT foodId FROM favorite_food")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT foodId FROM favorite_food")
    suspend fun ids(): List<String>

    @Query("SELECT f.* FROM food f JOIN favorite_food fav ON fav.foodId = f.id WHERE f.deleted = 0 ORDER BY fav.createdAt DESC")
    fun observeFoods(): Flow<List<FoodEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: FavoriteFoodEntity)

    @Query("DELETE FROM favorite_food WHERE foodId = :foodId")
    suspend fun delete(foodId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_food WHERE foodId = :foodId)")
    suspend fun contains(foodId: String): Boolean
}

@Dao
interface DayNoteDao {
    @Query("SELECT * FROM day_note WHERE deleted = 0 ORDER BY epochDay")
    fun observeAll(): Flow<List<DayNoteEntity>>

    @Query("SELECT * FROM day_note WHERE deleted = 0 AND epochDay BETWEEN :from AND :to ORDER BY epochDay")
    fun observeRange(from: Long, to: Long): Flow<List<DayNoteEntity>>

    @Query("SELECT * FROM day_note WHERE epochDay = :epochDay LIMIT 1")
    suspend fun byDay(epochDay: Long): DayNoteEntity?

    @Query("SELECT * FROM day_note WHERE updatedAt > :since ORDER BY updatedAt, epochDay")
    suspend fun changedSince(since: Long): List<DayNoteEntity>

    @Query("SELECT * FROM day_note WHERE deleted = 0 ORDER BY epochDay")
    suspend fun exportRows(): List<DayNoteEntity>

    @Upsert
    suspend fun upsert(row: DayNoteEntity)
}

@Entity(tableName = "supplement")
data class SupplementEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(defaultValue = "1") val slots: Int = 1,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
)

@Entity(tableName = "supplement_log", primaryKeys = ["epochDay", "supplementId", "slot"])
data class SupplementLogEntity(
    val epochDay: Long,
    val supplementId: String,
    @ColumnInfo(defaultValue = "1") val slot: Int = 1,
    val takenAt: Long,
)

@Dao
interface SupplementDao {
    @Query("SELECT * FROM supplement WHERE deleted = 0 ORDER BY sortOrder, createdAt, id")
    fun observeAll(): Flow<List<SupplementEntity>>

    @Query("SELECT * FROM supplement WHERE deleted = 0 ORDER BY sortOrder, createdAt, id")
    suspend fun all(): List<SupplementEntity>

    @Query("SELECT * FROM supplement WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): SupplementEntity?

    @Query("UPDATE supplement SET sortOrder = :sortOrder, updatedAt = :now WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int, now: Long)

    @Query("UPDATE supplement SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)

    @Insert
    suspend fun insert(row: SupplementEntity)

    @Upsert
    suspend fun upsert(row: SupplementEntity)

    @Query("SELECT l.supplementId, l.slot FROM supplement_log l JOIN supplement s ON s.id = l.supplementId WHERE l.epochDay = :epochDay AND s.deleted = 0")
    fun observeDayChecks(epochDay: Long): Flow<List<SupplementLogSlotRow>>

    @Query("SELECT l.* FROM supplement_log l JOIN supplement s ON s.id = l.supplementId WHERE l.epochDay BETWEEN :from AND :to AND s.deleted = 0 ORDER BY l.epochDay, l.supplementId, l.slot")
    fun observeRange(from: Long, to: Long): Flow<List<SupplementLogEntity>>

    @Query("SELECT * FROM supplement_log WHERE epochDay = :epochDay AND supplementId = :supplementId AND slot = :slot LIMIT 1")
    suspend fun logAt(epochDay: Long, supplementId: String, slot: Int): SupplementLogEntity?

    @Query("SELECT * FROM supplement_log WHERE epochDay = :epochDay AND supplementId = :supplementId")
    suspend fun logsForDay(epochDay: Long, supplementId: String): List<SupplementLogEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(row: SupplementLogEntity)

    @Query("DELETE FROM supplement_log WHERE epochDay = :epochDay AND supplementId = :supplementId AND slot = :slot")
    suspend fun deleteLog(epochDay: Long, supplementId: String, slot: Int)

    @Query("DELETE FROM supplement_log WHERE epochDay = :epochDay AND supplementId = :supplementId")
    suspend fun deleteLogsForDay(epochDay: Long, supplementId: String)

    @Query("SELECT l.epochDay, l.takenAt, l.slot, s.name FROM supplement_log l JOIN supplement s ON s.id = l.supplementId WHERE l.epochDay BETWEEN :from AND :to ORDER BY l.epochDay, s.sortOrder, s.createdAt, s.id, l.slot")
    suspend fun exportLogs(from: Long, to: Long): List<SupplementExportRow>

    @Query("DELETE FROM supplement_log")
    suspend fun clearLogs()
}

data class SupplementLogSlotRow(
    val supplementId: String,
    val slot: Int,
)

data class SupplementExportRow(
    val epochDay: Long,
    val takenAt: Long,
    val slot: Int,
    val name: String,
)

@Database(
    entities = [ProfileEntity::class, WeightEntryEntity::class, FoodEntity::class, MealEntryEntity::class,
        FavoriteFoodEntity::class, DayNoteEntity::class, FastEntity::class, StepDayEntity::class,
        SupplementEntity::class, SupplementLogEntity::class],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6),
    ],
)
abstract class DietDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun weightDao(): WeightDao
    abstract fun foodDao(): FoodDao
    abstract fun mealDao(): MealDao
    abstract fun favoriteFoodDao(): FavoriteFoodDao
    abstract fun dayNoteDao(): DayNoteDao
    abstract fun fastDao(): FastDao
    abstract fun stepDayDao(): StepDayDao
    abstract fun supplementDao(): SupplementDao
}
