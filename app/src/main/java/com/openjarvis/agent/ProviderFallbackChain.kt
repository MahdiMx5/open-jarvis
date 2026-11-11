package com.openjarvis.agent

import android.content.Context
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.llm.LLMProvider
import com.openjarvis.llm.UniversalAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ProviderFallbackChain(private val context: Context) {

    private val graphifyRepo = GraphifyRepository(context)
    private val cooldownMap = mutableMapOf<String, Long>()
    private val _notification = MutableStateFlow<String?>(null)
    private val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)

    private val fallbackOrder: List<String>
        get() = prefs.getString(
            "fallback_order",
            "Groq,Google Gemini,OpenRouter,Anthropic Claude,OpenAI"
        )?.split(",")?.map(String::trim)?.filter(String::isNotBlank) ?: emptyList()

    suspend fun completeWithFallback(system: String, user: String): Result<String> {
        val adapter = UniversalAdapter(context)
        val activeName = adapter.getProviderName()
        val autoRouting = prefs.getBoolean("auto_ai_routing", true)
        val task = user.lowercase()
        val taskPriority = when {
            listOf("کدنویسی", "برنامه‌نویسی", "کد ", "debug", "compile", "gradle", "kotlin", "python", "code").any { task.contains(it) } ->
                listOf("OpenAI", "Anthropic Claude", "Groq", "OpenRouter", "Google Gemini")
            listOf("تحلیل", "مقایسه", "طراحی معماری", "تحقیق", "استدلال", "analyze", "research", "architecture", "reasoning").any { task.contains(it) } ->
                listOf("Anthropic Claude", "OpenAI", "Google Gemini", "OpenRouter", "Groq")
            listOf("ترجمه", "خلاصه", "سلام", "چطوری", "translate", "summarize", "hello").any { task.contains(it) } ->
                listOf("Google Gemini", "Groq", "OpenRouter", "OpenAI", "Anthropic Claude")
            else -> listOf("Groq", "Google Gemini", "OpenAI", "OpenRouter", "Anthropic Claude")
        }
        val orderedNames = if (autoRouting) {
            (taskPriority + listOf(activeName) + fallbackOrder).distinct()
        } else {
            (listOf(activeName) + fallbackOrder).distinct()
        }
        val providers = orderedNames.mapNotNull { name ->
            if (!adapter.isProviderConfigured(name) || isInCooldown(name)) return@mapNotNull null
            adapter.getProviderForName(name)?.let { provider -> provider to name }
        }

        if (providers.isEmpty()) {
            return Result.failure(IllegalStateException("No configured AI provider. Add an API key in Settings."))
        }

        var lastError: Throwable? = null
        for ((provider, name) in providers) {
            val result = runCatching { provider.complete(system, user) }.getOrElse {
                Result.failure(it)
            }
            if (result.isSuccess) {
                _notification.value = null
                return result
            }

            val error = result.exceptionOrNull() ?: IllegalStateException("Unknown provider error")
            lastError = error
            runCatching {
                graphifyRepo.logTask("provider:$name", "failed: ${error.message.orEmpty()}", name, 0L)
            }

            val message = error.message.orEmpty()
            when {
                message.contains("429") -> {
                    cooldownMap[name] = System.currentTimeMillis() + 60_000L
                    _notification.value = "سرویس $name محدودیت درخواست دارد؛ دارم سرویس بعدی را امتحان می‌کنم."
                }
                message.contains("401") || message.contains("403") -> {
                    cooldownMap[name] = System.currentTimeMillis() + 3_600_000L
                    _notification.value = "کلید دسترسی $name مشکل دارد؛ دارم سرویس بعدی را امتحان می‌کنم."
                }
            }
        }

        return Result.failure(
            IllegalStateException("همه سرویس‌های تنظیم‌شده ناموفق بودند.", lastError)
        )
    }

    private fun isInCooldown(name: String): Boolean {
        val until = cooldownMap[name] ?: return false
        if (System.currentTimeMillis() >= until) {
            cooldownMap.remove(name)
            return false
        }
        return true
    }

    fun getNotification(): StateFlow<String?> = _notification
}

object JarvisErrorTranslator {

    fun translate(error: Throwable, providerName: String?): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("401") || message.contains("403") ->
                "کلید دسترسی ${providerName ?: "سرویس"} معتبر نیست؛ تنظیماتش رو بررسی کن."
            message.contains("429") ->
                "تعداد درخواست‌ها زیاد شده؛ کمی بعد دوباره امتحان کن."
            message.contains("503") ->
                "سرویس هوش مصنوعی فعلاً در دسترس نیست."
            message.contains("timeout", ignoreCase = true) ->
                "اتصال زمان زیادی برد؛ اینترنت رو بررسی کن."
            message.contains("Unable to resolve host", ignoreCase = true) ->
                "اینترنت در دسترس نیست."
            error.message?.contains("SecurityException") == true ->
                "اجازهٔ لازم داده نشده؛ دسترسی‌پذیری رو بررسی کن."
            else -> "یه مشکلی پیش اومد: ${message.take(100).ifBlank { "خطای ناشناخته" }}"
        }
    }
}
