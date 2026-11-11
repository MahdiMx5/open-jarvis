package com.openjarvis.intelligence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppIntelligenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        val packageName = intent.data?.schemeSpecificPart ?: return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AppAnalyzer(context.applicationContext).onAppInstalled(packageName)
            } catch (error: Exception) {
                android.util.Log.e("OpenJarvisReceiver", "Unable to analyze installed app", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
