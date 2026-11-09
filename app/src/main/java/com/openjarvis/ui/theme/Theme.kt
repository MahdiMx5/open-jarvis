package com.openjarvis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object VoidColor {
    val Void950 = Color(0xFF09090F)
    val Void900 = Color(0xFF11111B)
    val Void800 = Color(0xFF1A1A27)
    val Void700 = Color(0xFF272738)
    val Void600 = Color(0xFF35354A)
    val Violet = Color(0xFF9B59B6)
    val VioletDim = Color(0xFF6F3C91)
    val Cyan = Color(0xFF45D6E8)
    val Green = Color(0xFF42C98A)
    val Amber = Color(0xFFFFC857)
    val Red = Color(0xFFFF5C70)
    val TextPrimary = Color(0xFFF4F2FA)
    val TextSecondary = Color(0xFFB8B4C8)
    val TextDisabled = Color(0xFF777386)
    val BorderSubtle = Color(0x33FFFFFF)
    val BorderGlow = Color(0x669B59B6)
}

private val JarvisDarkColors = darkColorScheme(
    primary = VoidColor.Violet,
    secondary = VoidColor.Cyan,
    background = VoidColor.Void950,
    surface = VoidColor.Void900,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = VoidColor.TextPrimary,
    onSurface = VoidColor.TextPrimary,
    error = VoidColor.Red
)

@Composable
fun OpenJarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisDarkColors,
        content = content
    )
}
