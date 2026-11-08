package io.github.adgjmptw2.diet.data

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adgjmptw2.diet.domain.CustomFoodDraft
import io.github.adgjmptw2.diet.domain.Food
import io.github.adgjmptw2.diet.domain.toFood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 식약처 음식 데이터(assets/foods.tsv.gz)를 첫 실행 때 food 테이블에 넣고 검색한다.
 * 자산 줄 수가 바뀌면(데이터 갱신) 기본 음식만 다시 넣는다. 사용자가 만든 음식(custom)은 그대로.
 */
@Singleton
class FoodRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: DietDatabase,
    private val clock: Clock,
) {
    private val dao = db.foodDao()
    private val favoritesDao = db.favoriteFoodDao()
    private val importLock = Mutex()
    @Volatile private var ready = false

    /** Account data removal also clears Room's bundled food rows. */
    internal suspend fun resetImportState() = importLock.withLock { ready = false }

    suspend fun ensureImported() {
        if (ready) return
        importLock.withLock {
            if (ready) return
            withContext(Dispatchers.IO) {
                val rows = readAsset()
                val previous = dao.builtInRows()
                // 새 v3 칸은 기본 NULL이라 기존 가져오기 행도 값이 같다. 행 개수·내용이 다를 때만 다시 넣는다.
                val needsImport = previous.size != rows.size || previous != rows.sortedBy { it.id }
                if (needsImport) {
                    db.withTransaction {
                        dao.clearBuiltIn()
                        rows.chunked(1000).forEach { dao.insertAll(it) }
                    }
                }
            }
            ready = true
        }
    }

    suspend fun search(query: String, limit: Int = 50): List<Food> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        ensureImported()
        val favorites = favoritesDao.ids().toSet()
        return dao.search(q, limit).map { it.toDomain(favorite = it.id in favorites) }
    }

    suspend fun byId(id: String): Food? = dao.byId(id)?.toDomain(favorite = favoritesDao.contains(id))

    fun recentFoods(limit: Int = 20): Flow<List<Food>> =
        db.mealDao().observeRecentFoods(limit).withFavorites()

    fun favorites(): Flow<List<Food>> = favoritesDao.observeFoods().map { rows -> rows.map { it.toDomain(true) } }

    fun favoriteIds(): Flow<Set<String>> = favoritesDao.observeIds().map { it.toSet() }

    fun customFoods(): Flow<List<Food>> = dao.observeCustom().withFavorites()

    private fun Flow<List<FoodEntity>>.withFavorites(): Flow<List<Food>> =
        combine(this, favoritesDao.observeIds()) { rows, ids ->
            val favoriteIds = ids.toSet()
            rows.map { it.toDomain(favorite = it.id in favoriteIds) }
        }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        if (favorite) {
            if (!favoritesDao.contains(id)) favoritesDao.insert(FavoriteFoodEntity(id, clock.millis()))
        } else {
            favoritesDao.delete(id)
        }
    }

    /** 직접 입력한 음식을 저장해 다음 검색에도 나오게 */
    suspend fun createCustom(draft: CustomFoodDraft, favorite: Boolean = false): Food {
        val now = clock.millis()
        val row = draft.toEntity(id = "u-" + UUID.randomUUID(), updatedAt = now)
        db.withTransaction {
            dao.upsert(row)
            if (favorite) favoritesDao.insert(FavoriteFoodEntity(row.id, now))
        }
        return row.toDomain(favorite)
    }

    suspend fun updateCustom(id: String, draft: CustomFoodDraft, favorite: Boolean = false): Food? {
        val existing = dao.customById(id) ?: return null
        val now = clock.millis()
        val row = draft.toEntity(id = existing.id, updatedAt = now).copy(deleted = false)
        db.withTransaction {
            dao.upsert(row)
            if (favorite && !favoritesDao.contains(id)) favoritesDao.insert(FavoriteFoodEntity(id, now))
            if (!favorite) favoritesDao.delete(id)
        }
        return row.toDomain(favorite)
    }

    /** 이미 기록한 식단은 이름·kcal을 따로 저장하므로 참조가 깨지지 않는다. */
    suspend fun deleteCustom(id: String) {
        val existing = dao.customById(id) ?: return
        dao.upsert(existing.copy(deleted = true, updatedAt = clock.millis()))
        favoritesDao.delete(id)
    }

    private fun readAsset(): List<FoodEntity> =
        GZIPInputStream(context.assets.open(ASSET)).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull(::parse).toList()
        }

    companion object {
        const val ASSET = "foods.tsv.gzbin" // .gz 이면 AGP가 풀어서 이름이 바뀜

        /** id, 이름, 업체, 대분류, 대표식품, kcal/100, 탄/100, 단/100, 지/100, 1회 제공량, 단위 */
        fun parse(line: String): FoodEntity? {
            val c = line.split('\t')
            if (c.size < 11) return null
            val kcal = c[5].toDoubleOrNull() ?: return null
            return FoodEntity(
                id = c[0],
                name = c[1],
                brand = c[2],
                category = c[3],
                rep = c[4],
                kcal100 = kcal,
                carb100 = c[6].toDoubleOrNull(),
                prot100 = c[7].toDoubleOrNull(),
                fat100 = c[8].toDoubleOrNull(),
                serving = c[9].toDoubleOrNull()?.takeIf { it > 0 } ?: 100.0,
                unit = if (c[10] == "ml") "ml" else "g",
                custom = false,
            )
        }
    }
}

private fun CustomFoodDraft.toEntity(id: String, updatedAt: Long): FoodEntity {
    val saved = toFood(id = id)
    return FoodEntity(
        id = saved.id,
        name = saved.name,
        brand = saved.brand.orEmpty(),
        category = saved.category,
        rep = saved.rep,
        kcal100 = saved.kcal100,
        carb100 = saved.carb100,
        prot100 = saved.prot100,
        fat100 = saved.fat100,
        serving = saved.serving,
        unit = saved.unit,
        custom = true,
        updatedAt = updatedAt,
        servingKcal = saved.servingKcal,
        servingCarb = saved.servingCarb,
        servingProt = saved.servingProt,
        servingFat = saved.servingFat,
        servingWeightG = saved.servingWeightG,
    )
}

internal fun FoodEntity.toDomain(favorite: Boolean = false) = Food(
    id = id,
    name = name,
    brand = brand.ifEmpty { null },
    category = category,
    rep = rep,
    kcal100 = kcal100,
    carb100 = carb100,
    prot100 = prot100,
    fat100 = fat100,
    serving = serving,
    unit = unit,
    custom = custom,
    favorite = favorite,
    servingKcal = servingKcal,
    servingCarb = servingCarb,
    servingProt = servingProt,
    servingFat = servingFat,
    servingWeightG = servingWeightG,
)
