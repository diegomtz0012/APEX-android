package com.apex.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.apex.app.data.export.TripExporter
import com.apex.app.data.trip.Anomaly
import com.apex.app.data.trip.LatLng
import com.apex.app.data.trip.RouteFlag
import com.apex.app.data.trip.RpmBand
import com.apex.app.data.trip.Trip
import com.apex.app.data.trip.TripMode
import com.apex.app.ui.TripViewModel
import com.apex.app.ui.components.ApexCard
import com.apex.app.ui.components.SectionLabel
import com.apex.app.ui.share.ShareUtils
import com.apex.app.ui.theme.ApexColors
import com.apex.app.ui.theme.ApexDisplay
import com.apex.app.ui.theme.ApexMono
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val GAL_IN_L = 3.785411784

@Composable
fun ViajesScreen(viewModel: TripViewModel) {
    val state by viewModel.ui.collectAsState()
    val trips by viewModel.trips.collectAsState()
    val context = LocalContext.current
    val price = remember { viewModel.fuelPricePerGallon() }
    val exportScope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Trip?>(null) }
    var choosing by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    // Viaje a mostrar en detalle/mapa: el recién terminado o el seleccionado.
    val finishedTrip = if (state.finished) trips.find { it.id == state.finishedTripId } else null
    val detail: Trip? = finishedTrip ?: selected
    val route: List<LatLng> = when {
        state.recording -> state.route
        detail != null -> detail.route
        else -> emptyList()
    }
    val flags = if (state.recording) emptyList() else (detail?.routeFlags ?: emptyList())
    val anomalies = if (state.recording) emptyList() else (detail?.anomalies ?: emptyList())

    Column(modifier = Modifier.fillMaxSize()) {

        Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
            TripMap(route = route, flags = flags, anomalies = anomalies)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        ) {
            // --- Control: grabando / terminar / iniciar / selector de modo ---
            item {
                Spacer(Modifier.height(14.dp))
                if (state.recording) {
                    RecordingCard(state)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.stop() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ApexColors.Red, contentColor = ApexColors.Text
                        )
                    ) { Text("Terminar viaje", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                } else if (choosing) {
                    SectionLabel("¿Cómo vas a manejar?")
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        ModeButton("🏙️ Ciudad", ApexColors.Cyan, ApexColors.OnCyan, Modifier.weight(1f)) {
                            choosing = false; selected = null; viewModel.start(TripMode.CIUDAD)
                        }
                        Spacer(Modifier.width(10.dp))
                        ModeButton("🛣️ Carretera", ApexColors.Orange, ApexColors.OnOrange, Modifier.weight(1f)) {
                            choosing = false; selected = null; viewModel.start(TripMode.CARRETERA)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Toca uno para empezar",
                        color = ApexColors.Muted, fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else {
                    Button(
                        onClick = { choosing = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ApexColors.Cyan, contentColor = ApexColors.OnCyan
                        )
                    ) { Text("Iniciar viaje", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }

                state.message?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    Text(text = msg, color = ApexColors.Cyan, fontSize = 13.sp)
                }
                if (state.recording && state.fuelLiters == null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "💡 Para medir consumo: conéctate al OBD en “En vivo” y pon la cilindrada en “Carro”.",
                        color = ApexColors.Muted, fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // --- Detalle del viaje terminado / seleccionado ---
            if (!state.recording && detail != null) {
                item {
                    TripDetail(detail, price)
                    if (detail.rpmBands.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        SectionLabel("Consumo vs RPM")
                        Spacer(Modifier.height(8.dp))
                        ApexCard(modifier = Modifier.fillMaxWidth()) { RpmConsumoChart(detail.rpmBands) }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // --- Viajes guardados ---
            if (!state.recording) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionLabel("Viajes guardados")
                        if (trips.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(ApexColors.Surface2)
                                    .border(1.dp, ApexColors.Border, RoundedCornerShape(50))
                                    .clickable(enabled = !exporting) {
                                        exporting = true
                                        exportScope.launch {
                                            try {
                                                val file = TripExporter.export(context, trips, price)
                                                ShareUtils.shareFile(
                                                    context, file,
                                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                                )
                                            } finally {
                                                exporting = false
                                            }
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    if (exporting) "Generando…" else "⬇ Excel",
                                    color = ApexColors.Green, fontSize = 11.sp, fontFamily = ApexMono
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                items(trips) { trip ->
                    TripCard(
                        trip = trip,
                        price = price,
                        selected = selected?.id == trip.id,
                        onClick = { selected = if (selected?.id == trip.id) null else trip },
                        onShare = { ShareUtils.shareText(context, tripShareText(trip, price)) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun RecordingCard(state: com.apex.app.ui.TripUiState) {
    ApexCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Distancia", "%.2f km".format(state.distanceKm), ApexColors.Cyan)
            Stat("Tiempo", formatDuration(state.durationMs), ApexColors.Cyan)
            Stat("Vel.", "${state.curSpeedKmh}", ApexColors.Cyan)
        }
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("RPM", if (state.curRpm > 0) "${state.curRpm}" else "—", ApexColors.Orange)
            Stat("Motor", state.curCoolant?.let { "$it°C" } ?: "—", ApexColors.Orange)
            Stat("Máx", "${state.maxKmh}", ApexColors.Orange)
        }
    }
}

@Composable
private fun TripDetail(trip: Trip, price: Double) {
    val kmPerGal = trip.kmPerL?.let { it * GAL_IN_L }
    val gallons = trip.fuelLiters?.let { it / GAL_IN_L }
    val cost = if (gallons != null && price > 0) gallons * price else null
    val isCity = trip.mode == TripMode.CIUDAD

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModeTag(trip.mode)
            Spacer(Modifier.width(10.dp))
            Text(formatDate(trip.dateMillis), color = ApexColors.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(12.dp))

        // Rendimiento grande
        ApexCard(modifier = Modifier.fillMaxWidth()) {
            SectionLabel("Rendimiento")
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    kmPerGal?.let { "%.0f".format(it) } ?: "—",
                    color = if (isCity) ApexColors.Cyan else ApexColors.Orange,
                    fontSize = 40.sp, fontWeight = FontWeight.Black, fontFamily = ApexDisplay
                )
                Spacer(Modifier.width(8.dp))
                Text("km/galón", color = ApexColors.Muted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            StatGrid(
                listOf(
                    "Distancia" to "%.1f km".format(trip.distanceKm),
                    "Combustible" to (gallons?.let { "%.2f gal".format(it) } ?: "—"),
                    "Costo" to (cost?.let { "$%,.0f".format(it) } ?: "—"),
                    "Tiempo" to formatDuration(trip.durationMs)
                )
            )
        }
        Spacer(Modifier.height(12.dp))

        // Datos por modo
        SectionLabel(if (isCity) "Detalle ciudad" else "Detalle carretera")
        Spacer(Modifier.height(8.dp))
        ApexCard(modifier = Modifier.fillMaxWidth()) {
            val rows = if (isCity) {
                listOf(
                    "Ralentí" to formatMinSec(trip.idleMs),
                    "Paradas" to "${trip.stops}",
                    "Vel. prom" to "%.0f km/h".format(trip.avgKmh),
                    "Vel. máx" to "${trip.maxKmh} km/h",
                    "Eco-score" to "${trip.ecoScore}/100",
                    "Acel./fren." to "${trip.hardAccel} / ${trip.hardBrake}"
                )
            } else {
                listOf(
                    "Vel. prom" to "%.0f km/h".format(trip.avgKmh),
                    "Vel. máx" to "${trip.maxKmh} km/h",
                    "Subió" to "+${trip.climbM} m",
                    "Bajó" to "-${trip.dropM} m",
                    "Eco-score" to "${trip.ecoScore}/100",
                    "RPM prom" to if (trip.avgRpm > 0) "${trip.avgRpm}" else "—"
                )
            }
            StatGrid(rows)
        }

        // Temperaturas / presión (solo si el carro las dio)
        val temps = ArrayList<Pair<String, String>>()
        if (trip.coolantMax != null) temps.add("Refrigerante" to "${trip.coolantMax}° / ${trip.coolantMin}°")
        if (trip.oilMax != null) temps.add("Aceite" to "${trip.oilMax}° / ${trip.oilMin}°")
        if (trip.fuelPressureAvg != null) temps.add("Presión comb." to "${trip.fuelPressureMin}–${trip.fuelPressureAvg} kPa")
        if (temps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionLabel("Temperaturas y presión")
            Spacer(Modifier.height(8.dp))
            ApexCard(modifier = Modifier.fillMaxWidth()) { StatGrid(temps) }
        }

        // Análisis APEX
        if (trip.analysis.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ApexColors.CoachBg)
                    .border(1.dp, ApexColors.CoachBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text("🧠  ANÁLISIS APEX", color = ApexColors.Violet, fontSize = 11.sp, fontFamily = ApexMono, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Text(trip.analysis, color = ApexColors.CoachText, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }

        // Anomalías listadas
        if (trip.anomalies.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionLabel("Zonas marcadas")
            Spacer(Modifier.height(8.dp))
            trip.anomalies.forEach { a ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(anomalyColor(a.type)))
                    Spacer(Modifier.width(8.dp))
                    Text(a.label, color = ApexColors.Text, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StatGrid(items: List<Pair<String, String>>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        items.chunked(2).forEachIndexed { i, pair ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                pair.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label.uppercase(), color = ApexColors.Faint, fontSize = 10.sp, fontFamily = ApexMono)
                        Spacer(Modifier.height(3.dp))
                        Text(value, color = ApexColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = ApexDisplay)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(56.dp).clip(RoundedCornerShape(14.dp)).background(bg).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(label, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
}

@Composable
private fun ModeTag(mode: String) {
    val (label, color) = when (mode) {
        TripMode.CIUDAD -> "🏙️ CIUDAD" to ApexColors.Cyan
        TripMode.CARRETERA -> "🛣️ CARRETERA" to ApexColors.Orange
        else -> "VIAJE" to ApexColors.Muted
    }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(label, color = color, fontSize = 10.sp, fontFamily = ApexMono, fontWeight = FontWeight.Bold) }
}

private fun anomalyColor(type: String): Color = when (type) {
    "consumo_alto" -> ApexColors.Orange
    else -> ApexColors.Red
}

@Composable
private fun TripMap(route: List<LatLng>, flags: List<Int>, anomalies: List<Anomaly>) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(15.0)
                controller.setCenter(GeoPoint(10.9685, -74.7813))
            }
        },
        update = { map ->
            map.overlays.clear()
            if (route.size >= 2) {
                var i = 0
                while (i < route.size - 1) {
                    val c = flagAt(flags, i + 1)
                    val pts = ArrayList<GeoPoint>()
                    pts.add(GeoPoint(route[i].lat, route[i].lon))
                    var j = i
                    while (j < route.size - 1 && flagAt(flags, j + 1) == c) {
                        pts.add(GeoPoint(route[j + 1].lat, route[j + 1].lon)); j++
                    }
                    val line = Polyline(map)
                    line.setPoints(pts)
                    line.outlinePaint.color = routeColor(c).toArgb()
                    line.outlinePaint.strokeWidth = 12f
                    map.overlays.add(line)
                    i = j
                }
                anomalies.forEach { a ->
                    val m = Marker(map)
                    m.position = GeoPoint(a.lat, a.lon)
                    m.title = a.label
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    map.overlays.add(m)
                }
                val lastPoint = route.last()
                map.controller.setCenter(GeoPoint(lastPoint.lat, lastPoint.lon))
            }
            map.invalidate()
        }
    )
}

private fun flagAt(flags: List<Int>, idx: Int): Int = if (idx in flags.indices) flags[idx] else RouteFlag.OK

private fun routeColor(flag: Int): Color = when (flag) {
    RouteFlag.HIGH_FUEL -> ApexColors.Orange
    RouteFlag.ANOMALY -> ApexColors.Red
    else -> ApexColors.Cyan
}

@Composable
private fun RpmConsumoChart(bands: List<RpmBand>) {
    val maxLh = (bands.maxOfOrNull { it.lPerH } ?: 1.0).coerceAtLeast(0.1)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(130.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            bands.forEach { b ->
                val h = ((b.lPerH / maxLh) * 108.0).toInt().coerceAtLeast(4)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text("%.1f".format(b.lPerH), color = ApexColors.Cyan, fontSize = 8.sp, fontFamily = ApexMono)
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().height(h.dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(ApexColors.Cyan)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("%.1fk".format(b.rpm / 1000.0), color = ApexColors.Faint, fontSize = 8.sp, fontFamily = ApexMono)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Litros/hora promedio por rango de RPM", color = ApexColors.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = ApexDisplay)
        Spacer(Modifier.height(2.dp))
        Text(label, color = ApexColors.Faint, fontSize = 10.sp, fontFamily = ApexMono)
    }
}

@Composable
private fun TripCard(trip: Trip, price: Double, selected: Boolean, onClick: () -> Unit, onShare: () -> Unit) {
    ApexCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeTag(trip.mode)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatDate(trip.dateMillis),
                        color = if (selected) ApexColors.Orange else ApexColors.Muted,
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "%.1f km".format(trip.distanceKm),
                        color = ApexColors.Cyan, fontSize = 15.sp, fontWeight = FontWeight.Black, fontFamily = ApexDisplay
                    )
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Rounded.Share, contentDescription = "Compartir",
                        tint = ApexColors.Orange, modifier = Modifier.height(18.dp).clickable { onShare() }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Mini("Tiempo", formatDuration(trip.durationMs))
                Mini("Prom.", "%.0f km/h".format(trip.avgKmh))
                Mini("Rend.", trip.kmPerL?.let { "%.0f km/gal".format(it * GAL_IN_L) } ?: "—")
                Mini("Eco", if (trip.ecoScore > 0) "${trip.ecoScore}" else "—")
            }
        }
    }
}

@Composable
private fun Mini(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = ApexColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = ApexColors.Muted, fontSize = 10.sp)
    }
}

private fun tripShareText(trip: Trip, price: Double): String {
    val modo = when (trip.mode) {
        TripMode.CIUDAD -> "Ciudad"; TripMode.CARRETERA -> "Carretera"; else -> "Viaje"
    }
    val rend = trip.kmPerL?.let { "\nRendimiento: %.0f km/galón".format(it * GAL_IN_L) } ?: ""
    val gallons = trip.fuelLiters?.let { it / GAL_IN_L }
    val cost = if (gallons != null && price > 0) "\nCosto: $%,.0f".format(gallons * price) else ""
    return "APEX 🗺️ ($modo)\nViaje: %.1f km en %s".format(trip.distanceKm, formatDuration(trip.durationMs)) +
        "\nProm: %.0f km/h · Máx: %d km/h".format(trip.avgKmh, trip.maxKmh) + rend + cost +
        "\nEco-score: ${trip.ecoScore}/100"
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatMinSec(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d min".format(total / 60, total % 60)
}

private fun formatDate(millis: Long): String {
    val fmt = SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault())
    return fmt.format(Date(millis))
}
