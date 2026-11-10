package com.openjarvis.llm.providers

import com.openjarvis.llm.ConnectionResult
import com.openjarvis.llm.HttpClient
import com.openjarvis.llm.LLMProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GroqProvider(
    private val apiKey: String,
    private val model: String = "openai/gpt-oss-20b"
) : LLMProvider {
    
    override val name: String = "Groq"
    override val baseUrl: String = "https://api.groq.com/openai/v1"
    
    override suspend fun complete(
        systemPrompt: String,
        userMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    })
                })
                put("max_tokens", 1024)
                put("temperature", 0.1)
                put("stream", false)
            }.toString()
            
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            HttpClient.client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw Exception("Empty response")
                val json = JSONObject(body)
                if (json.has("choices")) {
                    json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                } else if (json.has("error")) {
                    throw Exception(json.getJSONObject("error").getString("message"))
                } else {
                    throw Exception("Unknown response: $body")
                }
            }
        }
    }
    
    override suspend fun testConnection(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val startTime = System.currentTimeMillis()
            val request = Request.Builder()
                .url("$baseUrl/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            
            HttpClient.client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val providerMessage = runCatching {
                        JSONObject(responseBody).optJSONObject("error")?.optString("message")
                    }.getOrNull().orEmpty()
                    val reason = when (response.code) {
                        401 -> "Groq API key is invalid or expired"
                        403 -> "Groq rejected this key or its permissions"
                        404 -> "Groq API endpoint or model was not found"
                        429 -> "Groq rate limit or quota reached"
                        in 500..599 -> "Groq service is temporarily unavailable"
                        else -> providerMessage.ifBlank { "Groq connection failed" }
                    }
                    throw Exception("$reason (HTTP ${response.code})")
                }
            }
            System.currentTimeMillis() - startTime
        }
    }
}
