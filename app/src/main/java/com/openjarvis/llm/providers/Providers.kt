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

private fun readProviderResponse(response: okhttp3.Response, providerName: String): String {
    val body = response.body?.string().orEmpty()
    if (!response.isSuccessful) {
        val detail = runCatching {
            val json = JSONObject(body)
            when {
                json.has("error") && json.opt("error") is JSONObject ->
                    json.getJSONObject("error").optString("message")
                json.has("error") -> json.optString("error")
                json.has("message") -> json.optString("message")
                else -> body.take(500)
            }
        }.getOrDefault(body.take(500))
        throw IllegalStateException(
            "$providerName request failed (HTTP ${response.code}): ${detail.ifBlank { "No error details returned" }}"
        )
    }
    if (body.isBlank()) throw IllegalStateException("$providerName returned an empty response")
    return body
}

class OpenRouterProvider(
    private val apiKey: String,
    private val model: String = "openrouter/free"
) : LLMProvider {
    
    override val name: String = "OpenRouter"
    override val baseUrl: String = "https://openrouter.ai/api/v1"
    
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
            }.toString()
            
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://github.com/tokenarc/open-jarvis")
                .addHeader("X-Title", "Open Jarvis")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            HttpClient.client.newCall(request).execute().use { response ->
                val body = readProviderResponse(response, name)
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
            // Validate the exact chat-completions route and selected model, not just API-key access.
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().put(
                    JSONObject().put("role", "user").put("content", "Reply with OK.")
                ))
                put("max_tokens", 8)
            }.toString()
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://github.com/MahdiMx5/open-jarvis")
                .addHeader("X-Title", "Open MX5")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            HttpClient.client.newCall(request).execute().use { response ->
                val body = readProviderResponse(response, name)
                val json = JSONObject(body)
                if (!json.has("choices") || json.getJSONArray("choices").length() == 0) {
                    throw IllegalStateException("OpenRouter test returned no completion choices")
                }
            }
            System.currentTimeMillis() - startTime
        }
    }
}

class AnthropicProvider(
    private val apiKey: String,
    private val model: String = "claude-haiku-4-20250514"
) : LLMProvider {
    
    override val name: String = "Anthropic Claude"
    override val baseUrl: String = "https://api.anthropic.com/v1"
    
    override suspend fun complete(
        systemPrompt: String,
        userMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = JSONObject().apply {
                put("model", model)
                put("max_tokens", 1024)
                put("system", systemPrompt)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    })
                })
            }.toString()
            
            val request = Request.Builder()
                .url("$baseUrl/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            HttpClient.client.newCall(request).execute().use { response ->
                val body = readProviderResponse(response, name)
                val json = JSONObject(body)
                if (json.has("content")) {
                    json.getJSONArray("content")
                        .getJSONObject(0)
                        .getString("text")
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
            val requestBody = JSONObject().apply {
                put("model", model)
                put("max_tokens", 1)
                put("messages", JSONArray().put(
                    JSONObject().put("role", "user").put("content", "ping")
                ))
            }.toString()
            
            val request = Request.Builder()
                .url("$baseUrl/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            HttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}")
                }
            }
            System.currentTimeMillis() - startTime
        }
    }
}

class OpenAIProvider(
    private val apiKey: String,
    private val model: String = "gpt-4o-mini"
) : LLMProvider {
    
    override val name: String = "OpenAI"
    override val baseUrl: String = "https://api.openai.com/v1"
    
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
            }.toString()
            
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            HttpClient.client.newCall(request).execute().use { response ->
                val body = readProviderResponse(response, name)
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
            // Test the same generation endpoint used by chat. A successful /models
            // response alone does not prove that this key can use the selected model.
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().put(
                    JSONObject().put("role", "user").put("content", "Reply with OK.")
                ))
                put("max_tokens", 1)
            }.toString()
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            HttpClient.client.newCall(request).execute().use { response ->
                val body = readProviderResponse(response, name)
                val json = JSONObject(body)
                if (!json.has("choices") || json.getJSONArray("choices").length() == 0) {
                    throw IllegalStateException("OpenAI test returned no completion choices")
                }
            }
            System.currentTimeMillis() - startTime
        }
    }
}

class OllamaProvider(
    override val baseUrl: String,
    private val model: String = "llama3"
) : LLMProvider {
    
    override val name: String = "Ollama"
    
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
                put("stream", false)
            }.toString()
            
            val request = Request.Builder()
                .url("$baseUrl/api/chat")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            HttpClient.client.newCall(request).execute().use { response ->
                val body = readProviderResponse(response, name)
                val json = JSONObject(body)
                if (json.has("message")) {
                    json.getJSONObject("message").getString("content")
                } else if (json.has("error")) {
                    throw Exception(json.getString("error"))
                } else {
                    throw Exception("Unknown response: $body")
                }
            }
        }
    }
    
    override suspend fun testConnection(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val startTime = System.currentTimeMillis()
            val requestBody = JSONObject().apply {
                put("model", model)
            }.toString()
            
            val request = Request.Builder()
                .url("$baseUrl/api/tags")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            HttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}")
                }
            }
            System.currentTimeMillis() - startTime
        }
    }
}

class CustomProvider(
    override val baseUrl: String,
    private val apiKey: String = "",
    private val model: String = "gpt-4o-mini"
) : LLMProvider {
    
    override val name: String = "Custom"
    
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
            }.toString()
            
            val builder = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
            
            if (apiKey.isNotBlank()) {
                builder.addHeader("Authorization", "Bearer $apiKey")
            }
            
            HttpClient.client.newCall(builder.build()).execute().use { response ->
                val body = readProviderResponse(response, name)
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
            
            val builder = Request.Builder()
                .url("$baseUrl/models")
                .get()
            
            if (apiKey.isNotBlank()) {
                builder.addHeader("Authorization", "Bearer $apiKey")
            }
            
            HttpClient.client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}")
                }
            }
            System.currentTimeMillis() - startTime
        }
    }
}