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
)

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

@Database(
    entities = [ProfileEntity::class, WeightEntryEntity::class, FoodEntity::class, MealEntryEntity::class,
        FavoriteFoodEntity::class, DayNoteEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class DietDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun weightDao(): WeightDao
    abstract fun foodDao(): FoodDao
    abstract fun mealDao(): MealDao
    abstract fun favoriteFoodDao(): FavoriteFoodDao
    abstract fun dayNoteDao(): DayNoteDao
}