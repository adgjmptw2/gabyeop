package io.github.adgjmptw2.diet.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "food", indices = [Index("name"), Index("rep")])
data class FoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** 업체가 없으면 빈 문자열 */
    val brand: String,
    val category: String,
    val rep: String,
    val kcal100: Double,
    val carb100: Double?,
    val prot100: Double?,
    val fat100: Double?,
    val serving: Double,
    val unit: String,
    val custom: Boolean,
)

@Entity(tableName = "meal_entry", indices = [Index("epochDay"), Index("foodId")])
data class MealEntryEntity(
    @PrimaryKey val id: String,
    val epochDay: Long,
    val mealType: String,
    val foodId: String?,
    val name: String,
    val emoji: String,
    val amount: Double,
    val unit: String,
    val kcal: Double,
    val carb: Double?,
    val prot: Double?,
    val fat: Double?,
    /** 기기 안 파일 경로. 서버로 보내지 않음 */
    val photoPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Dao
interface FoodDao {
    @Query("SELECT COUNT(*) FROM food WHERE custom = 0")
    suspend fun builtInCount(): Int

    @Query("DELETE FROM food WHERE custom = 0")
    suspend fun clearBuiltIn()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<FoodEntity>)

    @Upsert
    suspend fun upsert(row: FoodEntity)

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun byId(id: String): FoodEntity?

    /** 정확히 같은 이름 → 앞부분 일치 → 대표식품명 일치 → 나머지. 같은 순위면 업체 없는 음식, 짧은 이름 먼저 */
    @Query(
        """
        SELECT * FROM food
        WHERE name LIKE '%' || :q || '%' OR rep LIKE '%' || :q || '%' OR brand LIKE '%' || :q || '%'
        ORDER BY
            CASE WHEN name = :q THEN 0 WHEN name LIKE :q || '%' THEN 1 WHEN rep = :q THEN 2 ELSE 3 END,
            custom DESC,
            CASE WHEN brand = '' THEN 0 ELSE 1 END,
            length(name)
        LIMIT :limit
        """
    )
    suspend fun search(q: String, limit: Int): List<FoodEntity>
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meal_entry WHERE deleted = 0 AND epochDay = :epochDay ORDER BY createdAt")
    fun observeDay(epochDay: Long): Flow<List<MealEntryEntity>>

    @Query("SELECT * FROM meal_entry WHERE deleted = 0 AND epochDay BETWEEN :from AND :to ORDER BY epochDay, createdAt")
    fun observeRange(from: Long, to: Long): Flow<List<MealEntryEntity>>

    @Query("SELECT COUNT(DISTINCT epochDay) FROM meal_entry WHERE deleted = 0")
    fun observeLoggedDayCount(): Flow<Int>

    @Query("SELECT DISTINCT epochDay FROM meal_entry WHERE deleted = 0 ORDER BY epochDay")
    fun observeLoggedDays(): Flow<List<Long>>

    @Query("SELECT * FROM meal_entry WHERE id = :id")
    suspend fun byId(id: String): MealEntryEntity?

    @Upsert
    suspend fun upsert(row: MealEntryEntity)

    @Query("UPDATE meal_entry SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)

    /** 최근에 먹은 음식(같은 음식은 한 번만) */
    @Query(
        """
        SELECT f.* FROM food f JOIN (
            SELECT foodId, MAX(updatedAt) AS lastAt FROM meal_entry
            WHERE deleted = 0 AND foodId IS NOT NULL GROUP BY foodId
        ) m ON m.foodId = f.id
        ORDER BY m.lastAt DESC LIMIT :limit
        """
    )
    fun observeRecentFoods(limit: Int): Flow<List<FoodEntity>>
}
