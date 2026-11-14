package com.apex.app.data.obd

import com.apex.app.core.AppError
import com.apex.app.core.Result

/**
 * Clase base abstracta para todo comando OBD-II.
 *
 * Encapsula lo COMÚN: construir el comando, limpiar la respuesta,
 * detectar errores (NO DATA, ERROR, ?) y extraer los bytes de datos.
 * Cada PID concreto solo implementa su fórmula en decode().
 * (Polimorfismo + principio Abierto/Cerrado.)
 */
abstract class ObdCommand<out T>(
    private val mode: String,
    private val pid: String,
    val name: String,
    val unit: String
) {
    /** Comando completo que se envía (ej. "010C"). */
    val command: String get() = mode + pid

    /** Procesa la respuesta cruda y devuelve el valor decodificado o un error. */
    fun parse(raw: String): Result<T> {
        val upper = raw.uppercase()
        return when {
            upper.contains("NO DATA") || upper.contains("NODATA") ->
                Result.Failure(AppError.NoData)
            // El adaptador no logró enlazar con la ECU del vehículo.
            upper.contains("UNABLE TO CONNECT") ||
                upper.contains("BUS INIT") ||
                upper.contains("BUSINIT") ||
                upper.contains("CAN ERROR") ||
                upper.contains("STOPPED") ->
                Result.Failure(AppError.VehicleUnreachable)
            upper.contains("ERROR") ->
                Result.Failure(AppError.AdapterError)
            upper.contains("?") ->
                Result.Failure(AppError.AdapterError)
            else -> extract(upper, raw)
        }
    }

    private fun extract(upper: String, raw: String): Result<T> {
        // Dejar solo caracteres hexadecimales.
        val clean = upper.replace(Regex("[^0-9A-F]"), "")
        // El modo de respuesta es 0x40 + modo de petición (01 -> 41).
        val requestMode = mode.toIntOrNull(16)
            ?: return Result.Failure(AppError.CorruptResponse(raw))
        val responseMode = (requestMode + 0x40).toString(16).uppercase().padStart(2, '0')
        val marker = responseMode + pid.uppercase()

        val idx = clean.indexOf(marker)
        if (idx < 0) return Result.Failure(AppError.CorruptResponse(raw))

        val payload = clean.substring(idx + marker.length)
        val bytes = ArrayList<Int>()
        var i = 0
        while (i + 1 < payload.length) {
            val b = payload.substring(i, i + 2).toIntOrNull(16) ?: break
            bytes.add(b)
            i += 2
        }
        if (bytes.isEmpty()) return Result.Failure(AppError.CorruptResponse(raw))

        return try {
            Result.Success(decode(bytes))
        } catch (e: Exception) {
            Result.Failure(AppError.CorruptResponse(raw))
        }
    }

    /** Único método que cada PID concreto implementa: su fórmula. */
    protected abstract fun decode(bytes: List<Int>): T
}
