package com.apex.app.data.trip

/** Un punto geográfico simple (sin depender de la librería del mapa). */
data class LatLng(val lat: Double, val lon: Double)

/** Consumo promedio (L/h) en un rango de RPM, para la gráfica RPM vs consumo. */
data class RpmBand(val rpm: Int, val lPerH: Double)

/**
 * Un viaje grabado: su ruta y el resumen de rendimiento.
 * fuelLiters / kmPerL son null si el carro no entregó datos de consumo.
 */
data class Trip(
    val id: Long,
    val dateMillis: Long,
    val distanceKm: Double,
    val durationMs: Long,
    val avgKmh: Double,
    val maxKmh: Int,
    val fuelLiters: Double?,
    val kmPerL: Double?,
    val route: List<LatLng>,
    val rpmBands: List<RpmBand> = emptyList()
)
