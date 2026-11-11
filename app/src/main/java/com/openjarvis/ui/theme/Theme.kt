package com.openjarvis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object VoidColor {
    val Void950 = Color(0xFF080D14)
    val Void900 = Color(0xFF101923)
    val Void800 = Color(0xFF172431)
    val Void700 = Color(0xFF223342)
    val Void600 = Color(0xFF34495A)
    val Cyan = Color(0xFF35D5E8)
    val CyanDim = Color(0xFF187F91)
    val Green = Color(0xFF52D6A0)
    val Amber = Color(0xFFFFC66D)
    val Red = Color(0xFFFF7188)
    val TextPrimary = Color(0xFFEAF2F7)
    val TextSecondary = Color(0xFFB3C1CC)
    val TextDisabled = Color(0xFF8293A5)
    val BorderSubtle = Color(0x24FFFFFF)
    val BorderGlow = Color(0x6635D5E8)
}

private val JarvisDarkColors = darkColorScheme(
    primary = VoidColor.Cyan,
    secondary = VoidColor.CyanDim,
    tertiary = VoidColor.Green,
    background = VoidColor.Void950,
    surface = VoidColor.Void900,
    surfaceVariant = VoidColor.Void800,
    onPrimary = Color(0xFF061116),
    onSecondary = Color.White,
    onBackground = VoidColor.TextPrimary,
    onSurface = VoidColor.TextPrimary,
    onSurfaceVariant = VoidColor.TextSecondary,
    error = VoidColor.Red
)

@Composable
fun OpenJarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = JarvisDarkColors, content = content)
}
