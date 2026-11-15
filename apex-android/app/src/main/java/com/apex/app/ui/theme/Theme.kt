package com.apex.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.apex.app.R

/**
 * Paleta central de APEX (estilo "performance"), alineada a la maqueta.
 * Tres acentos: naranja (rendimiento), cian (viajes/escáner), violeta (asistente).
 */
object ApexColors {
    val Orange = Color(0xFFFF6A1A)
    val Orange2 = Color(0xFFFF8C3F)
    val OrangeDim = Color(0xFF7A3D15)
    val Bg = Color(0xFF09090B)
    val Surface = Color(0xFF131418)
    val Surface2 = Color(0xFF1A1C21)
    val SurfaceHi = Color(0xFF1E2026)
    val Border = Color(0xFF2A2D34)
    val Line = Color(0xFF2A2D34)
    val LineSoft = Color(0xFF1F2228)
    val Text = Color(0xFFF3F5F8)
    val Muted = Color(0xFF8B919B)
    val Faint = Color(0xFF575D67)
    val Cyan = Color(0xFF35D0D8)
    val Green = Color(0xFF47D67F)
    val Red = Color(0xFFF2585E)
    val Violet = Color(0xFF9D8CFF)
    val OnOrange = Color(0xFF1A0D04)
    val OnCyan = Color(0xFF042227)
    // Fondo/borde del panel "asistente" (coach)
    val CoachBg = Color(0xFF17121D)
    val CoachBorder = Color(0xFF3A2F52)
    val CoachText = Color(0xFFC9BDFF)
}

/** Oxanium (variable): la fuente de los números grandes y títulos — look performance. */
@OptIn(ExperimentalTextApi::class)
val ApexDisplay = FontFamily(
    Font(R.font.oxanium, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.oxanium, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.oxanium, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.oxanium, weight = FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(800)))
)

/** JetBrains Mono: etiquetas técnicas, registros, unidades. */
val ApexMono = FontFamily(
    Font(R.font.jetbrains_mono, weight = FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, weight = FontWeight.Bold)
)

private val ApexScheme = darkColorScheme(
    primary = ApexColors.Orange,
    onPrimary = ApexColors.OnOrange,
    secondary = ApexColors.Cyan,
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
