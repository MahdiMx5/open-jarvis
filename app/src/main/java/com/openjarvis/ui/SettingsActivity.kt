package com.openjarvis.ui

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
                    onSaveProvider = { name, baseUrl, apiKey, model ->
                        saveProviderSettings(name, baseUrl, apiKey, model)
                    },
                    voiceManager = voiceManager,
                    initialProviderName = prefs.getString("provider_name", "Groq") ?: "Groq",
                    initialBaseUrl = prefs.getString("provider_base_url", "") ?: "",
                    initialApiKey = prefs.getString("provider_api_key", "") ?: "",
                    initialModel = prefs.getString("provider_model", "llama-3.1-70b-versatile")
                        ?: "llama-3.1-70b-versatile",
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
            apply()
        }
    }
}