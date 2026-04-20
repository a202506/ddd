package com.buzzingmountain.dingclock.log

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Timber Tree that writes a single rolling log file per day under [logsDir].
 * Files older than [retentionDays] are deleted on construction.
 *
 * Format: `yyyy-MM-dd HH:mm:ss.SSS LEVEL TAG: message`
 */
class FileLogTree(
    private val logsDir: File,
    private val retentionDays: Int = 7,
) : Timber.Tree() {

    private val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val tsFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val writeLock = Any()

    init {
        runCatching {
            if (!logsDir.exists()) logsDir.mkdirs()
            cleanupOld()
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.DEBUG) return
        synchronized(writeLock) {
            runCatching {
                val now = Date()
                val file = File(logsDir, "app-${dayFormat.format(now)}.log")
                PrintWriter(FileWriter(file, true)).use { pw ->
                    val line = buildString {
                        append(tsFormat.format(now)).append(' ')
                        append(levelChar(priority)).append(' ')
                        append(tag ?: "-").append(": ")
                        append(message)
                    }
                    pw.println(line)
                    if (t != null) t.printStackTrace(pw)
                }
            }
        }
    }

    private fun levelChar(priority: Int): Char = when (priority) {
        Log.VERBOSE -> 'V'
        Log.DEBUG -> 'D'
        Log.INFO -> 'I'
        Log.WARN -> 'W'
        Log.ERROR -> 'E'
        Log.ASSERT -> 'A'
        else -> '?'
    }

    private fun cleanupOld() {
        val cutoff = System.currentTimeMillis() - retentionDays * 24L * 3600_000L
        logsDir.listFiles { f -> f.name.startsWith("app-") && f.name.endsWith(".log") }
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    companion object {
        fun resolveLogsDir(context: Context): File = File(context.filesDir, "logs")
    }
}
