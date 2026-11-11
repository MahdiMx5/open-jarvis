package com.openjarvis.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
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
                DashboardScreen(
                    onStartOverlay = { openPermissionCenter() },
                    onOpenPermissions = { openPermissionCenter() },
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    graphifyRepo = graphifyRepo
                )
            }
        }
    }

    private fun openPermissionCenter() {
        startActivity(Intent(this, PermissionCenterActivity::class.java))
    }
}
