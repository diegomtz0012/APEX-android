package com.apex.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.apex.app.data.bluetooth.ConnectionState
import com.apex.app.ui.components.ApexCard
import com.apex.app.ui.components.CircularGauge
import com.apex.app.ui.components.SectionLabel
import com.apex.app.ui.components.StatusDot
import com.apex.app.ui.theme.ApexColors
import com.apex.app.ui.LiveViewModel

@Composable
fun EnVivoScreen(viewModel: LiveViewModel) {
    val state by viewModel.ui.collectAsState()
    val connected = state.connection is ConnectionState.Connected

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        SectionLabel("En vivo")
        Spacer(Modifier.height(6.dp))
        StatusDot(
            text = statusText(state.connection),
            color = statusColor(state.connection)
        )
        Spacer(Modifier.height(18.dp))

        if (connected) {
            // Medidores grandes
            ApexCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CircularGauge(value = state.rpm, max = 7000, label = "RPM", unit = "rpm", size = 104.dp)
                    CircularGauge(value = state.speed, max = 220, label = "Velocidad", unit = "km/h", size = 104.dp)
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularGauge(value = state.coolant, max = 120, label = "Refrigerante", unit = "°C", size = 104.dp)
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.disconnect() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ApexColors.SurfaceHi,
                    contentColor = ApexColors.Text
                )
            ) { Text("Desconectar") }
        } else {
            // Lista de adaptadores
            SectionLabel("Adaptadores emparejados")
            Spacer(Modifier.height(10.dp))

            if (state.devices.isEmpty()) {
                Text(
                    text = "No hay adaptadores. Empareja tu ELM327 en los ajustes Bluetooth del teléfono y toca “Actualizar”.",
                    color = ApexColors.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            state.devices.forEach { device ->
                Button(
                    onClick = { viewModel.connect(device.address) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ApexColors.Orange,
                        contentColor = ApexColors.OnOrange
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(device.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(device.address, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.loadDevices() },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, SolidColor(ApexColors.Border))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = ApexColors.Orange,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Actualizar lista", color = ApexColors.Orange)
            }
        }

        // Registro
        Spacer(Modifier.height(22.dp))
        SectionLabel("// registro")
        Spacer(Modifier.height(8.dp))
        ApexCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                if (state.log.isEmpty()) {
                    Text(
                        text = "Sin actividad todavía.",
                        color = ApexColors.Muted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                state.log.forEach { line ->
                    Text(
                        text = line,
                        color = ApexColors.Text,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private fun statusText(state: ConnectionState): String = when (state) {
    is ConnectionState.Disconnected -> "Desconectado"
    is ConnectionState.Connecting -> "Conectando…"
    is ConnectionState.Connected -> "Conectado a ${state.device.name}"
    is ConnectionState.Failed -> "Error: ${state.error.message}"
}

private fun statusColor(state: ConnectionState) = when (state) {
    is ConnectionState.Connected -> ApexColors.Green
    is ConnectionState.Connecting -> ApexColors.Orange
    is ConnectionState.Failed -> ApexColors.Red
    is ConnectionState.Disconnected -> ApexColors.Muted
}
