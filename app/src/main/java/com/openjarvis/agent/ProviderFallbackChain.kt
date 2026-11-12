package com.openjarvis.agent

import android.content.Context
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.llm.UniversalAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Hybrid routing: try the selected provider first, then other providers for which
 * the user has already saved credentials. Never invent credentials or silently
 * use an unconfigured account.
 */
class ProviderFallbackChain(private val context: Context) {

    private val graphifyRepo = GraphifyRepository(context)
    private val _notification = MutableStateFlow<String?>(null)

    suspend fun completeWithFallback(system: String, user: String): Result<String> {
        val adapter = UniversalAdapter(context)
        val activeName = adapter.getProviderName()
        val candidates = listOf(activeName) +
            UniversalAdapter.AVAILABLE_PROVIDERS.filter { it != activeName }

        val configured = candidates.filter { adapter.isProviderConfigured(it) }
        if (configured.isEmpty()) {
            return Result.failure(
                IllegalStateException("هیچ ارائه‌دهندهٔ آنلاینِ پیکربندی‌شده‌ای وجود ندارد. برای پاسخ آفلاین، فعلاً فقط فرمان‌های پایه در دسترس‌اند.")
            )
        }

        val failures = mutableListOf<String>()
        for (name in configured) {
            val provider = adapter.getProviderForName(name)
            if (provider == null) {
                failures += "$name: provider unavailable"
                continue
            }

            val result = runCatching { provider.complete(system, user) }
                .getOrElse { Result.failure(it) }

            result.fold(
                onSuccess = { answer ->
                    if (answer.isNotBlank()) {
                        _notification.value = if (name == activeName) null
                            else "سرویس اصلی در دسترس نبود؛ پاسخ از $name دریافت شد."
                        runCatching {
                            graphifyRepo.logTask("provider:$name", "chat completed", name, 0L)
                        }
                        return Result.success(answer)
                    }
                    failures += "$name: empty response"
                },
                onFailure = { error ->
                    failures += "$name: ${error.message.orEmpty().take(120)}"
                    runCatching {
                        graphifyRepo.logTask(
                            "provider:$name",
                            "failed: ${error.message.orEmpty()}",
                            name,
                            0L
                        )
                    }
                }
            )
        }

        val message = failures.joinToString(" | ").take(700)
        _notification.value = "همهٔ سرویس‌های تنظیم‌شده ناموفق بودند: $message"
        return Result.failure(
            IllegalStateException(
                "هیچ سرویس آنلاینِ تنظیم‌شده‌ای پاسخ نداد. کلید و سهمیهٔ سرویس‌ها را بررسی کن. $message"
            )
        )
    }

    fun getNotification(): StateFlow<String?> = _notification
}

object JarvisErrorTranslator {

    fun translate(error: Throwable, providerName: String?): String {
        val root = generateSequence(error) { it.cause }.last()
        val message = root.message.orEmpty()
        return when {
            message.contains("401") || message.contains("403") ->
                "کلید دسترسی ${providerName ?: "سرویس"} معتبر نیست؛ کلید و دسترسی حساب را بررسی کن."
            message.contains("429") ->
                "محدودیت درخواست یا اعتبار سرویس اعمال شده است؛ وضعیت سهمیه را بررسی کن."
            message.contains("404") ->
                "مدل یا نشانی API پیدا نشد؛ نام مدل و ارائه‌دهنده را بررسی کن."
            message.contains("400") ->
                "درخواست توسط سرویس رد شد: ${message.take(140)}"
            message.contains("5") && message.contains("HTTP") ->
                "سرویس هوش مصنوعی موقتاً دچار مشکل شده است."
            message.contains("timeout", ignoreCase = true) ->
                "اتصال زمان زیادی برد؛ اینترنت و VPN را بررسی کن."
            message.contains("Unable to resolve host", ignoreCase = true) ->
                "نشانی سرویس پیدا نشد؛ اتصال اینترنت را بررسی کن."
            else -> "خطای ${providerName ?: "هوش مصنوعی"}: ${message.take(180).ifBlank { "خطای ناشناخته" }}"
        }
    }
}
