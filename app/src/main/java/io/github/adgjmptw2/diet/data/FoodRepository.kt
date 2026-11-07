package io.github.adgjmptw2.diet.data

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adgjmptw2.diet.domain.Food
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
) {
    private val dao = db.foodDao()
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
                if (dao.builtInCount() != rows.size) {
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
        return dao.search(q, limit).map { it.toDomain() }
    }

    suspend fun byId(id: String): Food? = dao.byId(id)?.toDomain()

    /** 직접 입력한 음식을 저장해 다음 검색에도 나오게 */
    suspend fun createCustom(name: String, kcal100: Double, serving: Double, unit: String): Food {
        val row = FoodEntity(
            id = "u-" + UUID.randomUUID(),
            name = name.trim(),
            brand = "",
            category = "직접 입력",
            rep = name.trim(),
            kcal100 = kcal100,
            carb100 = null,
            prot100 = null,
            fat100 = null,
            serving = serving,
            unit = unit,
            custom = true,
        )
        dao.upsert(row)
        return row.toDomain()
    }

    private fun readAsset(): List<FoodEntity> =
        GZIPInputStream(context.assets.open(ASSET)).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull(::parse).toList()
        }

    companion object {
        const val ASSET = "foods.tsv.gz"

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

internal fun FoodEntity.toDomain() = Food(
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
)
