package com.openjarvis.voice

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class VoiceManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "voice_prefs"
        private const val KEY_VOICE_ENABLED = "voice_enabled"
        private const val KEY_STT_MODE = "stt_mode"
        private const val KEY_SPEAK_RESULTS = "speak_results"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_RECOGNITION_LANGUAGE = "recognition_language"
        private const val KEY_TTS_LANGUAGE = "tts_language"
        private const val KEY_TTS_VOICE = "tts_voice"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val sttEngine = AndroidSTTEngine(context)
    private val ttsEngine = AndroidTTSEngine(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    sealed class VoiceState {
        object Idle : VoiceState()
        object Recording : VoiceState()
        object Transcribing : VoiceState()
        data class Result(val text: String) : VoiceState()
        data class Error(val message: String) : VoiceState()
    }

    init {
        // Apply saved settings
        ttsEngine.setSpeechRate(getSpeechRate())
        sttEngine.setRecognitionLanguage(getRecognitionLanguage())
        ttsEngine.setPreferredLanguage(getTtsLanguage())
        ttsEngine.setVoiceName(getTtsVoiceName())
    }

    fun initialize() { reloadSettings() }

    /**
     * Re-apply persisted voice preferences to the live engines.
     * SettingsActivity and OverlayService own separate VoiceManager instances,
     * so writing SharedPreferences alone does not update the running engines.
     */
    fun reloadSettings() {
        ttsEngine.setSpeechRate(getSpeechRate())
        sttEngine.setRecognitionLanguage(getRecognitionLanguage())
        ttsEngine.setPreferredLanguage(getTtsLanguage())
        ttsEngine.setVoiceName(getTtsVoiceName())
    }

    fun isVoiceEnabled(): Boolean = prefs.getBoolean(KEY_VOICE_ENABLED, false)

    fun setVoiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply()
    }

    fun getSTTMode(): STTMode {
        val mode = prefs.getString(KEY_STT_MODE, STTMode.PUSH_TO_TALK.name)
        return try {
            STTMode.valueOf(mode ?: STTMode.PUSH_TO_TALK.name)
        } catch (e: Exception) {
            STTMode.PUSH_TO_TALK
        }
    }

    fun setSTTMode(mode: STTMode) {
        prefs.edit().putString(KEY_STT_MODE, mode.name).apply()
    }

    fun isSpeakResultsEnabled(): Boolean = prefs.getBoolean(KEY_SPEAK_RESULTS, true)

    fun setSpeakResultsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPEAK_RESULTS, enabled).apply()
    }

    fun getSpeechRate(): Float = prefs.getFloat(KEY_SPEECH_RATE, 0.96f)

    fun getRecognitionLanguage(): String = prefs.getString(KEY_RECOGNITION_LANGUAGE, "fa-IR") ?: "fa-IR"

    fun setRecognitionLanguage(languageTag: String) {
        val supported = setOf("fa-IR", "en-US", "en-AU", "es-ES", "de-DE")
        val safeLanguage = languageTag.takeIf { it in supported } ?: "fa-IR"
        prefs.edit().putString(KEY_RECOGNITION_LANGUAGE, safeLanguage).apply()
        sttEngine.setRecognitionLanguage(safeLanguage)
    }

    fun getTtsLanguage(): String =
        prefs.getString(KEY_TTS_LANGUAGE, getRecognitionLanguage()) ?: getRecognitionLanguage()

    fun setTtsLanguage(languageTag: String) {
        val supported = setOf("fa-IR", "en-US", "en-AU", "es-ES", "de-DE")
        val safeLanguage = languageTag.takeIf { it in supported } ?: "fa-IR"
        prefs.edit().putString(KEY_TTS_LANGUAGE, safeLanguage).apply()
        ttsEngine.setPreferredLanguage(safeLanguage)
    }

    fun getTtsVoiceName(): String? = prefs.getString(KEY_TTS_VOICE, null)

    fun setTtsVoiceName(name: String?) {
        prefs.edit().apply {
            if (name.isNullOrBlank()) remove(KEY_TTS_VOICE) else putString(KEY_TTS_VOICE, name)
        }.apply()
        ttsEngine.setVoiceName(name)
    }

    fun getAvailableTtsVoices(languageTag: String = getTtsLanguage()): List<String> =
        ttsEngine.getAvailableVoices(languageTag)

    fun isTtsLanguageAvailable(languageTag: String = getTtsLanguage()): Boolean =
        ttsEngine.isLanguageAvailable(languageTag)

    fun setSpeechRate(rate: Float) {
        prefs.edit().putFloat(KEY_SPEECH_RATE, rate.coerceIn(0.5f, 2.0f)).apply()
        ttsEngine.setSpeechRate(rate)
    }

    // Push-to-talk mode
    fun startListening() {
        scope.launch {
            _state.value = VoiceState.Recording

            sttEngine.startListening(
                onResult = { text ->
                    if (!text.isNullOrBlank()) {
                        _state.value = VoiceState.Result(text)
                    } else {
                        _state.value = VoiceState.Error("Could not understand audio")
                    }
                },
                onError = { error ->
                    _state.value = VoiceState.Error(error)
                }
            )
        }
    }

    fun stopListening(): String? {
        val text = sttEngine.stop()
        _state.value = if (!text.isNullOrBlank()) {
            VoiceState.Result(text)
        } else {
            VoiceState.Idle
        }
        return text
    }

    // Auto VAD mode
    fun startListeningWithVAD() {
        if (!isVoiceEnabled()) {
            _state.value = VoiceState.Error("Voice disabled in settings")
            return
        }

        scope.launch {
            // Android SpeechRecognizer performs its own end-of-speech detection.
            // This is not a continuous microphone VAD implementation.
            _state.value = VoiceState.Recording

            sttEngine.startListening(
                onResult = { text ->
                    if (!text.isNullOrBlank()) {
                        _state.value = VoiceState.Result(text)
                    } else {
                        _state.value = VoiceState.Idle
                    }
                },
                onError = { error ->
                    _state.value = VoiceState.Error(error)
                }
            )
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (isSpeakResultsEnabled()) ttsEngine.speak(text, onDone) else onDone?.invoke()
    }

    fun setSpeechDetectedListener(listener: (() -> Unit)?) {
        sttEngine.setSpeechDetectedListener(listener)
    }

    fun testSpeak(text: String) {
        ttsEngine.speak(text)
    }

    fun stopSpeaking() {
        ttsEngine.stop()
    }

    fun resetState() {
        if (_state.value is VoiceState.Result || _state.value is VoiceState.Error) {
            _state.value = VoiceState.Idle
        }
    }

    fun release() {
        sttEngine.release()
        ttsEngine.release()
        scope.cancel()
    }
}