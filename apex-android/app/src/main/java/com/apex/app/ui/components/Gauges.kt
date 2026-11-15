package com.apex.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.app.ui.theme.ApexColors
import com.apex.app.ui.theme.ApexDisplay
import com.apex.app.ui.theme.ApexMono

/**
 * Medidor en tarjeta propia (estilo maqueta): arco + número grande (Oxanium)
 * + unidad + etiqueta. Pensado para ir en una fila con weight(1f) cada uno,
 * así no se enciman.
 */
@Composable
fun GaugeCard(
    value: Int?,
    max: Int,
    label: String,
    unit: String,
    accent: Color = ApexColors.Orange,
    modifier: Modifier = Modifier
) {
    val target = ((value ?: 0).toFloat() / max).coerceIn(0f, 1f)
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 350),
        label = "gauge"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ApexColors.Surface2)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 9.dp.toPx()
                val startAngle = 135f
                val sweepMax = 270f
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                drawArc(
                    color = ApexColors.LineSoft,
                    startAngle = startAngle,
                    sweepAngle = sweepMax,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent,
                    startAngle = startAngle,
                    sweepAngle = sweepMax * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value?.toString() ?: "—",
                    color = accent,
                    fontSize = 22.sp,
                    fontFamily = ApexDisplay,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = unit,
                    color = ApexColors.Muted,
                    fontSize = 9.sp,
                    fontFamily = ApexMono
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = ApexColors.Faint,
            fontSize = 9.sp,
            fontFamily = ApexMono
        )
    }
}

// Alias de compatibilidad por si alguna pantalla aún llama CircularGauge.
@Composable
fun CircularGauge(
    value: Int?,
    max: Int,
    label: String,
    unit: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 104.dp
) {
    GaugeCard(value = value, max = max, label = label, unit = unit, modifier = modifier)
}
