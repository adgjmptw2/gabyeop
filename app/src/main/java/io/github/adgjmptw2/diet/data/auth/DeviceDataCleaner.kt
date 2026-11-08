package io.github.adgjmptw2.diet.data.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adgjmptw2.diet.data.DietDatabase
import io.github.adgjmptw2.diet.data.FoodRepository
import io.github.adgjmptw2.diet.data.settings.TrackingStore
import io.github.adgjmptw2.diet.data.reminder.FastGoalScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceDataCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: DietDatabase,
    private val tokens: TokenStore,
    private val foods: FoodRepository,
    private val tracking: TrackingStore,
    private val fastGoals: FastGoalScheduler,
) {
    suspend fun clear() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        fastGoals.cancel()
        tracking.clear()
        foods.resetImportState()
        // Photo and export folders are confined to this application's private roots.
        // Keep the live DataStore directory: clearing its preferences is atomic and safe.
        val roots = listOfNotNull(context.filesDir, context.cacheDir,
            context.getExternalFilesDir(null), context.externalCacheDir)
        roots.forEach { root ->
            root.listFiles()?.filterNot { root == context.filesDir && it.name == "datastore" }
                ?.forEach { erase(it, root.canonicalFile) }
        }
        tokens.clear()
    }

    private fun erase(file: File, root: File) {
        val resolved = file.canonicalFile
        check(resolved.path.startsWith(root.path + File.separator)) { "Invalid private data path" }
        if (file.isDirectory) file.listFiles()?.forEach { erase(it, root) }
        if (file.exists() && !file.delete()) throw IOException("기기 파일을 지우지 못했어요")
    }
}
