package com.buzzingmountain.dingclock.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Pure-function helpers for locating nodes inside an accessibility tree. No state, no
 * service references — easy to test (when fed a fake tree).
 *
 * Convention: callers own the lifetime of the returned NodeInfo references; we never
 * recycle here because that would invalidate the result.
 */
object NodeFinder {

    typealias Predicate = (AccessibilityNodeInfo) -> Boolean

    /** First node (depth-first) matching [predicate], or null. */
    fun findFirst(root: AccessibilityNodeInfo?, predicate: Predicate): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (i in 0 until root.childCount) {
            findFirst(root.getChild(i), predicate)?.let { return it }
        }
        return null
    }

    /** All matching nodes (depth-first). */
    fun findAll(root: AccessibilityNodeInfo?, predicate: Predicate): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val out = mutableListOf<AccessibilityNodeInfo>()
        collect(root, predicate, out)
        return out
    }

    private fun collect(
        node: AccessibilityNodeInfo?,
        predicate: Predicate,
        out: MutableList<AccessibilityNodeInfo>,
    ) {
        if (node == null) return
        if (predicate(node)) out += node
        for (i in 0 until node.childCount) collect(node.getChild(i), predicate, out)
    }

    // ---- Common predicates -------------------------------------------------------

    fun byText(vararg expected: String, ignoreCase: Boolean = true): Predicate = { n ->
        val t = n.text?.toString().orEmpty()
        if (t.isEmpty()) false else expected.any { it.equals(t, ignoreCase) }
    }

    fun byTextContains(vararg fragments: String, ignoreCase: Boolean = true): Predicate = { n ->
        val t = n.text?.toString().orEmpty()
        val d = n.contentDescription?.toString().orEmpty()
        if (t.isEmpty() && d.isEmpty()) false
        else fragments.any { f -> t.contains(f, ignoreCase) || d.contains(f, ignoreCase) }
    }

    fun byViewId(vararg viewIds: String): Predicate = { n ->
        val id = n.viewIdResourceName.orEmpty()
        if (id.isEmpty()) false else viewIds.any { it == id }
    }

    fun byClassNameSuffix(vararg suffixes: String): Predicate = { n ->
        val cls = n.className?.toString().orEmpty()
        if (cls.isEmpty()) false else suffixes.any { cls.endsWith(it, ignoreCase = true) }
    }

    fun isToggleable(): Predicate = byClassNameSuffix("Switch", "SwitchCompat", "MaterialSwitch", "CheckBox", "ToggleButton", "CompoundButton")

    // ---- Tree navigation utilities ------------------------------------------------

    /**
     * Walk up from [node] looking for the first ancestor whose subtree contains a
     * node matching [predicate] (other than [node] itself). Useful for "find the
     * sibling control next to this label".
     */
    fun nearestRelative(node: AccessibilityNodeInfo, predicate: Predicate): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node.parent
        while (current != null) {
            findFirst(current) { it != node && predicate(it) }?.let { return it }
            current = current.parent
        }
        return null
    }

    /** Climb to the first clickable ancestor (or the node itself if it is clickable). */
    fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) return node
        var current: AccessibilityNodeInfo? = node.parent
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    /** Try ACTION_CLICK on the node, then on its first clickable ancestor. */
    fun clickEffectively(node: AccessibilityNodeInfo): Boolean {
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val target = clickableAncestor(node) ?: return false
        if (target == node) return false
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}
