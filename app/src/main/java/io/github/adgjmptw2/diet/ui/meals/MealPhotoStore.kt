package io.github.adgjmptw2.diet.ui.meals

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

internal data class CameraCapture(val uri: Uri, val path: String)

@Singleton
class MealPhotoStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val photos get() = File(context.filesDir, "meal_photos")
    private val captures get() = File(context.cacheDir, "meal_captures")

    internal suspend fun cameraCapture(): CameraCapture = withContext(Dispatchers.IO) {
        val file = newFile(captures)
        try {
            if (!file.createNewFile()) throw IOException("Capture file exists")
            CameraCapture(FileProvider.getUriForFile(context, "${context.packageName}.files", file), file.path)
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    suspend fun store(uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); Unit }
            ?: throw IOException("Photo unavailable")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Invalid photo")

        val orientation = resolver.openInputStream(uri)?.use { stream ->
            runCatching { ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        } ?: 1
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Photo decode failed")
        var output: File? = null
        try {
            val longest = max(bitmap.width, bitmap.height)
            if (longest > MAX_EDGE) {
                val ratio = MAX_EDGE.toDouble() / longest
                val scaled = Bitmap.createScaledBitmap(
                    bitmap, (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
                    (bitmap.height * ratio).roundToInt().coerceAtLeast(1), true,
                )
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
            if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            }
            if (bitmap.hasAlpha()) {
                val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                Canvas(opaque).apply { drawColor(Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
                bitmap.recycle()
                bitmap = opaque
            }
            val file = newFile(photos)
            output = file
            file.outputStream().use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)) throw IOException("Photo encode failed")
            }
            file.absolutePath
        } catch (e: Exception) {
            output?.delete()
            throw e
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun deletePhoto(path: String?) = withContext(Dispatchers.IO) { deleteOwned(path, photos) }

    internal suspend fun discardCapture(capture: CameraCapture) = withContext(Dispatchers.IO) {
        deleteOwned(capture.path, captures)
    }

    private fun deleteOwned(path: String?, directory: File) {
        if (path == null) return
        val file = File(path).canonicalFile
        if (file.parentFile == directory.canonicalFile) file.delete()
    }

    private fun newFile(directory: File): File {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Photo directory unavailable")
        return File(directory, "${UUID.randomUUID()}.jpg")
    }

    private companion object { const val MAX_EDGE = 1280 }
}
