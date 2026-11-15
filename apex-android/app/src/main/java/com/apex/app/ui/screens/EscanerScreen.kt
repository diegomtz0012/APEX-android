package com.apex.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.app.data.bluetooth.ConnectionState
import com.apex.app.data.obd.Dtc
import com.apex.app.ui.ScannerViewModel
import com.apex.app.ui.components.ApexCard
import com.apex.app.ui.components.SectionLabel
import com.apex.app.ui.components.StatusDot
import com.apex.app.ui.theme.ApexColors

@Composable
fun EscanerScreen(viewModel: ScannerViewModel) {
    val state by viewModel.ui.collectAsState()
    val conn by viewModel.connection.collectAsState()
    val connected = conn is ConnectionState.Connected
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = ApexColors.Surface,
            title = { Text("¿Borrar códigos?", color = ApexColors.Text) },
            text = {
                Text(
                    "Se borrarán los códigos de falla y se apagará el check engine. " +
                        "Si el problema sigue, el código volverá a aparecer.",
                    color = ApexColors.Muted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.clearCodes()
                }) { Text("Borrar", color = ApexColors.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancelar", color = ApexColors.Muted)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        SectionLabel("Escáner de fallas")
        Spacer(Modifier.height(6.dp))
        StatusDot(
            text = if (connected) "Conectado al vehículo" else "Sin conexión",
            color = if (connected) ApexColors.Green else ApexColors.Muted
        )

        Spacer(Modifier.height(18.dp))

        // Botón escanear
        Button(
            onClick = { viewModel.scan() },
            enabled = connected && !state.scanning,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ApexColors.Orange,
                contentColor = ApexColors.OnOrange,
                disabledContainerColor = ApexColors.SurfaceHi,
                disabledContentColor = ApexColors.Muted
            )
        ) {
            if (state.scanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = ApexColors.OnOrange,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Escanear códigos", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        if (!connected) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Conéctate al ELM327 en la pestaña “En vivo” para poder escanear.",
                color = ApexColors.Muted,
                fontSize = 12.sp
            )
        }

        // Mensaje
        state.message?.let { msg ->
            Spacer(Modifier.height(16.dp))
            Text(text = msg, color = ApexColors.Orange, fontSize = 13.sp)
        }

        // Resultados
        if (state.scanned && state.codes.isEmpty() && state.message == null) {
            Spacer(Modifier.height(24.dp))
            NoCodes()
        }

        if (state.codes.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SectionLabel("${state.codes.size} ${if (state.codes.size == 1) "código" else "códigos"}")
            Spacer(Modifier.height(10.dp))
            state.codes.forEach { dtc ->
                DtcCard(dtc)
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { showConfirm = true },
                enabled = connected && !state.scanning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Borrar códigos (apagar check engine)", color = ApexColors.Red)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "APEX solo LEE y BORRA códigos. Nunca reprograma ni modifica la computadora del carro.",
            color = ApexColors.Muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun NoCodes() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = ApexColors.Green,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text("Sin códigos de falla", color = ApexColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Tu carro no reportó fallas almacenadas.", color = ApexColors.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun DtcCard(dtc: Dtc) {
    ApexCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = ApexColors.Orange,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = dtc.code,
                    color = ApexColors.Orange,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(2.dp))
                Text(text = dtc.description, color = ApexColors.Text, fontSize = 13.sp)
            }
        }
    }
}
