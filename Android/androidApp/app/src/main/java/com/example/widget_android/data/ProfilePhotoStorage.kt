package com.example.widget_android.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ProfilePhotoStorage {

    private const val PROFILE_DIR = "profile_photo"
    private const val PROFILE_FILE = "avatar.jpg"
    private const val MAX_SIZE_PX = 512

    suspend fun importToAppStorage(
        context: Context,
        sourceUri: Uri
    ): String? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val directory = File(app.filesDir, PROFILE_DIR).apply { mkdirs() }
        val destination = File(directory, PROFILE_FILE)

        runCatching {
            val rawBytes = app.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: return@withContext null

            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
            val w = opts.outWidth
            val h = opts.outHeight
            if (w <= 0 || h <= 0) return@withContext null

            var sampleSize = 1
            while (w / sampleSize > MAX_SIZE_PX * 2 || h / sampleSize > MAX_SIZE_PX * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val sampled = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                ?: return@withContext null

            val scaled = if (sampled.width > MAX_SIZE_PX || sampled.height > MAX_SIZE_PX) {
                val ratio = MAX_SIZE_PX.toFloat() / maxOf(sampled.width, sampled.height)
                val newW = (sampled.width * ratio).toInt().coerceAtLeast(1)
                val newH = (sampled.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(sampled, newW, newH, true).also {
                    if (it !== sampled) sampled.recycle()
                }
            } else {
                sampled
            }

            destination.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            scaled.recycle()
        }.onFailure {
            return@withContext null
        }

        destination.absolutePath
    }

    fun deletePhoto(context: Context) {
        val directory = File(context.applicationContext.filesDir, PROFILE_DIR)
        val file = File(directory, PROFILE_FILE)
        file.delete()
    }

    fun toDisplayUri(value: String?): Uri? {
        if (value.isNullOrBlank()) return null
        return if (value.contains("://")) {
            Uri.parse(value)
        } else {
            val file = File(value)
            if (file.exists()) Uri.fromFile(file) else null
        }
    }

    fun decodeBitmap(value: String?): Bitmap? {
        if (value.isNullOrBlank() || value.contains("://")) return null
        val file = File(value)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }
}
