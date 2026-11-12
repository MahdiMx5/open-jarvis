package com.openjarvis.agent

import android.content.Context
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.llm.UniversalAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tries the selected provider first, then configured free-tier compatible providers.
 * Provider credentials are never created, guessed, or shared by this class.
 * Failed providers receive a persisted cooldown to avoid wasting quota on every turn.
 */
class ProviderFallbackChain(private val context: Context) {

    private val graphifyRepo = GraphifyRepository(context)
    private val _notification = MutableStateFlow<String?>(null)
    private val health = context.getSharedPreferences(HEALTH_PREFS, Context.MODE_PRIVATE)

    suspend fun completeWithFallback(system: String, user: String): Result<String> {
        val adapter = UniversalAdapter(context)
        val activeName = adapter.getProviderName()
        val freeProviders = UniversalAdapter.FREE_FALLBACK_PROVIDERS
        val candidates = (
            (if (activeName in freeProviders) listOf(activeName) else emptyList()) +
                freeProviders.filter { it != activeName }
            ).filter { adapter.isProviderConfigured(it) }

        if (candidates.isEmpty()) {
            val message = "هیچ سرویس آنلاینی تنظیم نشده است. از تنظیمات، یک ارائه‌دهنده سازگار را انتخاب و کلید رسمی آن را وارد کن. ساخت حساب و احراز هویت باید طبق شرایط خود سرویس انجام شود."
            _notification.value = message
            return Result.failure(IllegalStateException(message))
        }

        val now = System.currentTimeMillis()
        val ready = candidates.filter { (health.getLong(key(it, "retry_at"), 0L)) <= now }
        if (ready.isEmpty()) {
            val nextAt = candidates.minOf { health.getLong(key(it, "retry_at"), now) }
            val waitMinutes = ((nextAt - now).coerceAtLeast(0L) / 60_000L).coerceAtLeast(1L)
            val message = "سرویس‌های آنلاین موقتاً در محدودیت یا خطا هستند. MX5 دوباره پس از حدود $waitMinutes دقیقه و در درخواست بعدی امتحان می‌کند. برای افزودن سرویس دیگر، تنظیمات ارائه‌دهنده را باز کن."
            _notification.value = message
            return Result.failure(IllegalStateException(message))
        }

        val failures = mutableListOf<String>()
        for (name in ready) {
            val provider = adapter.getProviderForName(name)
            if (provider == null) {
                failures += "$name: provider unavailable"
                markFailure(name, IllegalStateException("Provider unavailable"))
                continue
            }

            val result = runCatching { provider.complete(system, user) }
                .getOrElse { Result.failure(it) }

            result.fold(
                onSuccess = { answer ->
                    if (answer.isNotBlank()) {
                        clearFailure(name)
                        _notification.value = if (name == activeName) null
                            else "سرویس اصلی در دسترس نبود؛ پاسخ از $name دریافت شد."
                        runCatching {
                            graphifyRepo.logTask("provider:$name", "chat completed", name, 0L)
                        }
                        return Result.success(answer)
                    }
                    val error = IllegalStateException("Empty response")
                    markFailure(name, error)
                    failures += "$name: پاسخ خالی"
                },
                onFailure = { error ->
                    markFailure(name, error)
                    failures += "$name: ${error.message.orEmpty().take(100)}"
                    runCatching {
                        graphifyRepo.logTask(
                            "provider:$name",
                            "failed: ${error.message.orEmpty().take(180)}",
                            name,
                            0L
                        )
                    }
                }
            )
        }

        val message = failures.joinToString(" | ").take(650)
        val needsSetup = candidates.size < UniversalAdapter.FREE_FALLBACK_PROVIDERS.size
        val guidance = if (needsSetup) {
            " برای داشتن جایگزین‌های بیشتر، از تنظیمات یکی از سرویس‌های رایگانِ قابل‌استفاده را انتخاب کن؛ اگر حساب لازم بود، فقط از مسیر رسمی و در صورت واجد شرایط بودن اقدام کن."
        } else ""
        val finalMessage = "سرویس‌های آماده پاسخ ندادند. برنامه در درخواست بعدی دوباره سرویس‌های خارج از زمان انتظار را امتحان می‌کند. $message$guidance"
        _notification.value = finalMessage
        return Result.failure(IllegalStateException(finalMessage))
    }

    private fun markFailure(name: String, error: Throwable) {
        val text = generateSequence(error) { it.cause }.joinToString(" ") { it.message.orEmpty() }
        val code = Regex("HTTP\\s+(\\d{3})", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val countKey = key(name, "failures")
        val count = (health.getInt(countKey, 0) + 1).coerceAtMost(10)
        val delayMs = when (code) {
            401, 403 -> 24L * 60 * 60 * 1000 // Credentials/permissions require user action.
            404 -> 6L * 60 * 60 * 1000 // Endpoint/model may have been retired.
            429 -> 15L * 60 * 1000 // Quota/rate-limit cooldown; no repeated quota-consuming probes.
            else -> if (code != null && code in 500..599) {
                (2L * 60 * 1000 * (1L shl (count - 1).coerceAtMost(7))).coerceAtMost(4L * 60 * 60 * 1000)
            } else {
                (60L * 1000 * (1L shl (count - 1).coerceAtMost(7))).coerceAtMost(2L * 60 * 60 * 1000)
            }
        }
        health.edit()
            .putInt(countKey, count)
            .putLong(key(name, "retry_at"), System.currentTimeMillis() + delayMs)
            .apply()
    }

    private fun clearFailure(name: String) {
        health.edit()
            .remove(key(name, "failures"))
            .remove(key(name, "retry_at"))
            .apply()
    }

    private fun key(name: String, suffix: String): String =
        "${name.lowercase().replace(Regex("[^a-z0-9]+"), "_")}_$suffix"

    fun getNotification(): StateFlow<String?> = _notification

    companion object {
        private const val HEALTH_PREFS = "provider_health"
    }
}

object JarvisErrorTranslator {

    fun translate(error: Throwable, providerName: String?): String {
        val root = generateSequence(error) { it.cause }.last()
        val message = root.message.orEmpty()
        return when {
            message.contains("401") || message.contains("403") ->
                "کلید یا مجوز دسترسی ${providerName ?: "سرویس"} معتبر نیست؛ در صورت نیاز از مسیر رسمی حساب را اصلاح کن."
            message.contains("429") ->
                "محدودیت درخواست یا سهمیه سرویس پر شده است؛ MX5 مدتی صبر می‌کند و دوباره امتحان می‌کند."
            message.contains("404") ->
                "مدل یا نشانی API پیدا نشد؛ ممکن است مدل حذف شده باشد."
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
