package com.apex.app.core

/**
 * Envoltorio type-safe para operaciones que pueden fallar.
 * El compilador obliga a manejar ambos casos (Success y Failure).
 */
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Failure(val error: AppError) : Result<Nothing>()
}

/**
 * Catálogo cerrado de errores posibles de la aplicación.
 */
sealed class AppError(val message: String) {
    // Bluetooth
    data object BluetoothDisabled : AppError("El Bluetooth está apagado.")
    data object BluetoothPermissionDenied : AppError("Falta el permiso de Bluetooth.")
    data object DeviceNotFound : AppError("No se encontró el adaptador OBD.")
    data object ConnectionFailed : AppError("No se pudo conectar al adaptador.")
    data object ConnectionLost : AppError("Se perdió la conexión con el adaptador.")
    data object Timeout : AppError("El adaptador no respondió a tiempo.")

    // Protocolo OBD / ELM327
    data object NoData : AppError("El vehículo no entregó datos (NO DATA).")
    data object AdapterError : AppError("El adaptador reportó un error (ERROR).")
    data object VehicleUnreachable :
        AppError("El adaptador no logró hablar con la ECU. Revisa que el carro esté en contacto y que el ELM327 esté bien puesto.")
    data class CorruptResponse(val raw: String) : AppError("Respuesta ilegible: $raw")

    // Inesperado
    data class Unknown(val detail: String) : AppError("Error inesperado: $detail")
}
