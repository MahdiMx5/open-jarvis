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
        val model = if (useActiveSettings) {
            prefs.getString("provider_model", "") ?: ""
        } else {
            prefs.getString("provider_${suffix}_model", "") ?: ""
        }

        return when (name) {
            "Groq" -> GroqProvider(apiKey, model.ifBlank { getDefaultModel(name) })
            "Google Gemini", "gemini" -> GeminiProvider(apiKey, model.ifBlank { getDefaultModel("Google Gemini") })
            "OpenRouter" -> OpenRouterProvider(apiKey, model.ifBlank { getDefaultModel(name) })
            "Anthropic Claude" -> AnthropicProvider(apiKey, model.ifBlank { getDefaultModel(name) })
            "OpenAI" -> OpenAIProvider(apiKey, model.ifBlank { getDefaultModel(name) })
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
            "Local (LlamaCpp)" -> getModelManager(context).getDownloadedTier() != null
            "Ollama (Local)", "Custom" -> !url.isNullOrBlank()
            else -> !key.isNullOrBlank()
        }
    }

    suspend fun complete(
        systemPrompt: String,
        userMessage: String
    ): Result<String> = getActiveProvider().complete(systemPrompt, userMessage)
    
    suspend fun testConnection(): Result<Long> = getActiveProvider().testConnection()
    
    fun saveSettings(name: String, baseUrl: String, apiKey: String, model: String) {
        prefs.edit().apply {
            putString("provider_name", name)
            putString("provider_base_url", baseUrl)
            putString("provider_api_key", apiKey)
            putString("provider_model", model)
            apply()
        }
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
        
        fun getDefaultModel(provider: String): String = when (provider) {
            "Groq" -> "openai/gpt-oss-20b"
            "Google Gemini" -> "gemini-1.5-flash"
            "OpenRouter" -> "meta-llama/llama-3-8b-instruct:free"
            "Anthropic Claude" -> "claude-haiku-4-20250514"
            "OpenAI" -> "gpt-4o-mini"
            "Ollama (Local)" -> "llama3"
            else -> "gpt-4o-mini"
        }
    }
}