package com.apex.app.data.trip

/** Un punto geográfico simple (sin depender de la librería del mapa). */
data class LatLng(val lat: Double, val lon: Double)

/** Consumo promedio (L/h) en un rango de RPM, para la gráfica RPM vs consumo. */
data class RpmBand(val rpm: Int, val lPerH: Double)

/** Tipos de modo de viaje. Se guarda como texto en el JSON. */
object TripMode {
    const val CIUDAD = "ciudad"
    const val CARRETERA = "carretera"
}

/** Calidad de un tramo de ruta, para pintar el mapa. */
object RouteFlag {
    const val OK = 0          // eficiente (cian)
    const val HIGH_FUEL = 1   // consumo alto (naranja)
    const val ANOMALY = 2     // RPM alta / temp / presión (rojo)
}

/** Un punto con algo fuera de lo normal durante el viaje (pin en el mapa + fila en Excel). */
data class Anomaly(
    val lat: Double,
    val lon: Double,
    val type: String,     // "rpm_alta" | "temp_alta" | "presion_baja" | "consumo_alto"
    val label: String,    // texto para el pin/Excel, ej. "RPM 4.800 sin cambiar marcha"
    val timeMillis: Long,
    val address: String = ""  // dirección aproximada (se llena al exportar con internet)
)

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
    val rpmBands: List<RpmBand> = emptyList(),
    val mode: String = "",
    val coolantMax: Int? = null,
    val coolantMin: Int? = null,
    val oilMax: Int? = null,
    val oilMin: Int? = null,
    val fuelPressureMin: Int? = null,
    val fuelPressureAvg: Int? = null,
    val idleMs: Long = 0L,
    val stops: Int = 0,
    val hardAccel: Int = 0,
    val hardBrake: Int = 0,
    val climbM: Int = 0,
    val dropM: Int = 0,
    val avgRpm: Int = 0,
    val ecoScore: Int = 0,
    val analysis: String = "",
    val routeFlags: List<Int> = emptyList(),
    val anomalies: List<Anomaly> = emptyList()
)
