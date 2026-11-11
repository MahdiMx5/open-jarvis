package com.openjarvis.ui.chat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChatMessage(val id: String, val role: String, val text: String, val timestamp: Long)
data class ChatThread(val id: String, val title: String, val updatedAt: Long, val messages: List<ChatMessage>)

object ChatStorage {
    private const val PREFS = "jarvis_chat_history"
    private const val KEY_THREADS = "threads"
    private const val KEY_CURRENT = "current_thread"

    fun loadThreads(context: Context): List<ChatThread> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_THREADS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                val messageArray = obj.optJSONArray("messages") ?: JSONArray()
                val messages = (0 until messageArray.length()).map { messageIndex ->
                    val item = messageArray.getJSONObject(messageIndex)
                    ChatMessage(item.optString("id"), item.optString("role", "assistant"), item.optString("text"), item.optLong("timestamp"))
                }
                ChatThread(obj.optString("id"), obj.optString("title", "گفت‌وگوی جدید"), obj.optLong("updatedAt"), messages)
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun currentThreadId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CURRENT, null)

    fun setCurrentThreadId(context: Context, id: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (id == null) remove(KEY_CURRENT) else putString(KEY_CURRENT, id)
        }.apply()
    }

    fun appendMessage(context: Context, threadId: String?, role: String, text: String): ChatThread {
        val threads = loadThreads(context).toMutableList()
        val now = System.currentTimeMillis()
        val index = threads.indexOfFirst { it.id == threadId }
        val base = if (index >= 0) threads[index] else ChatThread(
            UUID.randomUUID().toString(),
            if (role == "user") text.take(42) else "گفت‌وگوی جدید",
            now,
            emptyList()
        )
        val updated = base.copy(
            title = if (base.messages.isEmpty() && role == "user") text.take(42) else base.title,
            updatedAt = now,
            messages = base.messages + ChatMessage(UUID.randomUUID().toString(), role, text, now)
        )
        if (index >= 0) threads[index] = updated else threads.add(updated)
        val encoded = JSONArray()
        threads.sortedByDescending { it.updatedAt }.take(30).forEach { thread ->
            val messageArray = JSONArray()
            thread.messages.forEach { message ->
                messageArray.put(JSONObject().apply {
                    put("id", message.id); put("role", message.role); put("text", message.text); put("timestamp", message.timestamp)
                })
            }
            encoded.put(JSONObject().apply {
                put("id", thread.id); put("title", thread.title); put("updatedAt", thread.updatedAt); put("messages", messageArray)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_THREADS, encoded.toString()).putString(KEY_CURRENT, updated.id).apply()
        return updated
    }

    fun findThread(context: Context, id: String?): ChatThread? =
        if (id == null) null else loadThreads(context).firstOrNull { it.id == id }
}
