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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.app.data.bluetooth.ConnectionState
import com.apex.app.data.drag.DragResult
import com.apex.app.data.obd.Dtc
import com.apex.app.data.vehicle.VehicleProfile
import com.apex.app.data.vehicle.VehicleProfileRepository
import com.apex.app.ui.LiveUiState
import com.apex.app.ui.LiveViewModel
import com.apex.app.ui.MeasureViewModel
import com.apex.app.ui.ScannerViewModel
import com.apex.app.ui.components.ApexCard
import com.apex.app.ui.components.SectionLabel
import com.apex.app.ui.theme.ApexColors

private enum class TipLevel { GOOD, INFO, WARN }
private data class Tip(val level: TipLevel, val title: String, val text: String)

@Composable
fun AsistenteScreen(
    liveViewModel: LiveViewModel,
    measureViewModel: MeasureViewModel,
    scannerViewModel: ScannerViewModel,
    vehicleRepository: VehicleProfileRepository
) {
    val live by liveViewModel.ui.collectAsState()
    val history by measureViewModel.historyList.collectAsState()
    val scan by scannerViewModel.ui.collectAsState()
    val profile = remember { vehicleRepository.load() }

    val tips = buildTips(live, history, scan.codes, profile)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionLabel("Asistente")
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Consejos a partir de tus datos",
            color = ApexColors.Muted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(16.dp))

        tips.forEach { tip ->
            TipCard(tip)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun TipCard(tip: Tip) {
    val color = when (tip.level) {
        TipLevel.GOOD -> ApexColors.Green
        TipLevel.INFO -> ApexColors.Orange
        TipLevel.WARN -> ApexColors.Red
    }
    val icon = when (tip.level) {
        TipLevel.GOOD -> Icons.Rounded.CheckCircle
        TipLevel.INFO -> Icons.Rounded.Info
        TipLevel.WARN -> Icons.Rounded.Warning
    }
    ApexCard(modifier = Modifier.fillMaxWidth()) {
        Row {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(tip.title, color = ApexColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(tip.text, color = ApexColors.Muted, fontSize = 13.sp)
            }
        }
    }
}

/** Reglas del asistente: genera consejos a partir de los datos disponibles. */
private fun buildTips(
    live: LiveUiState,
    history: List<DragResult>,
    codes: List<Dtc>,
    profile: VehicleProfile
): List<Tip> {
    val tips = ArrayList<Tip>()
    val connected = live.connection is ConnectionState.Connected
    val running = (live.rpm ?: 0) > 400

    // Códigos de falla
    if (codes.isNotEmpty()) {
        tips.add(
            Tip(
                TipLevel.WARN,
                "${codes.size} código(s) de falla",
                "Tienes fallas activas (${codes.joinToString(", ") { it.code }}). Revísalas en el Escáner."
            )
        )
    }

    // Diagnóstico en vivo
    if (connected) {
        live.coolant?.let {
            if (it >= 105) tips.add(Tip(TipLevel.WARN, "Temperatura alta ($it°C)", "El motor está caliente. Detente y revisa refrigerante, ventilador y termostato."))
            else if (it in 80..100) tips.add(Tip(TipLevel.GOOD, "Temperatura normal ($it°C)", "El motor está en su rango ideal de operación."))
        }
        live.voltage?.let {
            if (running && it < 13.0f) tips.add(Tip(TipLevel.WARN, "Voltaje bajo (${"%.1f".format(it)}V)", "Con el motor andando debería estar sobre 13.5V. Revisa alternador y batería."))
            else if (it > 15.0f) tips.add(Tip(TipLevel.WARN, "Voltaje alto (${"%.1f".format(it)}V)", "Puede indicar sobrecarga del regulador. Revísalo."))
        }
        live.intakeTemp?.let {
            if (it >= 55) tips.add(Tip(TipLevel.INFO, "Admisión caliente ($it°C)", "El aire caliente resta potencia. Una toma de aire frío o reubicar el filtro ayudaría."))
        }
    } else {
        tips.add(Tip(TipLevel.INFO, "Conéctate para diagnóstico", "Conecta el ELM327 en “En vivo” para recibir consejos de temperatura, voltaje y más."))
    }

    // Historial de piques
    val accel = history.filter { it.test.startsWith("0-100") && it.timeMs != null }
    if (accel.size >= 2) {
        val last = accel[0].timeMs ?: 0
        val prevRun = accel[1].timeMs ?: 0
        val diff = (prevRun - last) / 1000.0
        if (diff > 0.1) tips.add(Tip(TipLevel.GOOD, "¡Mejoraste!", "Tu último 0-100 fue ${"%.2f".format(diff)}s más rápido que el anterior."))
        else if (diff < -0.1) tips.add(Tip(TipLevel.INFO, "Más lento que antes", "Tu último 0-100 fue ${"%.2f".format(-diff)}s más lento. Puede ser carga, clima o superficie."))
    }
    val best = accel.minByOrNull { it.timeMs ?: Long.MAX_VALUE }
    best?.timeMs?.let {
        tips.add(Tip(TipLevel.INFO, "Tu mejor 0-100", "Tu récord es ${"%.2f".format(it / 1000.0)}s. ¡A romperlo!"))
    }

    // Perfil del carro
    if (profile.weightKg.toIntOrNull()?.let { it <= 0 } != false) {
        tips.add(Tip(TipLevel.INFO, "Configura el peso", "Agrega el peso de tu carro en “Carro” para usar el dinamómetro virtual."))
    }

    if (tips.isEmpty()) {
        tips.add(Tip(TipLevel.GOOD, "Todo en orden", "No hay nada que reportar por ahora. Haz un pique o conéctate al carro para más consejos."))
    }
    return tips
}
