package io.github.adgjmptw2.diet.data

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adgjmptw2.diet.data.catalog.CatalogDb
import io.github.adgjmptw2.diet.data.catalog.CatalogFood
import io.github.adgjmptw2.diet.data.catalog.CatalogSearch
import io.github.adgjmptw2.diet.domain.CustomFoodDraft
import io.github.adgjmptw2.diet.domain.Food
import io.github.adgjmptw2.diet.domain.toFood
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 공공 음식은 읽기 전용 assets/catalog.db에서 찾고, 사용자가 고른 줄만 Room food에 캐시한다.
 * 내 음식(custom=1)은 Room 검색 결과를 항상 앞에 둔다.
 */
@Singleton
class FoodRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: DietDatabase,
    private val clock: Clock,
    private val catalog: CatalogDb,
) {
    private val dao = db.foodDao()
    private val favoritesDao = db.favoriteFoodDao()
    private val cleanupLock = Mutex()
    private val cleanup = CleanupState()

    /** 앱이 처음 열릴 때와 첫 검색 때 catalog 복사를 시작한다. 실패는 호출 쪽에서 알린다. */
    suspend fun prepareCatalog() {
        catalog.prepare()
    }

    /** 기기 데이터를 지운 뒤 catalog 사본을 다시 준비할 수 있게 한 번 정리 상태를 푼다. */
    internal fun resetCleanupState() {
        cleanup.done = false
    }

    /**
     * 기존 1.7만 기본 음식 중 즐겨찾기·식단이 참조하지 않는 줄만 한 번 정리한다.
     * 참조된 줄은 어떤 경우에도 지우지 않는다.
     */
    suspend fun cleanupLegacyFoods() {
        if (cleanup.done) return
        cleanupLock.withLock {
            if (cleanup.done) return
            withContext(Dispatchers.IO) {
                if (!cleanupMarker().exists()) {
                    db.withTransaction {
                        val favorites = favoritesDao.ids().toSet()
                        val meals = db.mealDao().referencedFoodIds().toSet()
                        FoodCleanup.unreferencedBuiltIns(dao.builtInIds(), favorites, meals)
                            .chunked(DELETE_CHUNK)
                            .forEach { dao.deleteByIds(it) }
                    }
                    try {
                        cleanupMarker().createNewFile()
                    } catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        // 정리에 성공했으면 표시를 못 남겨도 다음 실행에서 다시 확인할 뿐이다.
                    }
                }
                cleanup.done = true
            }
        }
    }

    suspend fun search(query: String, limit: Int = 50): List<Food> {
        val tokens = CatalogSearch.tokens(query)
        if (tokens.isEmpty()) return emptyList()
        val favorites = favoritesDao.ids().toSet()
        val normalized = CatalogSearch.normalizedQuery(query)
        val customs = dao.customRows()
            .filter { CatalogSearch.matchesAll(CatalogSearch.searchKey(it.name, it.brand), tokens) }
            .sortedWith(customOrder(normalized))
            .take(limit)
        val remaining = (limit - customs.size).coerceAtLeast(0)
        if (remaining == 0) return customs.map { it.toDomain(favorite = it.id in favorites) }
        // 내 음식은 항상 앞, 공공 결과는 이름 일치 → 앞부분 일치 → 짧은 이름 → 종류 순.
        val public = catalog.search(tokens, query, remaining)
        return customs.map { it.toDomain(favorite = it.id in favorites) } +
            public.map { it.toDomain(favorite = it.id in favorites) }
    }

    private fun cleanupMarker() = File(context.filesDir, CLEANUP_MARKER)

    private fun customOrder(normalized: String): Comparator<FoodEntity> = compareBy(
        { food ->
            val name = CatalogSearch.normalizedName(food.name)
            when {
                name == normalized -> 0
                name.startsWith(normalized) -> 1
                else -> 2
            }
        },
        { it.name.length },
        { CatalogSearch.normalizedName(it.name) },
        { it.id },
    )

    suspend fun byId(id: String): Food? {
        dao.byId(id)?.let { return it.toDomain(favorite = favoritesDao.contains(id)) }
        val cached = catalog.byId(id) ?: return null
        return cached.toDomain(favorite = favoritesDao.contains(id))
    }

    /** 검색 목록의 줄을 끼니에 넣거나 즐겨찾기할 때 Room 캐시에 남긴다. */
    suspend fun cache(food: Food) {
        if (food.custom || food.id.isBlank()) return
        dao.upsert(food.toEntity(custom = false, updatedAt = clock.millis()))
    }

    suspend fun cacheById(id: String): Food? {
        if (id.isBlank()) return null
        dao.byId(id)?.let { return it.toDomain(favorite = favoritesDao.contains(id)) }
        val source = catalog.byId(id) ?: return null
        val food = source.toDomain(favorite = false)
        cache(food)
        return food
    }

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
            cacheById(id)
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

    private class CleanupState {
        @Volatile var done = false
    }

    private companion object {
        const val CLEANUP_MARKER = "food-cleanup-v4"
        const val DELETE_CHUNK = 500
    }}

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
        pack = saved.pack,
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
    pack = pack,
    servingKcal = servingKcal,
    servingCarb = servingCarb,
    servingProt = servingProt,
    servingFat = servingFat,
    servingWeightG = servingWeightG,
)


private fun CatalogFood.toDomain(favorite: Boolean = false) = Food(
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
    custom = false,
    favorite = favorite,
    pack = pack,
)

private fun Food.toEntity(custom: Boolean, updatedAt: Long) = FoodEntity(
    id = id,
    name = name,
    brand = brand.orEmpty(),
    category = category,
    rep = rep,
    kcal100 = kcal100,
    carb100 = carb100,
    prot100 = prot100,
    fat100 = fat100,
    serving = serving,
    unit = unit,
    custom = custom,
    pack = pack,
    updatedAt = updatedAt,
    servingKcal = servingKcal,
    servingCarb = servingCarb,
    servingProt = servingProt,
    servingFat = servingFat,
    servingWeightG = servingWeightG,
)
