package com.spotlog.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object PhotoStorage {
    suspend fun copyToInternalStorage(context: Context, sourceUri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
                val destFile = File(photosDir, "${UUID.randomUUID()}.jpg")
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (destFile.exists() && destFile.length() > 0) destFile.absolutePath else null
            } catch (e: Exception) {
                null
            }
        }
}