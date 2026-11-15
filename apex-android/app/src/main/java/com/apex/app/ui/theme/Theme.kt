package com.apex.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Paleta central de APEX (estilo "performance": naranja sobre negro).
 * Se usa directamente en los componentes para tener control total del look.
 */
object ApexColors {
    val Orange = Color(0xFFFF6A1A)
    val OrangeDim = Color(0xFF7A3D15)
    val Bg = Color(0xFF09090B)
    val Surface = Color(0xFF15161A)
    val SurfaceHi = Color(0xFF1E2026)
    val Border = Color(0xFF2A2D35)
    val Text = Color(0xFFF3F5F8)
    val Muted = Color(0xFF8A8F98)
    val Green = Color(0xFF2ECC71)
    val Red = Color(0xFFE5484D)
    val OnOrange = Color(0xFF1A0D04)
}

private val ApexScheme = darkColorScheme(
    primary = ApexColors.Orange,
    onPrimary = ApexColors.OnOrange,
    secondary = ApexColors.Orange,
    background = ApexColors.Bg,
    onBackground = ApexColors.Text,
    surface = ApexColors.Surface,
    onSurface = ApexColors.Text,
    surfaceVariant = ApexColors.SurfaceHi,
    onSurfaceVariant = ApexColors.Muted,
    error = ApexColors.Red,
    outline = ApexColors.Border
)

@Composable
fun ApexTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ApexScheme, content = content)
}
