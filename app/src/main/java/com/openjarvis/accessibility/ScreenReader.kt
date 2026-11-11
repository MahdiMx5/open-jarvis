package com.openjarvis.accessibility

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

class ScreenReader(@Suppress("UNUSED_PARAMETER") context: Context) {

    fun extractAllText(): String {
        val rootNode = JarvisAccessibilityService.instance?.rootInActiveWindow ?: return ""
        val builder = StringBuilder()
        extractTextRecursive(rootNode, builder)
        rootNode.recycle()
        return builder.toString()
    }

    private fun extractTextRecursive(node: AccessibilityNodeInfo, builder: StringBuilder) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
            builder.append(it).append(" ")
        }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let {
            builder.append(it).append(" ")
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                extractTextRecursive(child, builder)
                child.recycle()
            }
        }
    }

    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val rootNode = JarvisAccessibilityService.instance?.rootInActiveWindow ?: return null
        val result = findNodeRecursive(rootNode, text)
        rootNode.recycle()
        return result
    }

    private fun findNodeRecursive(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val normalizedText = text.trim().lowercase()
        if (normalizedText.isEmpty()) return null

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val nodeText = current.text?.toString()?.lowercase()
            val contentDesc = current.contentDescription?.toString()?.lowercase()
            if (nodeText?.contains(normalizedText) == true ||
                contentDesc?.contains(normalizedText) == true
            ) {
                val result = AccessibilityNodeInfo.obtain(current)
                if (current !== root) current.recycle()
                while (queue.isNotEmpty()) queue.removeFirst().recycle()
                return result
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let(queue::add)
            }
            if (current !== root) current.recycle()
        }
        return null
    }

    fun findNodeByHint(hint: String): AccessibilityNodeInfo? {
        val rootNode = JarvisAccessibilityService.instance?.rootInActiveWindow ?: return null
        val result = findHintRecursive(rootNode, hint)
        rootNode.recycle()
        return result
    }

    private fun findHintRecursive(root: AccessibilityNodeInfo, hint: String): AccessibilityNodeInfo? {
        val normalizedHint = hint.trim().lowercase()
        if (normalizedHint.isEmpty()) return null

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current.hintText?.toString()?.lowercase()?.contains(normalizedHint) == true) {
                val result = AccessibilityNodeInfo.obtain(current)
                if (current !== root) current.recycle()
                while (queue.isNotEmpty()) queue.removeFirst().recycle()
                return result
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let(queue::add)
            }
            if (current !== root) current.recycle()
        }
        return null
    }

    fun getFocusedNode(): AccessibilityNodeInfo? {
        val rootNode = JarvisAccessibilityService.instance?.rootInActiveWindow ?: return null
        val focused = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        rootNode.recycle()
        return focused
    }
}
