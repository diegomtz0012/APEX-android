package com.apex.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.app.ui.DynoPhase
import com.apex.app.ui.DynoViewModel
import com.apex.app.ui.components.ApexCard
import com.apex.app.ui.components.SectionLabel
import com.apex.app.ui.theme.ApexColors
import com.apex.app.ui.theme.ApexDisplay

@Composable
fun DynoScreen(viewModel: DynoViewModel) {
    val state by viewModel.ui.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionLabel("Dinamómetro virtual")
        Spacer(Modifier.height(4.dp))
        Text("Potencia y torque estimados por GPS", color = ApexColors.Muted, fontSize = 12.sp)

        Spacer(Modifier.height(20.dp))

        // Potencia pico
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (state.phase == DynoPhase.RECORDING) "${state.curHp}" else "${state.peakHp}",
                color = ApexColors.Orange,
                fontSize = 76.sp,
                fontWeight = FontWeight.Black,
                fontFamily = ApexDisplay
            )
            Text(
                text = if (state.phase == DynoPhase.RECORDING) "HP ahora" else "HP máx estimados",
                color = ApexColors.Muted,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(18.dp))

        ApexCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Metric("HP máx", "${state.peakHp}", "@ ${state.peakHpAtKmh} km/h")
                Metric(
                    "Torque máx",
                    state.peakTorqueNm?.let { "$it" } ?: "—",
                    state.peakTorqueAtRpm?.let { "@ $it rpm" } ?: "requiere OBD"
                )
            }
        }

        // Curva de potencia
        if (state.curve.size >= 2) {
            Spacer(Modifier.height(18.dp))
            SectionLabel("Curva de potencia (HP vs km/h)")
            Spacer(Modifier.height(10.dp))
            ApexCard(modifier = Modifier.fillMaxWidth()) {
                PowerCurve(
                    points = state.curve.map { it.speedKmh to it.hp }
                )
            }
        }

        state.message?.let { msg ->
            Spacer(Modifier.height(14.dp))
            Text(text = msg, color = ApexColors.Orange, fontSize = 13.sp)
        }

        Spacer(Modifier.height(18.dp))

        when (state.phase) {
            DynoPhase.RECORDING -> {
                Button(
                    onClick = { viewModel.stop() },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ApexColors.Red,
                        contentColor = ApexColors.Text
                    )
                ) { Text("Terminar medición", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
            else -> {
                Button(
                    onClick = { viewModel.start() },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ApexColors.Orange,
                        contentColor = ApexColors.OnOrange
                    )
                ) {
                    Text(
                        if (state.phase == DynoPhase.FINISHED) "Medir de nuevo" else "Iniciar medición",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "Son estimaciones (usan valores típicos de aerodinámica y peso). Sirven para comparar " +
                "antes/después de un cambio, no como cifra de banco. Conéctate al OBD para estimar el torque.",
            color = ApexColors.Muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Metric(label: String, value: String, sub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = ApexColors.Orange, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = ApexColors.Text, fontSize = 12.sp)
        Text(text = sub, color = ApexColors.Muted, fontSize = 10.sp)
    }
}

@Composable
private fun PowerCurve(points: List<Pair<Int, Int>>) {
    val maxHp = (points.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    val minS = points.first().first
    val maxS = points.last().first
    val rangeS = (maxS - minS).coerceAtLeast(1)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val path = Path()
        points.forEachIndexed { i, pt ->
            val x = (pt.first - minS).toFloat() / rangeS * size.width
            val y = size.height - (pt.second.toFloat() / maxHp) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = ApexColors.Orange,
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
    }
}
