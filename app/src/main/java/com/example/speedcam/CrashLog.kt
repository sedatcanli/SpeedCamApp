package com.example.speedcam

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Yakalanmayan çökmeleri dosyaya yazar; ayarlardan okunur. */
object CrashLog {
    private const val NAME = "crash.log"

    fun install(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val time = SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", Locale.US
                ).format(Date())
                val sb = StringBuilder()
                sb.append("=== ").append(time).append(" ===\n")
                sb.append(throwable.toString()).append('\n')
                var cause: Throwable? = throwable.cause
                var depth = 0
                while (cause != null && depth < 5) {
                    sb.append("Caused by: ").append(cause.toString()).append('\n')
                    val st = cause.stackTrace
                    for (i in 0 until minOf(st.size, 25)) sb.append("  at ").append(st[i]).append('\n')
                    cause = cause.cause
                    depth++
                }
                val st = throwable.stackTrace
                for (i in 0 until minOf(st.size, 30)) sb.append("  at ").append(st[i]).append('\n')
                File(context.filesDir, NAME).writeText(sb.toString())
            } catch (_: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun lastCrash(context: Context): String? {
        return try {
            val f = File(context.filesDir, NAME)
            if (f.exists()) f.readText() else null
        } catch (_: Exception) { null }
    }

    fun clear(context: Context) {
        try { File(context.filesDir, NAME).delete() } catch (_: Exception) { }
    }
}
