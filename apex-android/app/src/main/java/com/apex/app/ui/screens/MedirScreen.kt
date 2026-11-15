package com.apex.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.app.ui.MeasurePhase
import com.apex.app.ui.MeasureViewModel
import com.apex.app.ui.TestType
import com.apex.app.ui.components.ApexCard
import com.apex.app.ui.components.SectionLabel
import com.apex.app.ui.theme.ApexColors

@Composable
fun MedirScreen(viewModel: MeasureViewModel) {
    val state by viewModel.ui.collectAsState()
    val idle = state.phase == MeasurePhase.IDLE || state.phase == MeasurePhase.FINISHED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionLabel("Medir", modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(10.dp))

        // Selector de prueba
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            TestType.values().forEach { t ->
                TestChip(
                    label = t.label,
                    selected = state.test == t,
                    enabled = idle,
                    onClick = { viewModel.selectTest(t) }
                )
                Spacer(Modifier.height(0.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        // Cronómetro grande
        val bigTime = if (state.phase == MeasurePhase.FINISHED) state.resultMs else state.elapsedMs
        Text(
            text = formatSeconds(bigTime),
            color = ApexColors.Orange,
            fontSize = 66.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
        Text(text = "segundos", color = ApexColors.Muted, fontSize = 13.sp)

        Spacer(Modifier.height(8.dp))
        Text(
            text = "${state.currentSpeedKmh} km/h",
            color = ApexColors.Text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        // Distancia (solo ¼ de milla)
        if (state.test.isDistance && state.phase == MeasurePhase.RUNNING) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${state.distanceM.toInt()} m / ${state.test.distanceM.toInt()} m",
                color = ApexColors.Muted,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        // Resultado
        ApexCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Split(state.test.label, formatSeconds(state.resultMs))
                Split("Máx", "${state.topSpeedKmh}")
            }
            if (state.note != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.note ?: "",
                    color = ApexColors.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        val statusMsg = state.message ?: phaseText(state.phase)
        Text(
            text = statusMsg,
            color = if (state.message != null) ApexColors.Orange else ApexColors.Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        when (state.phase) {
            MeasurePhase.IDLE, MeasurePhase.FINISHED -> {
                Button(
                    onClick = { viewModel.arm() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ApexColors.Orange,
                        contentColor = ApexColors.OnOrange
                    )
                ) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null)
                    Text(
                        text = if (state.phase == MeasurePhase.FINISHED) "  Medir de nuevo" else "  Armar medición",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
            MeasurePhase.ARMED, MeasurePhase.RUNNING -> {
                OutlinedButton(
                    onClick = { viewModel.reset() },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Cancelar", color = ApexColors.Orange, fontSize = 16.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "Hazlo en un lugar seguro y legal. La precisión depende del GPS de tu teléfono.",
            color = ApexColors.Muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TestChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) ApexColors.Orange else ApexColors.Surface)
            .border(
                width = 1.dp,
                color = if (selected) ApexColors.Orange else ApexColors.Border,
                shape = RoundedCornerShape(50)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            color = if (selected) ApexColors.OnOrange else if (enabled) ApexColors.Text else ApexColors.Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun Split(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = ApexColors.Orange, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(text = label, color = ApexColors.Muted, fontSize = 11.sp)
    }
}

private fun phaseText(phase: MeasurePhase): String = when (phase) {
    MeasurePhase.IDLE -> "Elige la prueba, toca “Armar” y prepárate."
    MeasurePhase.ARMED -> "Esperando el arranque…"
    MeasurePhase.RUNNING -> "¡Midiendo!"
    MeasurePhase.FINISHED -> "¡Listo! Guardado en el historial."
}

private fun formatSeconds(ms: Long?): String {
    if (ms == null || ms <= 0L) return "—"
    val seconds = ms / 1000.0
    val whole = seconds.toInt()
    val hundredths = ((seconds - whole) * 100).toInt()
    val hh = if (hundredths < 10) "0$hundredths" else "$hundredths"
    return "$whole.$hh"
}
