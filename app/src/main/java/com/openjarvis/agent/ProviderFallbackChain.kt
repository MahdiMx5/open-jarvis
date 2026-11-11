package com.openjarvis.agent

import android.content.Context
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.llm.UniversalAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Sends chat requests only to the provider explicitly selected by the user.
 * No automatic routing or silent fallback to another provider is performed.
 */
class ProviderFallbackChain(private val context: Context) {

    private val graphifyRepo = GraphifyRepository(context)
    private val _notification = MutableStateFlow<String?>(null)

    suspend fun completeWithFallback(system: String, user: String): Result<String> {
        val adapter = UniversalAdapter(context)
        val activeName = adapter.getProviderName()

        if (!adapter.isProviderConfigured(activeName)) {
            return Result.failure(
                IllegalStateException("برای $activeName کلید API یا تنظیمات لازم ثبت نشده است.")
            )
        }

        val provider = adapter.getProviderForName(activeName)
            ?: return Result.failure(
                IllegalStateException("ارائه‌دهنده $activeName قابل راه‌اندازی نیست؛ تنظیمات آن را بررسی کن.")
            )

        val result = runCatching { provider.complete(system, user) }.getOrElse {
            Result.failure(it)
        }

        result.fold(
            onSuccess = {
                _notification.value = null
                runCatching {
                    graphifyRepo.logTask("provider:$activeName", "chat completed", activeName, 0L)
                }
            },
            onFailure = { error ->
                runCatching {
                    graphifyRepo.logTask(
                        "provider:$activeName",
                        "failed: ${error.message.orEmpty()}",
                        activeName,
                        0L
                    )
                }
                _notification.value = "ارتباط با $activeName ناموفق بود: ${error.message.orEmpty().take(180)}"
            }
        )

        return result.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                Result.failure(
                    IllegalStateException(
                        "خطای $activeName: ${error.message ?: "پاسخی از سرویس دریافت نشد."}",
                        error
                    )
                )
            }
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
                "محدودیت درخواست یا اعتبار سرویس اعمال شده است؛ وضعیت حساب و پرداخت را بررسی کن."
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
