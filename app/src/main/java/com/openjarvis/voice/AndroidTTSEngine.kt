package com.openjarvis.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AndroidTTSEngine(private val context: Context) {
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var isReady = false
    private var speechRate = 0.96f
    private var pitch = 1.0f
    @Volatile private var preferredLanguageTag = "fa-IR"
    @Volatile private var preferredVoiceName: String? = null
    private val completionCallbacks = ConcurrentHashMap<String, () -> Unit>()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setSpeechRate(speechRate)
                tts?.setPitch(pitch)
                isReady = true
                configureLanguage(Locale.forLanguageTag(preferredLanguageTag))
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        completionCallbacks.remove(utteranceId)?.invoke()
                    }
                    override fun onError(utteranceId: String?) {
                        completionCallbacks.remove(utteranceId)?.invoke()
                    }
                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        completionCallbacks.remove(utteranceId)?.invoke()
                    }
                })
            }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isReady) return
        val clean = text
            .replace(Regex("\\[\\{.*?\\}\\]"), "")
            .replace(Regex("""[\[\]{}":]"""), " ")
            .trim()
        if (clean.isBlank()) return

        if (!selectLanguageFor(clean)) return
        val utteranceId = "jarvis_${UUID.randomUUID()}"
        if (onDone != null) completionCallbacks[utteranceId] = onDone
        val result = tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) completionCallbacks.remove(utteranceId)?.invoke()
    }

    fun speakQueued(text: String) {
        if (!isReady) return
        val clean = text
            .replace(Regex("\\[\\{.*?\\}\\]"), "")
            .replace(Regex("""[\[\]{}":]"""), " ")
            .trim()
        if (clean.isBlank()) return

        selectLanguageFor(clean)
        val utteranceId = "jarvis_${UUID.randomUUID()}"
        tts?.speak(clean, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    fun setPreferredLanguage(languageTag: String) {
        val supported = setOf("fa-IR", "en-US", "en-AU", "es-ES", "de-DE")
        preferredLanguageTag = languageTag.takeIf { it in supported } ?: "fa-IR"
        if (isReady) configureLanguage(Locale.forLanguageTag(preferredLanguageTag))
    }

    private fun selectLanguageFor(text: String): Boolean {
        val targetLocale = if (text.any { it.code in 0x0600..0x06FF }) {
            Locale.forLanguageTag("fa-IR")
        } else {
            Locale.forLanguageTag(preferredLanguageTag)
        }
        return configureLanguage(targetLocale)
    }

    private fun configureLanguage(targetLocale: Locale): Boolean {
        val engine = tts ?: return false
        val result = engine.setLanguage(targetLocale)
        if (result < TextToSpeech.LANG_AVAILABLE) return false
        applyPreferredVoice(targetLocale)
        return true
    }

    fun isLanguageAvailable(languageTag: String = preferredLanguageTag): Boolean {
        val engine = tts ?: return false
        return engine.isLanguageAvailable(Locale.forLanguageTag(languageTag)) >= TextToSpeech.LANG_AVAILABLE
    }

    fun getAvailableVoices(languageTag: String = preferredLanguageTag): List<String> {
        if (!isReady) return emptyList()
        val requestedLocale = Locale.forLanguageTag(languageTag)
        return tts?.voices
            ?.filter { voiceMatchesLocale(it, requestedLocale) }
            ?.map { it.name }
            ?.distinct()
            ?.sorted()
            .orEmpty()
    }

    fun setVoiceName(name: String?) {
        preferredVoiceName = name?.takeIf { it.isNotBlank() }
        if (isReady) configureLanguage(Locale.forLanguageTag(preferredLanguageTag))
    }

    private fun applyPreferredVoice(activeLocale: Locale) {
        val name = preferredVoiceName ?: return
        val selected = tts?.voices?.firstOrNull {
            it.name == name && voiceMatchesLocale(it, activeLocale)
        } ?: return
        try {
            tts?.voice = selected
        } catch (_: IllegalArgumentException) {
            // Retain the engine-selected default for this language.
        }
    }

    private fun voiceMatchesLocale(voice: Voice, requested: Locale): Boolean =
        voiceMatchesLocale(voice.locale, requested)

    private fun voiceMatchesLocale(voiceLocale: Locale, requested: Locale): Boolean {
        if (!voiceLocale.language.equals(requested.language, ignoreCase = true)) return false
        return requested.country.isBlank() ||
            voiceLocale.country.isBlank() ||
            voiceLocale.country.equals(requested.country, ignoreCase = true)
    }

    fun stop() { tts?.stop() }

    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(speechRate)
    }

    fun setPitch(pitchValue: Float) {
        pitch = pitchValue.coerceIn(0.5f, 2.0f)
        tts?.setPitch(pitch)
    }

    fun getSpeechRate(): Float = speechRate
    fun getPitch(): Float = pitch
    fun isSpeaking(): Boolean = tts?.isSpeaking ?: false

    fun release() {
        isReady = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        completionCallbacks.clear()
    }
}
