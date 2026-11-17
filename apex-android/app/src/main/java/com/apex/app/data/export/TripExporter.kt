package com.apex.app.data.export

import android.content.Context
import android.location.Geocoder
import com.apex.app.data.trip.Anomaly
import com.apex.app.data.trip.RouteFlag
import com.apex.app.data.trip.Trip
import com.apex.app.data.trip.TripMode
import com.apex.app.data.trip.TripRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exporta los viajes a un archivo .xlsx con tres hojas: Resumen, Zonas/anomalías
 * (con dirección aproximada por geocodificación inversa) y Recorrido.
 */
object TripExporter {

    private const val GAL_IN_L = 3.785411784

    suspend fun export(context: Context, trips: List<Trip>, price: Double): File =
        withContext(Dispatchers.IO) {
            val withAddresses = fillAddresses(context, trips)
            val sheets = listOf(
                resumenSheet(withAddresses, price),
                zonasSheet(withAddresses),
                recorridoSheet(withAddresses)
            )
            val bytes = XlsxWriter.build(sheets)
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val file = File(dir, "APEX_viajes_$stamp.xlsx")
            file.writeBytes(bytes)
            file
        }

    /** Geocodifica las anomalías sin dirección y persiste el resultado. */
    private fun fillAddresses(context: Context, trips: List<Trip>): List<Trip> {
        if (!Geocoder.isPresent()) return trips
        val geocoder = Geocoder(context, Locale.getDefault())
        val repo = TripRepository(context)
        var changed = false
        val result = trips.map { trip ->
            if (trip.anomalies.none { it.address.isBlank() }) return@map trip
            val updated = trip.anomalies.map { a ->
                if (a.address.isNotBlank()) a
                else a.copy(address = addressOf(geocoder, a.lat, a.lon))
            }
            changed = true
            val newTrip = trip.copy(anomalies = updated)
            repo.update(newTrip)
            newTrip
        }
        return if (changed) result else trips
    }

    private fun addressOf(geocoder: Geocoder, lat: Double, lon: Double): String = try {
        @Suppress("DEPRECATION")
        val list = geocoder.getFromLocation(lat, lon, 1)
        val a = list?.firstOrNull()
        when {
            a == null -> ""
            !a.thoroughfare.isNullOrBlank() ->
                listOfNotNull(a.thoroughfare, a.subThoroughfare, a.locality ?: a.subAdminArea)
                    .joinToString(" ")
            else -> a.getAddressLine(0) ?: ""
        }
    } catch (e: Exception) {
        ""
    }

    private fun resumenSheet(trips: List<Trip>, price: Double): Sheet {
        val rows = ArrayList<List<Cell>>()
        rows.add(
            listOf(
                "Fecha", "Modo", "Distancia (km)", "Combustible (gal)", "km/galon",
                "Costo", "Duracion (min)", "Vel prom (km/h)", "Vel max (km/h)",
                "Refrigerante max (C)", "Eco-score"
            ).map { Cell.Str(it) }
        )
        trips.forEach { t ->
            val gallons = t.fuelLiters?.let { it / GAL_IN_L }
            val kmGal = t.kmPerL?.let { it * GAL_IN_L }
            val cost = if (gallons != null && price > 0) gallons * price else null
            rows.add(
                listOf(
                    Cell.Str(dateStr(t.dateMillis)),
                    Cell.Str(modeLabel(t.mode)),
                    Cell.Num(round1(t.distanceKm)),
                    gallons?.let { Cell.Num(round2(it)) } ?: Cell.Str("—"),
                    kmGal?.let { Cell.Num(round1(it)) } ?: Cell.Str("—"),
                    cost?.let { Cell.Num(round0(it)) } ?: Cell.Str("—"),
                    Cell.Num(round0(t.durationMs / 60000.0)),
                    Cell.Num(round0(t.avgKmh)),
                    Cell.Num(t.maxKmh.toDouble()),
                    t.coolantMax?.let { Cell.Num(it.toDouble()) } ?: Cell.Str("—"),
                    Cell.Num(t.ecoScore.toDouble())
                )
            )
        }
        return Sheet("Resumen", rows)
    }

    private fun zonasSheet(trips: List<Trip>): Sheet {
        val rows = ArrayList<List<Cell>>()
        rows.add(
            listOf("Fecha viaje", "Tipo", "Detalle", "Direccion", "Lat", "Lon", "Hora")
                .map { Cell.Str(it) }
        )
        trips.forEach { t ->
            t.anomalies.forEach { a ->
                rows.add(
                    listOf(
                        Cell.Str(dateStr(t.dateMillis)),
                        Cell.Str(anomalyType(a.type)),
                        Cell.Str(a.label),
                        Cell.Str(a.address),
                        Cell.Num(a.lat),
                        Cell.Num(a.lon),
                        Cell.Str(timeStr(a.timeMillis))
                    )
                )
            }
        }
        if (rows.size == 1) rows.add(listOf(Cell.Str("Sin anomalias registradas")))
        return Sheet("Zonas", rows)
    }

    private fun recorridoSheet(trips: List<Trip>): Sheet {
        val rows = ArrayList<List<Cell>>()
        rows.add(listOf("Fecha viaje", "Lat", "Lon", "Estado tramo").map { Cell.Str(it) })
        trips.forEach { t ->
            t.route.forEachIndexed { i, p ->
                val flag = t.routeFlags.getOrElse(i) { RouteFlag.OK }
                rows.add(
                    listOf(
                        Cell.Str(dateStr(t.dateMillis)),
                        Cell.Num(p.lat),
                        Cell.Num(p.lon),
                        Cell.Str(flagLabel(flag))
                    )
                )
            }
        }
        return Sheet("Recorrido", rows)
    }

    private fun modeLabel(mode: String) = when (mode) {
        TripMode.CIUDAD -> "Ciudad"; TripMode.CARRETERA -> "Carretera"; else -> "Viaje"
    }

    private fun anomalyType(type: String) = when (type) {
        "rpm_alta" -> "RPM alta"
        "temp_alta" -> "Temperatura alta"
        "presion_baja" -> "Presion combustible baja"
        "consumo_alto" -> "Consumo alto"
        else -> type
    }

    private fun flagLabel(flag: Int) = when (flag) {
        RouteFlag.HIGH_FUEL -> "Consumo alto"
        RouteFlag.ANOMALY -> "Anomalia"
        else -> "OK"
    }

    private fun dateStr(ms: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
    private fun timeStr(ms: Long) = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))
    private fun round0(v: Double) = Math.round(v).toDouble()
    private fun round1(v: Double) = Math.round(v * 10) / 10.0
    private fun round2(v: Double) = Math.round(v * 100) / 100.0
}
