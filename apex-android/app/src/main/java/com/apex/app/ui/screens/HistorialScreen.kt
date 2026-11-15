package com.apex.app.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.app.data.drag.DragResult
import com.apex.app.ui.MeasureViewModel
import com.apex.app.ui.components.ApexCard
import com.apex.app.ui.components.ComingSoonScreen
import com.apex.app.ui.components.SectionLabel
import com.apex.app.ui.theme.ApexColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistorialScreen(viewModel: MeasureViewModel) {
    val runs by viewModel.historyList.collectAsState()

    if (runs.isEmpty()) {
        ComingSoonScreen(
            icon = Icons.Rounded.History,
            title = "Sin mediciones aún",
            description = "Cuando hagas un pique en la pestaña “Medir”, quedará guardado aquí."
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        SectionLabel("Historial")
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${runs.size} ${if (runs.size == 1) "medición" else "mediciones"}",
            color = ApexColors.Muted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(16.dp))

        runs.forEach { run ->
            RunCard(run)
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.clearHistory() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Borrar historial", color = ApexColors.Red)
        }
    }
}

@Composable
private fun RunCard(run: DragResult) {
    ApexCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = formatDate(run.dateMillis),
                color = ApexColors.Muted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Metric("0-100", formatSeconds(run.time0to100Ms), big = true)
                Metric("0-60", formatSeconds(run.time0to60Ms), big = false)
                Metric("Máx", "${run.topSpeedKmh} km/h", big = false)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, big: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = ApexColors.Orange,
            fontSize = if (big) 26.sp else 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(text = label, color = ApexColors.Muted, fontSize = 11.sp)
    }
}

private fun formatDate(millis: Long): String {
    val fmt = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault())
    return fmt.format(Date(millis))
}

private fun formatSeconds(ms: Long?): String {
    if (ms == null || ms <= 0L) return "—"
    val seconds = ms / 1000.0
    val whole = seconds.toInt()
    val hundredths = ((seconds - whole) * 100).toInt()
    val hh = if (hundredths < 10) "0$hundredths" else "$hundredths"
    return "$whole.$hh s"
}
