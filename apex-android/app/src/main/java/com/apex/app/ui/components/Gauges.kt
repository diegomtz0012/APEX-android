package com.apex.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.app.ui.theme.ApexColors

/**
 * Medidor circular estilo tablero: un arco que se llena según el valor,
 * con el número grande en el centro. Si el valor es null, muestra "—".
 */
@Composable
fun CircularGauge(
    value: Int?,
    max: Int,
    label: String,
    unit: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 118.dp
) {
    val target = ((value ?: 0).toFloat() / max).coerceIn(0f, 1f)
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 300),
        label = "gauge"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 11.dp.toPx()
                val startAngle = 135f
                val sweepMax = 270f
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val topLeft = Offset(stroke / 2f, stroke / 2f)

                // Pista de fondo
                drawArc(
                    color = ApexColors.Border,
                    startAngle = startAngle,
                    sweepAngle = sweepMax,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                // Valor
                drawArc(
                    color = ApexColors.Orange,
                    startAngle = startAngle,
                    sweepAngle = sweepMax * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Text(
                    text = value?.toString() ?: "—",
                    color = ApexColors.Orange,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                androidx.compose.material3.Text(
                    text = unit,
                    color = ApexColors.Muted,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.material3.Text(
            text = label,
            color = ApexColors.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
