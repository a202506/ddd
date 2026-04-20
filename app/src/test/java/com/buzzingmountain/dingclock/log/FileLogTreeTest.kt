package com.buzzingmountain.dingclock.log

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileLogTreeTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `writes a single dated file with formatted line`() {
        val tree = FileLogTree(tempDir.root)
        tree.log(Log.INFO, "Tag", "hello world", null)

        val files = tempDir.root.listFiles { f -> f.name.startsWith("app-") && f.name.endsWith(".log") }
            ?.toList() ?: emptyList()
        assertEquals(1, files.size)
        val content = files[0].readText()
        assertTrue("missing tag in: $content", content.contains("Tag: hello world"))
        assertTrue("missing level char", content.contains(" I "))
    }

    @Test
    fun `appends additional lines to same file`() {
        val tree = FileLogTree(tempDir.root)
        repeat(3) { tree.log(Log.WARN, "T", "msg-$it", null) }
        val file = tempDir.root.listFiles { f -> f.name.startsWith("app-") }!!.first()
        val lines = file.readLines()
        assertEquals(3, lines.size)
    }

    @Test
    fun `cleans up files older than retention window`() {
        val staleDay = File(tempDir.root, "app-20200101.log").apply {
            writeText("old\n")
            setLastModified(System.currentTimeMillis() - 30L * 24 * 3600_000)
        }
        val freshDay = File(tempDir.root, "app-20991231.log").apply {
            writeText("new\n")
            setLastModified(System.currentTimeMillis())
        }

        FileLogTree(tempDir.root, retentionDays = 7)

        assertFalse("stale file should be deleted", staleDay.exists())
        assertTrue("fresh file must remain", freshDay.exists())
    }
}
