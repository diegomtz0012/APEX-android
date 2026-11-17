package com.apex.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apex.app.core.Result
import com.apex.app.data.bluetooth.BluetoothDeviceInfo
import com.apex.app.data.bluetooth.ConnectionState
import com.apex.app.data.bluetooth.ObdTransport
import com.apex.app.data.obd.ObdInitializer
import com.apex.app.data.obd.VehicleInfo
import com.apex.app.data.obd.VinDecoder
import com.apex.app.data.vehicle.VehicleProfile
import com.apex.app.data.vehicle.VehicleProfileRepository
import com.apex.app.data.obd.commands.CoolantCommand
import com.apex.app.data.obd.commands.EngineLoadCommand
import com.apex.app.data.obd.commands.IntakeTempCommand
import com.apex.app.data.obd.commands.MapCommand
import com.apex.app.data.obd.commands.RpmCommand
import com.apex.app.data.obd.commands.SpeedCommand
import com.apex.app.data.obd.commands.ThrottleCommand
import com.apex.app.data.obd.commands.TimingAdvanceCommand
import com.apex.app.data.obd.commands.VoltageCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val vehicle: VehicleInfo? = null,
    val log: List<String> = emptyList()
)

class LiveViewModel(
    private val transport: ObdTransport,
    private val initializer: ObdInitializer,
    private val vehicleRepo: VehicleProfileRepository
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
                    scanVehicle()
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

    /** Barrido inicial: lee VIN y combustible, decodifica y crea el perfil solo. */
    private suspend fun scanVehicle() {
        appendLog("Barrido del vehículo…")

        // Primar el protocolo: la primera petición dispara la búsqueda del ELM327
        // (puede tardar). Esto la fija ANTES de leer el VIN, que si no se perdía.
        repeat(2) { transport.send("0100") }

        // VIN (modo 09 PID 02) — con diagnóstico de lo que respondió el carro.
        val vinResp = transport.send("0902")
        val vinRaw = if (vinResp is Result.Success) vinResp.data else "(sin respuesta)"
        appendLog("VIN resp: ${vinRaw.replace("\r", " ").replace("\n", " ").trim().take(60)}")
        val vin = if (vinResp is Result.Success) VinDecoder.parseVin(vinResp.data) else null

        // Combustible (modo 01 PID 51)
        val fuelResp = transport.send("0151")
        val fuelRaw = if (fuelResp is Result.Success) fuelResp.data else "(sin respuesta)"
        appendLog("Combustible resp: ${fuelRaw.replace("\r", " ").replace("\n", " ").trim().take(40)}")
        val diesel = if (fuelResp is Result.Success) VinDecoder.parseFuelType(fuelResp.data) else null

        if (vin == null && diesel == null) {
            appendLog("Tu carro no entregó VIN ni combustible por OBD. Ponlo a mano en 'Carro'.")
            return
        }

        var make: String? = null
        var model: String? = null
        var year: String? = null
        var disp: String? = null
        if (vin != null) {
            appendLog("VIN: $vin")
            val online = withContext(Dispatchers.IO) { VinDecoder.decodeOnline(vin) }
            if (online != null) {
                make = online.make; model = online.model; year = online.year; disp = online.displacementL
            }
            if (make == null) make = VinDecoder.makeFromVin(vin)
            if (year == null) year = VinDecoder.yearFromVin(vin)
        }

        val info = VehicleInfo(vin, make, model, year, diesel)
        _ui.value = _ui.value.copy(vehicle = info)
        val detected = listOfNotNull(make, model, year).joinToString(" ").ifBlank { "—" }
        val fuelTxt = diesel?.let { if (it) " · Diésel" else " · Gasolina" } ?: ""
        appendLog("Detectado: $detected$fuelTxt")

        // Crear/actualizar el perfil sin pisar lo que el usuario ya haya puesto.
        val current = vehicleRepo.load()
        val updated = current.copy(
            make = current.make.ifBlank { make ?: "" },
            model = current.model.ifBlank { model ?: "" },
            year = current.year.ifBlank { year ?: "" },
            vin = current.vin.ifBlank { vin ?: "" },
            displacementL = current.displacementL.ifBlank { disp ?: "" },
            diesel = diesel ?: current.diesel,
            nickname = current.nickname.ifBlank { listOfNotNull(make, model).joinToString(" ") }
        )
        vehicleRepo.save(updated)
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
        private val initializer: ObdInitializer,
        private val vehicleRepo: VehicleProfileRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LiveViewModel(transport, initializer, vehicleRepo) as T
    }
}
