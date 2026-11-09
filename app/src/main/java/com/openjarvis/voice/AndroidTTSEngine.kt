package com.openjarvis.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

class AndroidTTSEngine(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var speechRate = 0.96f
    private var pitch = 1.0f
    @Volatile private var preferredLanguageTag = "fa-IR"

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                tts?.setLanguage(Locale.US)
                tts?.setSpeechRate(speechRate)
                tts?.setPitch(pitch)
                
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    override fun onError(utteranceId: String?) {}
                })
            }
        }
    }

    fun speak(text: String) {
        if (!isReady) return
        
        val clean = text
            .replace(Regex("\\[\\{.*?\\}\\]"), "")
            .replace(Regex("[\\[\\]{}\":]"), " ")
            .trim()
        
        if (clean.isBlank()) return
        
        selectLanguageFor(clean)
        val utteranceId = "jarvis_${UUID.randomUUID()}"
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun speakQueued(text: String) {
        if (!isReady) return
        
        val clean = text
            .replace(Regex("\\[\\{.*?\\}\\]"), "")
            .replace(Regex("[\\[\\]{}\":]"), " ")
            .trim()
        
        if (clean.isBlank()) return
        
        selectLanguageFor(clean)
        val utteranceId = "jarvis_${UUID.randomUUID()}"
        tts?.speak(clean, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    fun setPreferredLanguage(languageTag: String) {
        val supported = setOf("fa-IR", "en-US", "en-AU", "es-ES", "de-DE")
        preferredLanguageTag = languageTag.takeIf { it in supported } ?: "fa-IR"
    }

    private fun selectLanguageFor(text: String) {
        val hasPersian = text.any { it in '\u0600'..'\u06FF' }
        val locale = if (hasPersian) Locale.forLanguageTag("fa-IR") else Locale.forLanguageTag(preferredLanguageTag)
        val result = tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (result >= TextToSpeech.LANG_AVAILABLE) {
            tts?.language = locale
        } else {
            // Keep the engine's currently configured language; do not silently
            // claim that a Persian voice is installed when the device lacks one.
            tts?.language = Locale.getDefault()
        }
    }

    fun stop() {
        tts?.stop()
    }

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
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}