package com.openjarvis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object VoidColor {
    val Void950 = Color(0xFF070912)
    val Void900 = Color(0xFF0D1220)
    val Void800 = Color(0xFF151D30)
    val Void700 = Color(0xFF202B43)
    val Void600 = Color(0xFF34415D)
    val Violet = Color(0xFF9B7BFF)
    val VioletDim = Color(0xFF6548C8)
    val Cyan = Color(0xFF42D9E8)
    val Green = Color(0xFF52D6A0)
    val Amber = Color(0xFFFFC66D)
    val Red = Color(0xFFFF7188)
    val TextPrimary = Color(0xFFF5F6FF)
    val TextSecondary = Color(0xFFB5BED2)
    val TextDisabled = Color(0xFF7E899F)
    val BorderSubtle = Color(0x24FFFFFF)
    val BorderGlow = Color(0x669B7BFF)
}

private val JarvisDarkColors = darkColorScheme(
    primary = VoidColor.Violet,
    secondary = VoidColor.Cyan,
    tertiary = VoidColor.Green,
    background = VoidColor.Void950,
    surface = VoidColor.Void900,
    surfaceVariant = VoidColor.Void800,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = VoidColor.TextPrimary,
    onSurface = VoidColor.TextPrimary,
    onSurfaceVariant = VoidColor.TextSecondary,
    error = VoidColor.Red
)

@Composable
fun OpenJarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = JarvisDarkColors, content = content)
}
