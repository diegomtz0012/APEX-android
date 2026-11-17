package com.apex.app.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.apex.app.data.trip.LatLng
import com.apex.app.data.trip.Trip
import com.apex.app.ui.TripViewModel
import com.apex.app.ui.components.ApexCard
import com.apex.app.ui.components.SectionLabel
import com.apex.app.ui.share.ShareUtils
import com.apex.app.ui.theme.ApexColors
import com.apex.app.ui.theme.ApexDisplay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ViajesScreen(viewModel: TripViewModel) {
    val state by viewModel.ui.collectAsState()
    val trips by viewModel.trips.collectAsState()
    val context = LocalContext.current
    var selected by remember { mutableStateOf<Trip?>(null) }

    // Qué ruta mostrar en el mapa: la que se graba, o un viaje seleccionado.
    val sel = selected
    val route: List<LatLng> = when {
        state.recording || state.finished -> state.route
        sel != null -> sel.route
        else -> emptyList()
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // --- Mapa ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            TripMap(route = route)
        }

        // --- Panel de stats / control ---
        Column(modifier = Modifier.padding(16.dp)) {
            ApexCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Stat("Distancia", "%.2f km".format(state.distanceKm))
                    Stat("Tiempo", formatDuration(state.durationMs))
                    Stat("Vel.", "${state.curSpeedKmh}")
                }
                if (state.kmPerL != null || state.fuelLiters != null) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Stat("Combustible", state.fuelLiters?.let { "%.2f L".format(it) } ?: "—")
                        Stat("Rendimiento", state.kmPerL?.let { "%.1f km/L".format(it) } ?: "—")
                        Stat("Máx", "${state.maxKmh}")
                    }
                }
            }

            if (state.recording && state.fuelLiters == null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "💡 Para medir consumo: conéctate al OBD en “En vivo” antes de arrancar y pon la cilindrada de tu carro en “Carro”.",
                    color = ApexColors.Muted,
                    fontSize = 11.sp
                )
            }

            state.message?.let { msg ->
                Spacer(Modifier.height(10.dp))
                Text(text = msg, color = ApexColors.Cyan, fontSize = 13.sp)
            }

            Spacer(Modifier.height(14.dp))
            if (state.recording) {
                Button(
                    onClick = { viewModel.stop() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ApexColors.Red,
                        contentColor = ApexColors.Text
                    )
                ) { Text("Terminar viaje", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            } else {
                Button(
                    onClick = { selected = null; viewModel.start() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ApexColors.Cyan,
                        contentColor = ApexColors.OnCyan
                    )
                ) { Text("Iniciar viaje", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }

        // --- Viajes guardados ---
        if (!state.recording) {
            SectionLabel(
                "Viajes guardados",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                items(trips) { trip ->
                    TripCard(
                        trip = trip,
                        selected = selected?.id == trip.id,
                        onClick = { selected = if (selected?.id == trip.id) null else trip },
                        onShare = { ShareUtils.shareText(context, tripShareText(trip)) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun TripMap(route: List<LatLng>) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(15.0)
                controller.setCenter(GeoPoint(10.9685, -74.7813)) // Barranquilla por defecto
            }
        },
        update = { map ->
            map.overlays.clear()
            if (route.isNotEmpty()) {
                val line = Polyline(map)
                line.setPoints(route.map { GeoPoint(it.lat, it.lon) })
                line.outlinePaint.color = ApexColors.Cyan.toArgb()
                line.outlinePaint.strokeWidth = 12f
                map.overlays.add(line)
                val lastPoint = route.last()
                map.controller.setCenter(GeoPoint(lastPoint.lat, lastPoint.lon))
            }
            map.invalidate()
        }
    )
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = ApexColors.Cyan, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = ApexDisplay)
        Spacer(Modifier.height(2.dp))
        Text(text = label, color = ApexColors.Faint, fontSize = 10.sp, fontFamily = com.apex.app.ui.theme.ApexMono)
    }
}

@Composable
private fun TripCard(trip: Trip, selected: Boolean, onClick: () -> Unit, onShare: () -> Unit) {
    ApexCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDate(trip.dateMillis),
                    color = if (selected) ApexColors.Orange else ApexColors.Muted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "%.2f km".format(trip.distanceKm),
                        color = ApexColors.Cyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = ApexDisplay
                    )
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Compartir",
                        tint = ApexColors.Orange,
                        modifier = Modifier
                            .height(18.dp)
                            .clickable { onShare() }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Mini("Tiempo", formatDuration(trip.durationMs))
                Mini("Prom.", "%.0f km/h".format(trip.avgKmh))
                Mini("Rend.", trip.kmPerL?.let { "%.1f km/L".format(it) } ?: "—")
            }
        }
    }
}

@Composable
private fun Mini(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = ApexColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(text = label, color = ApexColors.Muted, fontSize = 10.sp)
    }
}

private fun tripShareText(trip: Trip): String {
    val rend = trip.kmPerL?.let { "\nRendimiento: %.1f km/L".format(it) } ?: ""
    return "APEX 🗺️\nViaje: %.2f km en %s".format(trip.distanceKm, formatDuration(trip.durationMs)) +
        "\nProm: %.0f km/h · Máx: %d km/h".format(trip.avgKmh, trip.maxKmh) + rend
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}

private fun formatDate(millis: Long): String {
    val fmt = SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault())
    return fmt.format(Date(millis))
}
