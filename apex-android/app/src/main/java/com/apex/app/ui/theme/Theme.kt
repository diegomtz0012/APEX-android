package com.apex.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ApexColors = darkColorScheme(
    primary = Color(0xFFFF6A1A),
    onPrimary = Color(0xFF1A0D04),
    background = Color(0xFF09090B),
    onBackground = Color(0xFFF3F5F8),
    surface = Color(0xFF131418),
    onSurface = Color(0xFFF3F5F8)
)

@Composable
fun ApexTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ApexColors, content = content)
}
