package com.apex.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apex.app.core.Result
import com.apex.app.data.bluetooth.BluetoothDeviceInfo
import com.apex.app.data.bluetooth.ConnectionState
import com.apex.app.data.bluetooth.ObdTransport
import com.apex.app.data.obd.ObdInitializer
import com.apex.app.data.obd.commands.CoolantCommand
import com.apex.app.data.obd.commands.EngineLoadCommand
import com.apex.app.data.obd.commands.IntakeTempCommand
import com.apex.app.data.obd.commands.MapCommand
import com.apex.app.data.obd.commands.RpmCommand
import com.apex.app.data.obd.commands.SpeedCommand
import com.apex.app.data.obd.commands.ThrottleCommand
import com.apex.app.data.obd.commands.TimingAdvanceCommand
import com.apex.app.data.obd.commands.VoltageCommand
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Estado inmutable que la pantalla observa. */
data class LiveUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val devices: List<BluetoothDeviceInfo> = emptyList(),
    val rpm: Int? = null,
    val speed: Int? = null,
    val coolant: Int? = null,
    // Datos extra (se leen rotando, uno por ciclo)
    val throttle: Int? = null,
    val load: Int? = null,
    val map: Int? = null,
    val intakeTemp: Int? = null,
    val timing: Int? = null,
    val voltage: Float? = null,
    val log: List<String> = emptyList()
)

class LiveViewModel(
    private val transport: ObdTransport,
    private val initializer: ObdInitializer
) : ViewModel() {

    private companion object {
        const val POLL_DELAY_MS = 150L
        const val MAX_LOG_LINES = 40
    }

    private val _ui = MutableStateFlow(LiveUiState())
    val ui: StateFlow<LiveUiState> = _ui.asStateFlow()

    private val rpmCmd = RpmCommand()
    private val speedCmd = SpeedCommand()
    private val coolantCmd = CoolantCommand()
    private val throttleCmd = ThrottleCommand()
    private val loadCmd = EngineLoadCommand()
    private val mapCmd = MapCommand()
    private val intakeCmd = IntakeTempCommand()
    private val timingCmd = TimingAdvanceCommand()
    private val voltageCmd = VoltageCommand()
    private var pollJob: Job? = null

    private val extraReaders: List<suspend () -> Unit> = listOf(
        ::readThrottle, ::readLoad, ::readMap, ::readIntake, ::readTiming, ::readVoltage
    )
    private var extraIndex = 0

    init {
        viewModelScope.launch {
            transport.connectionState.collect { state ->
                _ui.value = _ui.value.copy(connection = state)
            }
        }
        loadDevices()
    }

    fun loadDevices() {
        when (val r = transport.pairedDevices()) {
            is Result.Success -> _ui.value = _ui.value.copy(devices = r.data)
            is Result.Failure -> appendLog(r.error.message)
        }
    }

    fun connect(address: String) {
        viewModelScope.launch {
            appendLog("Conectando a $address…")
            when (val r = transport.connect(address)) {
                is Result.Success -> {
                    appendLog("Conectado. Inicializando…")
                    initializer.initialize().forEach { appendLog(it) }
                    startPolling()
                }
                is Result.Failure -> appendLog("Error: ${r.error.message}")
            }
        }
    }

    fun disconnect() {
        pollJob?.cancel()
        viewModelScope.launch {
            transport.disconnect()
            appendLog("Desconectado.")
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                readRpm()
                readSpeed()
                readCoolant()
                // Un dato extra por ciclo (rotando), para no frenar los medidores grandes.
                extraReaders[extraIndex].invoke()
                extraIndex = (extraIndex + 1) % extraReaders.size
                delay(POLL_DELAY_MS)
            }
        }
    }

    private suspend fun readRpm() {
        val r = transport.send(rpmCmd.command)
        if (r is Result.Success) {
            val v = rpmCmd.parse(r.data)
            if (v is Result.Success) _ui.value = _ui.value.copy(rpm = v.data)
        }
    }

    private suspend fun readSpeed() {
        val r = transport.send(speedCmd.command)
        if (r is Result.Success) {
            val v = speedCmd.parse(r.data)
            if (v is Result.Success) _ui.value = _ui.value.copy(speed = v.data)
        }
    }

    private suspend fun readCoolant() {
        val r = transport.send(coolantCmd.command)
        if (r is Result.Success) {
            val v = coolantCmd.parse(r.data)
            if (v is Result.Success) _ui.value = _ui.value.copy(coolant = v.data)
        }
    }

    private suspend fun readThrottle() {
        val r = transport.send(throttleCmd.command)
        if (r is Result.Success) {
            val v = throttleCmd.parse(r.data)
            if (v is Result.Success) _ui.value = _ui.value.copy(throttle = v.data)
        }
    }

    private suspend fun readLoad() {
        val r = transport.send(loadCmd.command)
        if (r is Result.Success) {
            val v = loadCmd.parse(r.data)
            if (v is Result.Success) _ui.value = _ui.value.copy(load = v.data)
        }
    }

    private suspend fun readMap() {
        val r = transport.send(mapCmd.command)
        if (r is Result.Success) {
            val v = mapCmd.parse(r.data)
            if (v is Result.Success) _ui.value = _ui.value.copy(map = v.data)
        }
    }

    private suspend fun readIntake() {
        val r = transport.send(intakeCmd.command)
        if (r is Result.Success) {
            val v = intakeCmd.parse(r.data)
            if (v is Result.Success) _ui.value = _ui.value.copy(intakeTemp = v.data)
        }
    }

    private suspend fun readTiming() {
        val r = transport.send(timingCmd.command)
        if (r is Result.Success) {
            val v = timingCmd.parse(r.data)
            if (v is Result.Success) _ui.value = _ui.value.copy(timing = v.data)
        }
    }

    private suspend fun readVoltage() {
        val r = transport.send(voltageCmd.command)
        if (r is Result.Success) {
            val v = voltageCmd.parse(r.data)
            if (v is Result.Success) _ui.value = _ui.value.copy(voltage = v.data)
        }
    }

    private fun appendLog(msg: String) {
        _ui.value = _ui.value.copy(log = (_ui.value.log + msg).takeLast(MAX_LOG_LINES))
    }

    /** Fábrica manual (inyección simple, sin Hilt por ahora). */
    class Factory(
        private val transport: ObdTransport,
        private val initializer: ObdInitializer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LiveViewModel(transport, initializer) as T
    }
}
