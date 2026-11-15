package com.apex.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.app.ui.MeasurePhase
import com.apex.app.ui.MeasureViewModel
import com.apex.app.ui.components.ApexCard
import com.apex.app.ui.components.SectionLabel
import com.apex.app.ui.theme.ApexColors

@Composable
fun MedirScreen(viewModel: MeasureViewModel) {
    val state by viewModel.ui.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionLabel("Pique 0-100", modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Medición con GPS",
            color = ApexColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(Modifier.height(24.dp))

        // Cronómetro grande
        val bigTime = when (state.phase) {
            MeasurePhase.FINISHED -> state.t100Ms
            else -> state.elapsedMs
        }
        Text(
            text = formatSeconds(bigTime),
            color = ApexColors.Orange,
            fontSize = 68.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "segundos",
            color = ApexColors.Muted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(10.dp))

        // Velocidad actual
        Text(
            text = "${state.currentSpeedKmh} km/h",
            color = ApexColors.Text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(22.dp))

        // Marcas / resultados
        ApexCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Split("0-60 km/h", formatSeconds(state.t60Ms))
                Split("0-100 km/h", formatSeconds(state.t100Ms))
                Split("Máx", "${state.topSpeedKmh}")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Mensaje de estado
        val statusMsg = state.message ?: phaseText(state.phase)
        Text(
            text = statusMsg,
            color = if (state.message != null) ApexColors.Orange else ApexColors.Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(18.dp))

        // Controles
        when (state.phase) {
            MeasurePhase.IDLE, MeasurePhase.FINISHED -> {
                Button(
                    onClick = { viewModel.arm() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Cancelar", color = ApexColors.Orange, fontSize = 16.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Consejo: haz la prueba en un lugar seguro y legal. La precisión depende del GPS de tu teléfono.",
            color = ApexColors.Muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
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
    MeasurePhase.IDLE -> "Toca “Armar” y prepárate para arrancar desde parado."
    MeasurePhase.ARMED -> "Esperando el arranque… acelera a fondo."
    MeasurePhase.RUNNING -> "¡Midiendo!"
    MeasurePhase.FINISHED -> "¡Listo! Guardado en el historial."
}

/** Convierte milisegundos a un texto "7.82" (segundos con 2 decimales). */
private fun formatSeconds(ms: Long?): String {
    if (ms == null || ms <= 0L) return "—"
    val seconds = ms / 1000.0
    val whole = seconds.toInt()
    val hundredths = ((seconds - whole) * 100).toInt()
    val hh = if (hundredths < 10) "0$hundredths" else "$hundredths"
    return "$whole.$hh"
}
