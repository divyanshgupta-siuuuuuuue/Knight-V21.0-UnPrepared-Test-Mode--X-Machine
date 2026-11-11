package com.knight.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Lets Knight press Back/Home, scroll and tap for you. It does nothing on its own: it only acts when a voice command asks. */
class KnightAccessibilityService : AccessibilityService() {
    companion object { @Volatile var instance: KnightAccessibilityService? = null }
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val n = findScrollable(root) ?: return false
        return n.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }
    private fun findScrollable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isScrollable) return n
        for (i in 0 until n.childCount) { val c = n.getChild(i) ?: continue; findScrollable(c)?.let { return it } }
        return null
    }
    fun clickText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (n in root.findAccessibilityNodeInfosByText(text)) {
            var x: AccessibilityNodeInfo? = n
            while (x != null) { if (x.isClickable) return x.performAction(AccessibilityNodeInfo.ACTION_CLICK); x = x.parent }
        }
        return false
    }
    fun screenText(): String {
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?, d: Int) {
            if (n == null || d > 25 || sb.length > 1200) return
            n.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { sb.append(it).append(". ") }
            for (i in 0 until n.childCount) walk(n.getChild(i), d + 1)
        }
        walk(rootInActiveWindow, 0)
        return sb.toString().trim()
    }
}
