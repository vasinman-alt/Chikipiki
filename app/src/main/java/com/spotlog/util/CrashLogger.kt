package com.spotlog.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

object CrashLogger {

    private const val CRASH_FILE_NAME = "crash_report.txt"
    private const val MAX_LOG_SIZE_BYTES = 1024 * 1024L // 1 MB

    fun init(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                val externalDir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
                val logFile = File(externalDir, CRASH_FILE_NAME)

                // Ограничение размера
                if (logFile.exists() && logFile.length() > MAX_LOG_SIZE_BYTES) {
                    logFile.delete()
                }

                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val stackTrace = StringWriter().also { exception.printStackTrace(PrintWriter(it)) }.toString()

                val report = """
                    ========================
                    Crash Time: $timestamp
                    App: Chikipiki (debug)
                    Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
                    Thread: ${thread.name}
                    Exception: ${exception.javaClass.name}
                    Message: ${exception.message}
                    Stack Trace:
                    $stackTrace
                """.trimIndent()

                logFile.appendText(report + "\n\n")
            } catch (_: Throwable) {
                // не удалось записать – ничего не поделать
            } finally {
                defaultHandler?.uncaughtException(thread, exception)
            }
        }
    }
}