package com.openjarvis.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.openjarvis.R
import com.openjarvis.llm.UniversalAdapter
import com.openjarvis.ui.settings.ProviderFormSettings
import com.openjarvis.ui.settings.SettingsScreen
import com.openjarvis.ui.theme.OpenJarvisTheme
import com.openjarvis.voice.VoiceManager

class SettingsActivity : ComponentActivity() {
    
    private lateinit var masterKey: MasterKey
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var voiceManager: VoiceManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        prefs = EncryptedSharedPreferences.create(
            this,
            "jarvis_encrypted_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        voiceManager = VoiceManager(applicationContext)

        setContent {
            OpenJarvisTheme {
                SettingsScreen(
                    onNavigateBack = { finish() },
                    onOpenProfile = { startActivity(Intent(this@SettingsActivity, ProfileActivity::class.java)) },
                    onSaveProvider = { name, baseUrl, apiKey, model ->
                        saveProviderSettings(name, baseUrl, apiKey, model)
                    },
                    onLoadProvider = { name ->
                        val providerKey = name.lowercase().replace(Regex("[^a-z0-9]+"), "_")
                        val activeProvider = prefs.getString("provider_name", "Groq") ?: "Groq"
                        val legacyFallback = name == activeProvider
                        val defaultModel = UniversalAdapter.getDefaultModel(name)
                        val storedModel = prefs.getString(
                            "provider_${providerKey}_model",
                            if (legacyFallback) prefs.getString("provider_model", defaultModel) else defaultModel
                        ) ?: defaultModel
                        val selectedModel = if (
                            name == "Groq" && storedModel == "llama-3.1-70b-versatile"
                        ) defaultModel else storedModel
                        ProviderFormSettings(
                            baseUrl = prefs.getString(
                                "provider_${providerKey}_base_url",
                                if (legacyFallback) prefs.getString("provider_base_url", "") else ""
                            ) ?: "",
                            apiKey = prefs.getString(
                                "provider_${providerKey}_api_key",
                                if (legacyFallback) prefs.getString("provider_api_key", "") else ""
                            ) ?: "",
                            model = selectedModel
                        )
                    },
                    onTestConnection = {
                        UniversalAdapter(applicationContext).testConnection()
                    },
                    voiceManager = voiceManager,
                    initialProviderName = prefs.getString("provider_name", "Groq") ?: "Groq",
                    initialBaseUrl = prefs.getString("provider_base_url", "") ?: "",
                    initialApiKey = prefs.getString("provider_api_key", "") ?: "",
                    initialModel = run {
                        val provider = prefs.getString("provider_name", "Groq") ?: "Groq"
                        val defaultModel = UniversalAdapter.getDefaultModel(provider)
                        val savedModel = prefs.getString("provider_model", defaultModel) ?: defaultModel
                        if (provider == "Groq" && savedModel == "llama-3.1-70b-versatile") defaultModel else savedModel
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    
    override fun onDestroy() {
        if (::voiceManager.isInitialized) voiceManager.release()
        super.onDestroy()
    }

    private fun saveProviderSettings(name: String, baseUrl: String, apiKey: String, model: String) {
        prefs.edit().apply {
            putString("provider_name", name)
            putString("provider_base_url", baseUrl)
            putString("provider_api_key", apiKey)
            putString("provider_model", model)
            val providerKey = name.lowercase().replace(Regex("[^a-z0-9]+"), "_")
            putString("provider_${providerKey}_base_url", baseUrl)
            putString("provider_${providerKey}_api_key", apiKey)
            putString("provider_${providerKey}_model", model)
            apply()
        }
    }
}