package com.apex.app.core

/**
 * Constantes globales. Centralizar evita los 'magic numbers'.
 */
object Constants {

    /** Comandos de configuración del adaptador ELM327. */
    object ElmCommands {
        const val RESET = "ATZ"
        const val ECHO_OFF = "ATE0"
        const val LINEFEED_OFF = "ATL0"
        const val SPACES_OFF = "ATS0"
        const val HEADERS_OFF = "ATH0"
        const val PROTOCOL_AUTO = "ATSP0"
    }

    /** Modos de servicio OBD-II (SAE J1979). */
    object ObdMode {
        const val LIVE_DATA = "01"
        const val READ_DTC = "03"
        const val CLEAR_DTC = "04"
    }

    /** Respuestas especiales del ELM327. */
    object ElmResponse {
        const val PROMPT = ">"
        const val NO_DATA = "NO DATA"
        const val ERROR = "ERROR"
        const val QUESTION = "?"
    }

    /** Tiempos de espera y reintentos (milisegundos). */
    object Timeouts {
        const val COMMAND_RESPONSE_MS = 2_000L
        const val RECONNECT_DELAY_MS = 1_500L
        const val MAX_RECONNECT_ATTEMPTS = 3
    }

    /** UUID estándar del perfil Serial Port (SPP) del Bluetooth clásico. */
    const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
}
