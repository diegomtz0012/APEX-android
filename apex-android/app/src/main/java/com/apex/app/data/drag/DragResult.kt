package com.apex.app.data.drag

/**
 * Resultado de una medición (pique / prueba).
 * Generalizado para soportar 0-100, 0-200, ¼ milla, 60-100, 80-120, etc.
 */
data class DragResult(
    val id: Long,
    val dateMillis: Long,
    val test: String,          // nombre de la prueba, ej "0-100 km/h"
    val timeMs: Long?,         // tiempo principal del resultado
    val topSpeedKmh: Int,      // velocidad máxima alcanzada
    val note: String?          // detalle extra, ej "0-60: 4.52 s" o "trap: 118 km/h"
)
