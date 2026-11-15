package com.apex.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.app.data.drag.DragResult
import com.apex.app.ui.MeasureViewModel
import com.apex.app.ui.components.ApexCard
import com.apex.app.ui.components.ComingSoonScreen
import com.apex.app.ui.components.SectionLabel
import com.apex.app.ui.share.ShareUtils
import com.apex.app.ui.theme.ApexColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistorialScreen(viewModel: MeasureViewModel) {
    val runs by viewModel.historyList.collectAsState()
    val context = LocalContext.current

    if (runs.isEmpty()) {
        ComingSoonScreen(
            icon = Icons.Rounded.History,
            title = "Sin mediciones aún",
            description = "Cuando hagas una prueba en “Medir”, quedará guardada aquí."
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
        Text(
            text = "${runs.size} ${if (runs.size == 1) "medición" else "mediciones"}",
            color = ApexColors.Muted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(16.dp))

        runs.forEach { run ->
            RunCard(run, onShare = { ShareUtils.shareText(context, shareText(run)) })
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { ShareUtils.shareCsv(context, "apex_historial.csv", buildCsv(runs)) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Exportar a CSV", color = ApexColors.Orange)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { viewModel.clearHistory() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Borrar historial", color = ApexColors.Red)
        }
    }
}

@Composable
private fun RunCard(run: DragResult, onShare: () -> Unit) {
    ApexCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = run.test,
                        color = ApexColors.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatDate(run.dateMillis),
                        color = ApexColors.Muted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = formatSeconds(run.timeMs),
                    color = ApexColors.Orange,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (run.note ?: "") + "  ·  máx ${run.topSpeedKmh} km/h",
                    color = ApexColors.Muted,
                    fontSize = 12.sp
                )
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = "Compartir",
                    tint = ApexColors.Orange,
                    modifier = Modifier
                        .height(20.dp)
                        .clickable { onShare() }
                )
            }
        }
    }
}

private fun shareText(run: DragResult): String {
    val note = if (run.note != null) "\n${run.note}" else ""
    return "APEX 🏁\n${run.test}: ${formatSeconds(run.timeMs)} s\nVelocidad máx: ${run.topSpeedKmh} km/h$note"
}

private fun buildCsv(runs: List<DragResult>): String {
    val sb = StringBuilder()
    sb.append("fecha,prueba,tiempo_s,vel_max_kmh,nota\n")
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    runs.forEach { r ->
        val time = r.timeMs?.let { "%.2f".format(it / 1000.0) } ?: ""
        val note = (r.note ?: "").replace(",", " ")
        sb.append("${fmt.format(Date(r.dateMillis))},${r.test},$time,${r.topSpeedKmh},$note\n")
    }
    return sb.toString()
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
    return "$whole.$hh"
}
