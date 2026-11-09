package io.github.adgjmptw2.diet.data.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adgjmptw2.diet.data.DietDatabase
import io.github.adgjmptw2.diet.domain.supplementSlotLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: DietDatabase,
) {
    suspend fun export(): List<Uri> = withContext(Dispatchers.IO) {
        val (weights, meals, notes) = db.withTransaction {
            Triple(db.weightDao().exportRows(), db.mealDao().exportRows(), db.dayNoteDao().exportRows())
        }
        val fasts = db.fastDao().exportRows()
        val supplements = db.supplementDao().exportLogs(Long.MIN_VALUE, Long.MAX_VALUE)
        val directory = File(context.cacheDir, "exports").apply { check(isDirectory || mkdirs()) }
        // A separate folder per export avoids overwriting files already open in another app.
        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 7 * 86_400_000L }
            ?.forEach { it.deleteRecursively() }
        val runDirectory = File(directory, UUID.randomUUID().toString()).apply { check(mkdirs()) }
        val weightFile = File(runDirectory, "체중.csv")
        val mealFile = File(runDirectory, "식단.csv")
        val noteFile = File(runDirectory, "메모.csv")
        val fastFile = File(runDirectory, "단식.csv")
        val supplementFile = File(runDirectory, "supplements.csv")
        weightFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.append(csvRow("id", "날짜", "체중(kg)", "체지방률(%)", "생성시각(ms)", "수정시각(ms)"))
            weights.forEach { writer.append(csvRow(it.id, LocalDate.ofEpochDay(it.epochDay), it.weightKg, it.bodyFatPct, it.createdAt, it.updatedAt)) }
        }
        mealFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.append(csvRow("id", "날짜", "끼니", "음식id", "음식", "이모지", "양", "단위", "kcal",
                "탄수화물(g)", "단백질(g)", "지방(g)", "생성시각(ms)", "수정시각(ms)"))
            meals.forEach { writer.append(csvRow(it.id, LocalDate.ofEpochDay(it.epochDay), it.mealType,
                it.foodId, it.name, it.emoji, it.amount, it.unit, it.kcal, it.carb, it.prot, it.fat, it.createdAt, it.updatedAt)) }
        }
        noteFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.append(csvRow("날짜", "한 줄 메모", "자세한 메모", "물(ml)", "수정시각(ms)"))
            notes.forEach { writer.append(csvRow(LocalDate.ofEpochDay(it.epochDay), it.memo, it.note, it.waterMl, it.updatedAt)) }
        }
        fastFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.append(csvRow("id", "시작시각(ms)", "종료시각(ms)", "목표(시간)", "수정시각(ms)"))
            fasts.forEach { writer.append(csvRow(it.id, it.startAt, it.endAt, it.targetHours, it.updatedAt)) }
        }
        supplementFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.append(csvRow("날짜", "때", "영양제 이름"))
            supplements.forEach {
                writer.append(csvRow(LocalDate.ofEpochDay(it.epochDay), supplementSlotLabel(it.slot), it.name))
            }
        }
        listOf(weightFile, mealFile, noteFile, fastFile, supplementFile)
            .map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }
    }

    fun shareIntent(uris: List<Uri>): Intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "text/csv"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        clipData = ClipData.newUri(context.contentResolver, "기록 CSV", uris.first()).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun csvRow(vararg values: Any?): String = values.joinToString(",") { value ->
    var text = value?.toString().orEmpty()
    // User-entered food names must remain plain text in spreadsheet apps.
    if (value is String && text.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) text = "'$text"
    "\"${text.replace("\"", "\"\"")}\""
} + "\r\n"
