package com.apex.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.app.data.bluetooth.ConnectionState
import com.apex.app.ui.LiveViewModel
import com.apex.app.ui.components.ApexCard
import com.apex.app.ui.components.SectionLabel
import com.apex.app.ui.components.StatusDot
import com.apex.app.ui.theme.ApexColors

@Composable
fun InicioScreen(viewModel: LiveViewModel, onGoLive: () -> Unit) {
    val state by viewModel.ui.collectAsState()
    val connected = state.connection is ConnectionState.Connected

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = 24.dp)
    ) {
        Text(
            text = "APEX",
            color = ApexColors.Orange,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Text(
            text = "Rendimiento real · ELM327",
            color = ApexColors.Muted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(22.dp))

        // Tarjeta de estado
        ApexCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionLabel("Estado")
                Spacer(Modifier.height(10.dp))
                StatusDot(
                    text = if (connected) {
                        val c = state.connection as ConnectionState.Connected
                        "Conectado a ${c.device.name}"
                    } else "Sin conexión",
                    color = if (connected) ApexColors.Green else ApexColors.Muted
                )

                if (connected) {
                    Spacer(Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        MiniStat("RPM", state.rpm?.toString() ?: "—", Modifier.weight(1f))
                        MiniStat("KM/H", state.speed?.toString() ?: "—", Modifier.weight(1f))
                        MiniStat("°C", state.coolant?.toString() ?: "—", Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onGoLive,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ApexColors.Orange,
                contentColor = ApexColors.OnOrange
            )
        ) {
            Icon(imageVector = Icons.Rounded.Speed, contentDescription = null)
            Spacer(Modifier.height(0.dp))
            Text(
                text = if (connected) "  Ver datos en vivo" else "  Conectar y medir",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("Tu herramienta")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "APEX lee los datos de rendimiento directamente de la computadora de tu carro. " +
                "Pronto: medición de pique 0-100, historial de sesiones y escáner de fallas.",
            color = ApexColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = value, color = ApexColors.Orange, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = ApexColors.Muted, fontSize = 11.sp)
    }
}
