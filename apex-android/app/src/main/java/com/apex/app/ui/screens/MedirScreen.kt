package com.apex.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.apex.app.ui.theme.ApexDisplay
import com.apex.app.ui.theme.ApexMono

@Composable
fun MedirScreen(viewModel: MeasureViewModel) {
    val state by viewModel.ui.collectAsState()

    // Modo automático: se arma solo al entrar y se apaga al salir.
    DisposableEffect(Unit) {
        viewModel.armAuto()
        onDispose { viewModel.stopAuto() }
    }

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
                    onClick = { viewModel.selectTest(t) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Cronómetro grande
        val running = state.phase == MeasurePhase.RUNNING
        val bigTime = if (running) state.elapsedMs else state.resultMs
        Text(
            text = formatSeconds(bigTime),
            color = ApexColors.Orange,
            fontSize = 72.sp,
            fontWeight = FontWeight.Black,
            fontFamily = ApexDisplay
        )
        Text(text = "SEGUNDOS", color = ApexColors.Faint, fontSize = 11.sp, fontFamily = ApexMono, letterSpacing = 2.sp)

        Spacer(Modifier.height(8.dp))
        Text(
            text = "${state.currentSpeedKmh} km/h",
            color = ApexColors.Text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        if (state.test.isDistance && running) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${state.distanceM.toInt()} m / ${state.test.distanceM.toInt()} m",
                color = ApexColors.Muted,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        // Resultado (en COLUMNA, para que no se encime)
        ApexCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Split(state.test.label, formatSeconds(state.resultMs))
                    Split("Máx", "${state.topSpeedKmh}")
                }
                val note = state.note
                if (note != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = note,
                        color = ApexColors.Muted,
                        fontSize = 13.sp,
                        fontFamily = ApexMono,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        val statusMsg = state.message ?: phaseText(state.phase)
        Text(
            text = statusMsg,
            color = if (running) ApexColors.Orange else ApexColors.Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { viewModel.reset() },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(
                text = if (running) "Cancelar" else "Reiniciar",
                color = ApexColors.Orange,
                fontSize = 15.sp
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "Automático: elige la prueba y arranca a fondo desde parado — mide y guarda solo. " +
                "Hazlo en un lugar seguro y legal.",
            color = ApexColors.Faint,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TestChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            color = if (selected) ApexColors.OnOrange else ApexColors.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun Split(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = ApexColors.Orange, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = ApexDisplay)
        Spacer(Modifier.height(2.dp))
        Text(text = label, color = ApexColors.Faint, fontSize = 10.sp, fontFamily = ApexMono)
    }
}

private fun phaseText(phase: MeasurePhase): String = when (phase) {
    MeasurePhase.IDLE -> "Preparando GPS…"
    MeasurePhase.ARMED -> "Listo. Acelera a fondo desde parado."
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
