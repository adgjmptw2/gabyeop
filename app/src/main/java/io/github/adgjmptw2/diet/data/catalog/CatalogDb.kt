package io.github.adgjmptw2.diet.data.catalog

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** catalog.db의 한 줄. 영양은 100g/100ml 기준이다. */
data class CatalogFood(
    val id: String,
    val name: String,
    val brand: String,
    val category: String,
    val rep: String,
    val kcal100: Double,
    val carb100: Double?,
    val prot100: Double?,
    val fat100: Double?,
    val serving: Double,
    val unit: String,
    val pack: Double?,
    val kind: Int,
)

/** 검색 결과와 catalog 원본 조회에 함께 쓰는 읽기 전용 접근. */
@Singleton
class CatalogDb @Inject constructor(@ApplicationContext private val context: Context) {

    private val lock = Mutex()
    @Volatile private var database: SQLiteDatabase? = null
    @Volatile private var preparationError: Throwable? = null

    /** 자산 크기·압축 해제 크기와 앱 자체의 설치·업데이트 시각. 사본 meta에 기록한다. */
    private fun assetSignature(): String = runCatching {
        context.assets.openFd(ASSET).use { "${it.length}:${it.declaredLength}:${packageUpdatedAt()}" }
    }.getOrElse { "pkg:${packageUpdatedAt()}" }

    private fun packageUpdatedAt(): Long = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    }.getOrDefault(0L)

    /**
     * 자산을 databases/catalog.db로 복사하고 읽기 전용으로 연다.
     * 이미 같은 자산이면 그대로 재사용한다. 실패는 다음 호출에서 다시 시도한다.
     */
    suspend fun prepare(): SQLiteDatabase = lock.withLock {
        database?.takeIf { it.isOpen }?.let { return it }
        preparationError = null
        val opened = try {
            withContext(Dispatchers.IO) { openOrCopy() }
        } catch (failure: Throwable) {
            preparationError = failure
            throw failure
        }
        database = opened
        opened
    }

    fun ready(): Boolean = database?.isOpen == true

    fun error(): Throwable? = preparationError

    /**
     * 토큰이 모두 skey에 든 줄을 이름 정확 일치 → 앞부분 일치 → 짧은 이름 → 음식·원재료 순으로
     * 가져온다. 정렬은 SQL에서 해야 33만 줄에서도 원하는 줄이 후보 밖으로 밀리지 않는다.
     */
    suspend fun search(tokens: List<String>, query: String, limit: Int): List<CatalogFood> {
        if (tokens.isEmpty()) return emptyList()
        val db = prepare()
        return withContext(Dispatchers.IO) {
            val where = tokens.joinToString(" AND ") { "k.skey LIKE ? ESCAPE '\\'" }
            val normalized = CatalogSearch.normalizedQuery(query)
            val args = (tokens.map { "%" + escapeLike(it) + "%" } +
                listOf(normalized, normalized, limit.toString())).toTypedArray()
            val sql = """
                SELECT f.id, f.name, f.brand, f.category, f.rep, f.kcal100, f.carb100,
                    f.prot100, f.fat100, f.serving, f.unit, f.pack, f.kind
                FROM fkey k JOIN food f ON f.rid = k.rid
                WHERE $where
                ORDER BY
                    CASE WHEN lower(trim(f.name)) = ? THEN 0
                         WHEN instr(lower(trim(f.name)), ?) = 1 THEN 1
                         ELSE 2 END,
                    length(f.name), CASE WHEN f.kind <= 1 THEN 0 ELSE 1 END, f.name, f.id
                LIMIT ?
            """.trimIndent()
            db.rawQuery(sql, args).use { cursor -> cursor.mapRows(::readFood) }
        }
    }

    suspend fun byId(id: String): CatalogFood? {
        if (id.isBlank()) return null
        val db = prepare()
        return withContext(Dispatchers.IO) {
            db.rawQuery(
                "SELECT id, name, brand, category, rep, kcal100, carb100, prot100, fat100, serving, unit, pack, kind FROM food WHERE id = ? LIMIT 1",
                arrayOf(id),
            ).use { cursor -> if (cursor.moveToFirst()) readFood(cursor) else null }
        }
    }

    private fun openOrCopy(): SQLiteDatabase {
        val path = context.getDatabasePath(FILE_NAME)
        path.parentFile?.mkdirs()
        val copy = path.parentFile?.let { File(it, "$FILE_NAME.tmp") }
            ?: error("database directory unavailable")
        val signature = assetSignature()
        // 자산을 열 수 없어도 유효한 사본이 있으면 그대로 쓴다.
        if (path.isFile && markerMatches(readMarker(path), signature)) return open(path)
        try {
            copy.delete()
            context.assets.open(ASSET).use { input ->
                copy.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
            }
            val version = readVersion(copy) ?: throw IOException("catalog.db에 버전 정보가 없어요")
            writeMarker(copy, "$version|$signature")
            if (path.exists() && !path.delete()) throw IOException("catalog.db 이전 파일을 지우지 못했어요")
            if (!copy.renameTo(path)) throw IOException("catalog.db를 준비하지 못했어요")
            return open(path)
        } catch (failure: Throwable) {
            runCatching { copy.delete() }
            // 새 자산을 복사하지 못해도 이전 사본이 열리면 앱은 계속 쓸 수 있다.
            if (path.isFile) return open(path)
            throw failure
        }
    }

    /** meta.asset은 "version|자산서명" 형태다. 자산 서명만 비교한다. */
    private fun markerMatches(marker: String?, signature: String): Boolean =
        marker?.substringAfter('|') == signature

    companion object {
        const val ASSET = "catalog.db"
        const val FILE_NAME = "catalog.db"
    }

    private fun open(path: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY)

    private fun readVersion(path: File): String? = runCatching {
        SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT value FROM meta WHERE key = 'version' LIMIT 1", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }
    }.getOrNull()

    private fun readMarker(path: File): String? = runCatching {
        SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT value FROM meta WHERE key = 'asset' LIMIT 1", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }
    }.getOrNull()


    private fun writeMarker(path: File, marker: String) {
        SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("INSERT OR REPLACE INTO meta(key, value) VALUES('asset', ?)", arrayOf(marker))
        }
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun readFood(cursor: Cursor) = CatalogFood(
        id = cursor.getString(0),
        name = cursor.getString(1),
        brand = cursor.getString(2).orEmpty(),
        category = cursor.getString(3).orEmpty(),
        rep = cursor.getString(4).orEmpty(),
        kcal100 = cursor.getDouble(5),
        carb100 = cursor.nullableDouble(6),
        prot100 = cursor.nullableDouble(7),
        fat100 = cursor.nullableDouble(8),
        serving = cursor.getDouble(9),
        unit = cursor.getString(10).let { if (it == "ml") "ml" else "g" },
        pack = cursor.nullableDouble(11),
        kind = cursor.getInt(12),
    )

    private fun Cursor.nullableDouble(index: Int): Double? =
        if (isNull(index)) null else getDouble(index)

    private fun <T> Cursor.mapRows(read: (Cursor) -> T): List<T> {
        val rows = ArrayList<T>()
        while (moveToNext()) rows.add(read(this))
        return rows
    }

}

