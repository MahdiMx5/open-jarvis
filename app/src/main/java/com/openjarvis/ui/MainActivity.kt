package com.openjarvis.ui

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.openjarvis.accessibility.JarvisAccessibilityService
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.ui.dashboard.DashboardScreen
import com.openjarvis.ui.theme.OpenJarvisTheme

class MainActivity : ComponentActivity() {

    private lateinit var graphifyRepo: GraphifyRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        graphifyRepo = GraphifyRepository(applicationContext)

        setContent {
            OpenJarvisTheme {
                val accessibilityEnabled = isAccessibilityServiceEnabled()

                if (!accessibilityEnabled) {
                    PermissionScreen(
                        accessibilityEnabled = accessibilityEnabled,
                        onEnableAccessibility = { startAccessibilitySettings() },
                    )
                } else {
                    DashboardScreen(
                        onStartOverlay = { startOverlayService() },
                        onOpenSettings = { openSettings() },
                        graphifyRepo = graphifyRepo,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val componentName = ComponentName(this, JarvisAccessibilityService::class.java)
        return enabledServices.contains(componentName.flattenToString())
    }

    private fun startAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun startOverlayService() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                AUDIO_PERMISSION_REQUEST
            )
            return
        }

        try {
            OverlayService.start(this)
            Toast.makeText(this, "جارویس داره شروع می‌شه", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            Toast.makeText(
                this,
                "جارویس شروع نشد: ${error.message ?: "خطای ناشناخته"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startOverlayService()
        } else if (requestCode == AUDIO_PERMISSION_REQUEST) {
            Toast.makeText(this, "برای استفاده از دستیار صوتی، اجازهٔ میکروفون لازم است.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    companion object {
        private const val AUDIO_PERMISSION_REQUEST = 1001
    }
}

@Composable
fun PermissionScreen(
    accessibilityEnabled: Boolean,
    onEnableAccessibility: () -> Unit,
) {
    val primaryColor = com.openjarvis.ui.theme.VoidColor.Violet
    val onSurfaceColor = com.openjarvis.ui.theme.VoidColor.TextPrimary
    val onSurfaceVariantColor = com.openjarvis.ui.theme.VoidColor.TextSecondary

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        androidx.compose.material3.Text(
            text = "اجازه دسترسی لازمه",
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            color = onSurfaceColor
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))

        androidx.compose.material3.Text(
            text = "برای اینکه جارویس بتونه با اجازهٔ خودت با برنامه‌ها کار کنه، این دسترسی‌ها لازمه.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = onSurfaceVariantColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))

        if (!accessibilityEnabled) {
            androidx.compose.material3.Button(
                onClick = onEnableAccessibility,
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Text("فعال‌کردن دسترسی‌پذیری")
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        }

        if (accessibilityEnabled) {
            androidx.compose.material3.Text(
                text = "جارویس فعاله",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = primaryColor
            )
        }
    }
    }
}
