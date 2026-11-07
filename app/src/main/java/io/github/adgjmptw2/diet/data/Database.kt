package io.github.adgjmptw2.diet.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
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

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 0")
    fun observe(): Flow<ProfileEntity?>

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface WeightDao {
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

@Database(entities = [ProfileEntity::class, WeightEntryEntity::class], version = 1, exportSchema = true)
abstract class DietDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun weightDao(): WeightDao
}
