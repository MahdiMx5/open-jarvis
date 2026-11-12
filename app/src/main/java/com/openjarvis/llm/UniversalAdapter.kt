package com.openjarvis.llm

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.openjarvis.llm.providers.*
import com.openjarvis.local.ModelManager

class UniversalAdapter(private val context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "jarvis_encrypted_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun getActiveProvider(): LLMProvider {
        return getProviderForName(getProviderName())
            ?: CustomProvider("", "", "")
    }

    fun getProviderForName(name: String): LLMProvider? {
        val activeName = getProviderName()
        val suffix = name.lowercase().replace(Regex("[^a-z0-9]+"), "_")
        val useActiveSettings = name == activeName
        val baseUrl = if (useActiveSettings) {
            prefs.getString("provider_base_url", "") ?: ""
        } else {
            prefs.getString("provider_${suffix}_base_url", "") ?: ""
        }
        val apiKey = if (useActiveSettings) {
            prefs.getString("provider_api_key", "") ?: ""
        } else {
            prefs.getString("provider_${suffix}_api_key", "") ?: ""
        }
        val configuredModel = if (useActiveSettings) {
            prefs.getString("provider_model", "") ?: ""
        } else {
            prefs.getString("provider_${suffix}_model", "") ?: ""
        }
        // Automatic fallback is strictly limited to no-cost routes.
        // Never allow a saved paid model selection to leak into fallback traffic.
        val model = when (name) {
            "OpenRouter" -> "openrouter/free"
            "Groq" -> getDefaultModel("Groq")
            else -> configuredModel
        }

        return when (name) {
            "Groq" -> GroqProvider(apiKey, model.ifBlank { getDefaultModel(name) })
            "Google Gemini", "gemini" -> GeminiProvider(apiKey, model.ifBlank { getDefaultModel("Google Gemini") })
            "OpenRouter" -> OpenRouterProvider(apiKey, model.ifBlank { getDefaultModel(name) })
            "Anthropic Claude" -> AnthropicProvider(apiKey, model.ifBlank { getDefaultModel(name) })
            "OpenAI" -> OpenAIProvider(apiKey, model.ifBlank { getDefaultModel(name) })
            "Cerebras" -> CustomProvider("https://api.cerebras.ai/v1", apiKey, model.ifBlank { getDefaultModel(name) })
            "SambaNova Cloud" -> CustomProvider("https://api.sambanova.ai/v1", apiKey, model.ifBlank { getDefaultModel(name) })
            "NVIDIA NIM" -> CustomProvider("https://integrate.api.nvidia.com/v1", apiKey, model.ifBlank { getDefaultModel(name) })
            "Mistral API" -> CustomProvider("https://api.mistral.ai/v1", apiKey, model.ifBlank { getDefaultModel(name) })
            "Cloudflare Workers AI" -> CustomProvider(baseUrl, apiKey, model.ifBlank { getDefaultModel(name) })
            "Ollama (Local)" -> OllamaProvider(baseUrl, model.ifBlank { getDefaultModel(name) })
            "Local (LlamaCpp)" -> {
                val manager = getModelManager(context)
                val tier = manager.getDownloadedTier() ?: return null
                LlamaProvider(manager.getModelPath(tier), tier.displayName)
            }
            "Custom" -> CustomProvider(baseUrl, apiKey, model)
            else -> null
        }
    }

    fun isProviderConfigured(name: String): Boolean {
        val suffix = name.lowercase().replace(Regex("[^a-z0-9]+"), "_")
        val active = name == getProviderName()
        val key = if (active) prefs.getString("provider_api_key", "") else prefs.getString("provider_${suffix}_api_key", "")
        val url = if (active) prefs.getString("provider_base_url", "") else prefs.getString("provider_${suffix}_base_url", "")
        return when (name) {
            // A GGUF file alone is not a running inference engine. Keep this disabled
            // until the native llama.cpp runtime is integrated and verified.
            "Local (LlamaCpp)" -> false
            "Ollama (Local)" -> isPrivateOrLoopbackEndpoint(url)
            "Custom" -> !url.isNullOrBlank()
            "Cloudflare Workers AI" -> !url.isNullOrBlank() && !key.isNullOrBlank()
            else -> !key.isNullOrBlank()
        }
    }

    suspend fun complete(
        systemPrompt: String,
        userMessage: String
    ): Result<String> = getActiveProvider().complete(systemPrompt, userMessage)
    
    suspend fun testConnection(): Result<Long> = getActiveProvider().testConnection()
    
    fun saveSettings(name: String, baseUrl: String, apiKey: String, model: String) {
        val suffix = name.lowercase().replace(Regex("[^a-z0-9]+"), "_")
        prefs.edit().apply {
            // Keep a separate encrypted profile for every provider so switching never
            // overwrites another provider's key, URL, or model.
            putString("provider_${suffix}_base_url", baseUrl)
            putString("provider_${suffix}_api_key", apiKey)
            putString("provider_${suffix}_model", model)

            // Keep the legacy active-provider keys in sync for existing app code.
            putString("provider_name", name)
            putString("provider_base_url", baseUrl)
            putString("provider_api_key", apiKey)
            putString("provider_model", model)
            apply()
        }
        // A changed key or endpoint should be retried immediately, not held by an old cooldown.
        context.getSharedPreferences("provider_health", Context.MODE_PRIVATE).edit()
            .remove("${suffix}_retry_at")
            .remove("${suffix}_failures")
            .apply()
    }


    
    fun getProviderName(): String = prefs.getString("provider_name", "Groq") ?: "Groq"
    
    companion object {
        private var modelManager: ModelManager? = null
        
        val AVAILABLE_PROVIDERS = listOf(
            "Groq",
            "Google Gemini",
            "OpenRouter",
            "Anthropic Claude",
            "OpenAI",
            "Cerebras",
            "SambaNova Cloud",
            "NVIDIA NIM",
            "Mistral API",
            "Cloudflare Workers AI",
            "Ollama (Local)",
            "Local (LlamaCpp)",
            "Custom"
        )
        
        fun getModelManager(context: Context): ModelManager {
            return modelManager ?: synchronized(this) {
                modelManager ?: ModelManager(context.applicationContext).also { modelManager = it }
            }
        }
        
        fun isLocalModelLoaded(): Boolean {
            return modelManager?.state?.value is ModelManager.ModelState.Loaded
        }
        
        suspend fun loadLocalModel(tier: ModelManager.ModelTier): Result<Unit> {
            return modelManager?.loadModel(tier) ?: Result.failure(Exception("ModelManager not initialized"))
        }
        
        // Hard allowlist for automatic fallback. Providers whose free credits can
        // silently roll into billable usage are intentionally excluded.
        // OpenRouter is pinned to openrouter/free; Ollama must point to a private host.
        val FREE_FALLBACK_PROVIDERS = listOf(
            "Groq",
            "OpenRouter",
            "Ollama (Local)"
        )

        private fun isPrivateOrLoopbackEndpoint(rawUrl: String?): Boolean {
            if (rawUrl.isNullOrBlank()) return false
            val host = runCatching { android.net.Uri.parse(rawUrl).host?.lowercase() }.getOrNull()
                ?: return false
            return host == "localhost" ||
                host == "::1" ||
                host == "127.0.0.1" ||
                host.endsWith(".local") ||
                host.startsWith("10.") ||
                host.startsWith("192.168.") ||
                (host.startsWith("172.") &&
                    host.substringAfter("172.").substringBefore(".").toIntOrNull() in 16..31)
        }

        fun getDefaultModel(provider: String): String = when (provider) {
            "Groq" -> "openai/gpt-oss-20b"
            "Google Gemini" -> "gemini-1.5-flash"
            "OpenRouter" -> "openrouter/free"
            "Anthropic Claude" -> "claude-haiku-4-20250514"
            "OpenAI" -> "gpt-4o-mini"
            "Cerebras" -> "llama3.1-8b"
            "SambaNova Cloud" -> "Meta-Llama-3.3-70B-Instruct"
            "NVIDIA NIM" -> "meta/llama-3.1-8b-instruct"
            "Mistral API" -> "mistral-small-latest"
            "Cloudflare Workers AI" -> "@cf/meta/llama-3.1-8b-instruct"
            "Ollama (Local)" -> "llama3"
            else -> "gpt-4o-mini"
        }
    }
}