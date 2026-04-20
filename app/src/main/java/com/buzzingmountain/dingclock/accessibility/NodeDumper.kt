package com.buzzingmountain.dingclock.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Renders a [AccessibilityNodeInfo] tree to a multi-line, human-readable string suitable for
 * pasting into the log file. Used by Phase 2's "dump current screen" tool and Phase 5's
 * state-machine debug output.
 *
 * Per-node line:
 *   <indent><Class> [#viewId] "text" desc="cd" [flags] [l,t,r,b]
 *
 * Flags (one char each, lower = false):
 *   C clickable / K checkable / k checked / F focusable / f focused
 *   L longClickable / S scrollable / E enabled (e=disabled) / V visibleToUser (v=invisible)
 */
object NodeDumper {

    fun dump(root: AccessibilityNodeInfo?, maxNodes: Int = 800): String {
        if (root == null) return "(null root)"
        val sb = StringBuilder()
        val counter = intArrayOf(0)
        dumpNode(root, 0, sb, counter, maxNodes)
        if (counter[0] >= maxNodes) {
            sb.append("... truncated at $maxNodes nodes\n")
        }
        return sb.toString()
    }

    private fun dumpNode(
        node: AccessibilityNodeInfo?,
        depth: Int,
        sb: StringBuilder,
        counter: IntArray,
        maxNodes: Int,
    ) {
        if (node == null) return
        if (counter[0] >= maxNodes) return
        counter[0]++

        val indent = "  ".repeat(depth)
        val cls = node.className?.toString()?.substringAfterLast('.') ?: "?"
        val id = node.viewIdResourceName?.substringAfterLast('/').orEmpty()
        val text = node.text?.toString()?.take(80).orEmpty()
        val desc = node.contentDescription?.toString()?.take(80).orEmpty()
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val flags = buildString {
            append(if (node.isClickable) 'C' else '-')
            append(if (node.isCheckable) 'K' else '-')
            append(if (node.isChecked) 'k' else '-')
            append(if (node.isFocusable) 'F' else '-')
            append(if (node.isFocused) 'f' else '-')
            append(if (node.isLongClickable) 'L' else '-')
            append(if (node.isScrollable) 'S' else '-')
            append(if (node.isEnabled) 'E' else 'e')
            append(if (node.isVisibleToUser) 'V' else 'v')
        }
        sb.append(indent).append(cls)
        if (id.isNotEmpty()) sb.append(" #").append(id)
        if (text.isNotEmpty()) sb.append(" \"").append(escape(text)).append('"')
        if (desc.isNotEmpty()) sb.append(" desc=\"").append(escape(desc)).append('"')
        sb.append(" [").append(flags).append(']')
        sb.append(" [")
            .append(bounds.left).append(',')
            .append(bounds.top).append(',')
            .append(bounds.right).append(',')
            .append(bounds.bottom).append(']')
        sb.append('\n')

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            try {
                dumpNode(child, depth + 1, sb, counter, maxNodes)
            } finally {
                @Suppress("DEPRECATION")
                child?.recycle()
            }
        }
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
