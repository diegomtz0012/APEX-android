package com.apex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.app.data.bluetooth.ConnectionState

@Composable
fun LiveScreen(viewModel: LiveViewModel) {
    val state by viewModel.ui.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "APEX",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Lectura real · ELM327 clásico",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 12.sp
            )

            Text(
                text = statusText(state.connection),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
            )

            val connected = state.connection is ConnectionState.Connected

            if (!connected) {
                Text(
                    text = "Adaptadores emparejados:",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 12.sp
                )
                if (state.devices.isEmpty()) {
                    Text(
                        text = "Ninguno. Empareja el ELM327 en los ajustes Bluetooth del teléfono y toca ‘Actualizar’.",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                state.devices.forEach { device ->
                    Button(
                        onClick = { viewModel.connect(device.address) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text("${device.name}  ·  ${device.address}")
                    }
                }
                OutlinedButton(
                    onClick = { viewModel.loadDevices() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Text("Actualizar lista")
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DataCell("RPM", state.rpm?.toString())
                    DataCell("KM/H", state.speed?.toString())
                    DataCell("°C", state.coolant?.toString())
                }
                Button(
                    onClick = { viewModel.disconnect() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Desconectar")
                }
            }

            Text(
                text = "// registro",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    state.log.forEach { line ->
                        Text(
                            text = line,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DataCell(unit: String, value: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value ?: "—",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = unit,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun statusText(state: ConnectionState): String = when (state) {
    is ConnectionState.Disconnected -> "● Desconectado"
    is ConnectionState.Connecting -> "● Conectando…"
    is ConnectionState.Connected -> "● Conectado a ${state.device.name}"
    is ConnectionState.Failed -> "● Error: ${state.error.message}"
}
