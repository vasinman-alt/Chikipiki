package com.spotlog.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object PhotoProcessor {
    private const val DEFAULT_MAX_DIMENSION = 1920
    private const val DEFAULT_JPEG_QUALITY = 80

    suspend fun processAndStore(
        context: Context,
        sourceUri: Uri,
        keepOriginalSize: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeSampledBitmap(context, sourceUri, DEFAULT_MAX_DIMENSION) ?: return@withContext null
            val finalBitmap = if (keepOriginalSize) bitmap else scaleToMaxDimension(bitmap, DEFAULT_MAX_DIMENSION)

            val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
            val destFile = File(photosDir, "${UUID.randomUUID()}.jpg")
            destFile.outputStream().use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, DEFAULT_JPEG_QUALITY, out)
            }
            if (finalBitmap !== bitmap) bitmap.recycle()
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        options.inJustDecodeBounds = false
        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDim)
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sampleSize = 1
        if (height > maxDim || width > maxDim) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / sampleSize >= maxDim && halfWidth / sampleSize >= maxDim) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }

    private fun scaleToMaxDimension(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap

        val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}