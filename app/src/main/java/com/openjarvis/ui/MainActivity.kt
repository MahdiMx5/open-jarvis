package com.openjarvis.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.ui.dashboard.DashboardScreen
import com.openjarvis.ui.theme.OpenJarvisTheme

class MainActivity : ComponentActivity() {
    private lateinit var graphifyRepo: GraphifyRepository
    private var waitingForOverlayPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        graphifyRepo = GraphifyRepository(applicationContext)
        setContent {
            OpenJarvisTheme {
                DashboardScreen(
                    onStartOverlay = { startVoiceAssistant() },
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    graphifyRepo = graphifyRepo
                )
            }
        }
    }

    private fun startVoiceAssistant() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
            return
        }
        ensureMicrophonePermission()
    }

    private fun ensureMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_REQUEST)
            return
        }
        ensureOverlayPermission()
    }

    private fun ensureOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = true
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "برای نمایش پنجره شناور، اجازه نمایش روی برنامه‌ها را فعال کن.", Toast.LENGTH_LONG).show()
            return
        }
        startVoiceService()
    }

    override fun onResume() {
        super.onResume()
        if (waitingForOverlayPermission) {
            waitingForOverlayPermission = false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                startVoiceService()
            } else {
                Toast.makeText(this, "بدون این مجوز، پنجره شناور فعال نمی‌شود.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startVoiceService() {
        try {
            OverlayService.start(this)
            Toast.makeText(this, "جارویس فعال شد؛ برای بیدارکردن بگو سلام جارویس.", Toast.LENGTH_LONG).show()
        } catch (error: Exception) {
            Toast.makeText(this, "شروع جارویس ناموفق بود: ${error.message ?: "خطای ناشناخته"}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NOTIFICATION_PERMISSION_REQUEST -> ensureMicrophonePermission()
            AUDIO_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) ensureOverlayPermission()
                else Toast.makeText(this, "برای فرمان صوتی، اجازهٔ میکروفون لازم است.", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 1001
        private const val AUDIO_PERMISSION_REQUEST = 1002
    }
}
