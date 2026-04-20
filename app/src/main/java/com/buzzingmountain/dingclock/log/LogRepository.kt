package com.buzzingmountain.dingclock.log

import android.content.Context
import java.io.File

class LogRepository(context: Context) {

    private val logsDir: File = FileLogTree.resolveLogsDir(context.applicationContext)

    /** All log files, newest mtime first. */
    fun listFiles(): List<File> =
        logsDir.listFiles { f -> f.name.startsWith("app-") && f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** Concatenated tail of [maxLines] across all files (newest first). */
    fun tail(maxLines: Int = 4000): String {
        val files = listFiles()
        if (files.isEmpty()) return ""
        val collected = ArrayDeque<String>(maxLines + 16)
        for (file in files) {
            file.useLines { lines ->
                lines.forEach {
                    collected.addLast(it)
                    if (collected.size > maxLines) collected.removeFirst()
                }
            }
            if (collected.size >= maxLines) break
        }
        return collected.joinToString("\n")
    }

    fun clearAll() {
        listFiles().forEach { it.delete() }
    }

    fun totalBytes(): Long = listFiles().sumOf { it.length() }
}
