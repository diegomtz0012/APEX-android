package com.apex.app.data.obd

import com.apex.app.core.Constants
import com.apex.app.core.Result
import com.apex.app.data.bluetooth.ObdTransport
import kotlinx.coroutines.delay

/**
 * Envía la secuencia de comandos AT que deja el ELM327 listo
 * y devuelve un registro legible de cada paso (para mostrar en UI).
 */
class ObdInitializer(private val transport: ObdTransport) {

    private companion object {
        const val INIT_DELAY_MS = 200L
    }

    suspend fun initialize(): List<String> {
        val log = mutableListOf<String>()
        val sequence = listOf(
            Constants.ElmCommands.RESET,
            Constants.ElmCommands.ECHO_OFF,
            Constants.ElmCommands.LINEFEED_OFF,
            Constants.ElmCommands.SPACES_OFF,
            Constants.ElmCommands.HEADERS_OFF,
            Constants.ElmCommands.PROTOCOL_AUTO
        )
        for (command in sequence) {
            when (val response = transport.send(command)) {
                is Result.Success ->
                    log.add("$command → ${response.data.replace("\r", " ").trim()}")
                is Result.Failure ->
                    log.add("$command → (${response.error.message})")
            }
            delay(INIT_DELAY_MS)
        }
        log.add("Adaptador inicializado.")
        return log
    }
}
