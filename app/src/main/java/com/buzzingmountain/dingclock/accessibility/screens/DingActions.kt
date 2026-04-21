package com.buzzingmountain.dingclock.accessibility.screens

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.buzzingmountain.dingclock.accessibility.AccessibilityBridge
import com.buzzingmountain.dingclock.accessibility.NodeFinder

/**
 * Higher-level actions on top of [NodeFinder] used by the state machine and login flow.
 */
object DingActions {

    /** Click the first node whose text equals (or contains) any of [labels]. */
    suspend fun clickByText(vararg labels: String, timeoutMs: Long = 8_000, tag: String = "click"): Boolean {
        val node = AccessibilityBridge.awaitNode(timeoutMs = timeoutMs, tag = tag) { n ->
            val t = n.text?.toString().orEmpty()
            val d = n.contentDescription?.toString().orEmpty()
            labels.any { lbl -> t == lbl || t.contains(lbl) || d == lbl || d.contains(lbl) }
        } ?: return false
        return NodeFinder.clickEffectively(node)
    }

    /**
     * Like [clickByText] but only matches on exact text/contentDescription equality. Use when
     * a containing substring would cause a false positive (e.g. matching "密码登录" when looking
     * for "登录").
     */
    suspend fun clickByTextExact(vararg labels: String, timeoutMs: Long = 8_000, tag: String = "click-exact"): Boolean {
        val node = AccessibilityBridge.awaitNode(timeoutMs = timeoutMs, tag = tag) { n ->
            val t = n.text?.toString().orEmpty()
            val d = n.contentDescription?.toString().orEmpty()
            labels.any { lbl -> t == lbl || d == lbl }
        } ?: return false
        return NodeFinder.clickEffectively(node)
    }

    /** Find an EditText whose hint or text contains [hint], then ACTION_SET_TEXT it. */
    suspend fun setEditText(hint: String, value: String, timeoutMs: Long = 6_000): Boolean {
        val edit = AccessibilityBridge.awaitNode(
            timeoutMs = timeoutMs,
            tag = "edit:$hint",
            predicate = { node ->
                val cls = node.className?.toString().orEmpty()
                if (!cls.endsWith("EditText", ignoreCase = true)) return@awaitNode false
                val txt = node.text?.toString().orEmpty()
                val desc = node.contentDescription?.toString().orEmpty()
                val hintTxt = node.hintText?.toString().orEmpty()
                hint.isEmpty() ||
                    txt.contains(hint, ignoreCase = true) ||
                    desc.contains(hint, ignoreCase = true) ||
                    hintTxt.contains(hint, ignoreCase = true)
            },
        ) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Find any password EditText (inputType TYPE_TEXT_VARIATION_PASSWORD ≈ class endsWith EditText + isPassword). */
    suspend fun setPasswordField(value: String, timeoutMs: Long = 6_000): Boolean {
        val edit = AccessibilityBridge.awaitNode(
            timeoutMs = timeoutMs,
            tag = "password-edit",
            predicate = { node ->
                val cls = node.className?.toString().orEmpty()
                cls.endsWith("EditText", ignoreCase = true) && node.isPassword
            },
        ) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}
