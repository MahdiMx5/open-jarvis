package com.openjarvis.agent

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class RiskyActionConfirmation(private val context: Context) {
    
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "risky_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    
    fun shouldConfirm(action: Action): Boolean {
        return when (action.action) {
            Action.TYPE -> {
                val packageName = action.packageName
                requiresInputConfirmation(packageName)
            }
            Action.OPEN_APP -> {
                val isFinancial = action.packageName in financialPackages
                isFinancial
            }
            else -> false
        }
    }
    
    fun getRiskLevel(action: Action): RiskLevel {
        return when (action.action) {
            Action.TYPE -> {
                when {
                    action.packageName in financialPackages -> RiskLevel.HIGH
                    requiresInputConfirmation(action.packageName) -> RiskLevel.MEDIUM
                    else -> RiskLevel.LOW
                }
            }
            Action.OPEN_APP -> {
                action.packageName?.let { pkg ->
                    when {
                        pkg in financialPackages -> RiskLevel.HIGH
                        pkg in socialPackages -> RiskLevel.MEDIUM
                        else -> RiskLevel.LOW
                    }
                } ?: RiskLevel.LOW
            }
            else -> RiskLevel.LOW
        }
    }
    
    fun buildPreview(action: Action): ActionPreview {
        val risk = getRiskLevel(action)
        val summary = buildSummary(action)
        val details = buildDetails(action)
        
        return ActionPreview(
            riskLevel = risk,
            summary = summary,
            details = details,
            canUndo = false
        )
    }
    
    private fun buildSummary(action: Action): String {
        return when (action.action) {
            Action.TYPE -> "Send message: \"${action.value?.take(30)}...\""
            Action.OPEN_APP -> "Open ${action.label ?: action.packageName}"
            else -> "Execute: ${action.action}"
        }
    }
    
    private fun buildDetails(action: Action): List<String> {
        return listOf(
            "This action will interact with ${action.packageName}",
            "Sensitive input requires explicit confirmation",
            "You can cancel before the text is entered"
        )
    }
    
    fun isEnabled(): Boolean = prefs.getBoolean("risky_confirmation_enabled", true)
    fun setEnabled(enabled: Boolean) = prefs.edit().putBoolean("risky_confirmation_enabled", enabled).apply()
    
    data class ActionPreview(
        val riskLevel: RiskLevel,
        val summary: String,
        val details: List<String>,
        val canUndo: Boolean
    )
    
    enum class RiskLevel { LOW, MEDIUM, HIGH }
    
    companion object {
        fun requiresInputConfirmation(packageName: String?): Boolean {
            if (packageName.isNullOrBlank()) return false
            return packageName in messagingPackages ||
                packageName in financialPackages ||
                packageName in socialPackages
        }

        private val messagingPackages = listOf(
            "com.whatsapp",
            "com.google.android.apps.messaging",
            "com.instagram.android",
            "com.facebook.orca",
            "org.telegram.messenger",
            "com.discord"
        )
        
        private val financialPackages = listOf(
            "com.google.android.apps.wallet",
            "com.paytm",
            "com.phonepe",
            "com.razorpay"
        )
        
        private val socialPackages = listOf(
            "com.instagram.android",
            "com.twitter.android",
            "com.facebook.katana",
            "com.snapchat.android",
            "com.tiktok"
        )
    }
}