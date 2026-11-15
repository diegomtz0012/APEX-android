package com.apex.app.data.drag

/**
 * Resultado de una medición de aceleración (pique).
 * Los tiempos están en milisegundos; null si no se alcanzó esa marca.
 */
data class DragResult(
    val id: Long,              // marca de tiempo única (epoch millis)
    val dateMillis: Long,      // cuándo se hizo
    val time0to60Ms: Long?,    // 0-60 km/h
    val time0to100Ms: Long?,   // 0-100 km/h
    val topSpeedKmh: Int       // velocidad máxima alcanzada en la corrida
)
