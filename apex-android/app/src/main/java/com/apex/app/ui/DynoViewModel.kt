package com.apex.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apex.app.core.Result
import com.apex.app.data.bluetooth.ConnectionState
import com.apex.app.data.bluetooth.ObdTransport
import com.apex.app.data.location.SpeedProvider
import com.apex.app.data.location.SpeedSample
import com.apex.app.data.obd.commands.RpmCommand
import com.apex.app.data.vehicle.VehicleProfileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Un punto de la curva: velocidad (km/h) y potencia estimada (HP). */
data class CurvePoint(val speedKmh: Int, val hp: Int)

enum class DynoPhase { IDLE, RECORDING, FINISHED }

data class DynoUiState(
    val phase: DynoPhase = DynoPhase.IDLE,
    val curSpeedKmh: Int = 0,
    val curHp: Int = 0,
    val curRpm: Int? = null,
    val peakHp: Int = 0,
    val peakHpAtKmh: Int = 0,
    val peakTorqueNm: Int? = null,
    val peakTorqueAtRpm: Int? = null,
    val curve: List<CurvePoint> = emptyList(),
    val message: String? = null
)

/**
 * Dinamómetro virtual. Estima la potencia (HP) y el torque (Nm) a partir de
 * la aceleración por GPS y la física del vehículo (inercia + aerodinámica +
 * rodadura). Los números son ESTIMACIONES; sirven para comparar antes/después.
 */
class DynoViewModel(
    private val speedProvider: SpeedProvider,
    private val transport: ObdTransport,
    private val vehicleRepo: VehicleProfileRepository
) : ViewModel() {

    private companion object {
        const val DRIVER_KG = 75.0
        const val RHO = 1.2            // densidad del aire kg/m³
        const val CDA = 0.65          // Cd * área frontal (estimado, sedán)
        const val CRR = 0.013         // coef. de rodadura
        const val G = 9.81
        const val DRIVETRAIN_EFF = 0.85
        const val WATT_PER_HP = 745.7
        const val MIN_MS = 5.0        // velocidad mínima para medir (m/s)
        const val MAX_HP = 1500       // tope de cordura
        const val MAX_NM = 2000
    }

    private val _ui = MutableStateFlow(DynoUiState())
    val ui: StateFlow<DynoUiState> = _ui.asStateFlow()

    private var collectJob: Job? = null
    private var rpmJob: Job? = null
    private val rpmCmd = RpmCommand()

    private var mass = 0.0
    private var prev: SpeedSample? = null
    private var lastRpm: Int? = null
    private val buckets = HashMap<Int, Int>() // velocidad(redondeada a 5) -> HP máx
    private var peakHp = 0
    private var peakHpAtKmh = 0
    private var peakTorque = 0
    private var peakTorqueRpm = 0

    fun start() {
        if (!speedProvider.hasLocationPermission()) {
            _ui.value = DynoUiState(message = "Falta el permiso de ubicación (GPS).")
            return
        }
        if (!speedProvider.isGpsEnabled()) {
            _ui.value = DynoUiState(message = "Activa el GPS (ubicación) del teléfono.")
            return
        }
        val weight = vehicleRepo.load().weightKg.toIntOrNull()
        if (weight == null || weight <= 0) {
            _ui.value = DynoUiState(message = "Primero configura el PESO de tu carro en la pestaña “Carro”.")
            return
        }
        mass = weight + DRIVER_KG

        prev = null
        lastRpm = null
        buckets.clear()
        peakHp = 0; peakHpAtKmh = 0; peakTorque = 0; peakTorqueRpm = 0

        _ui.value = DynoUiState(
            phase = DynoPhase.RECORDING,
            message = "Acelera a fondo en una marcha (ej. 3ª), de baja a alta. Luego “Terminar”."
        )

        collectJob = viewModelScope.launch {
            speedProvider.speedFlow().collect { onSample(it) }
        }
        rpmJob = viewModelScope.launch {
            while (isActive && _ui.value.phase == DynoPhase.RECORDING) {
                if (transport.connectionState.value is ConnectionState.Connected) {
                    val r = transport.send(rpmCmd.command)
                    if (r is Result.Success) {
                        val v = rpmCmd.parse(r.data)
                        if (v is Result.Success) lastRpm = v.data
                    }
                }
                delay(500L)
            }
        }
    }

    private fun onSample(sample: SpeedSample) {
        val p = prev
        val speedInt = sample.kmh.roundToInt()

        if (p != null) {
            val dt = (sample.timeNanos - p.timeNanos) / 1_000_000_000.0
            val v = sample.kmh / 3.6 // m/s
            if (dt > 0.0 && v >= MIN_MS) {
                val a = (sample.kmh - p.kmh) / 3.6 / dt // m/s²
                if (a > 0.0) {
                    val fInertia = mass * a
                    val fAero = 0.5 * RHO * CDA * v * v
                    val fRoll = CRR * mass * G
                    val pWheel = (fInertia + fAero + fRoll) * v
                    val pCrank = pWheel / DRIVETRAIN_EFF
                    val hp = (pCrank / WATT_PER_HP).roundToInt()
                    if (hp in 1..MAX_HP) {
                        val bucket = (speedInt / 5) * 5
                        val best = buckets[bucket] ?: 0
                        if (hp > best) buckets[bucket] = hp
                        if (hp > peakHp) {
                            peakHp = hp; peakHpAtKmh = speedInt
                        }
                        // Torque si hay RPM del OBD
                        val rpm = lastRpm
                        if (rpm != null && rpm > 0) {
                            val nm = (pCrank * 9.5493 / rpm).roundToInt()
                            if (nm in 1..MAX_NM && nm > peakTorque) {
                                peakTorque = nm; peakTorqueRpm = rpm
                            }
                        }
                        _ui.value = _ui.value.copy(
                            curSpeedKmh = speedInt,
                            curHp = hp,
                            curRpm = lastRpm,
                            peakHp = peakHp,
                            peakHpAtKmh = peakHpAtKmh,
                            peakTorqueNm = if (peakTorque > 0) peakTorque else null,
                            peakTorqueAtRpm = if (peakTorque > 0) peakTorqueRpm else null
                        )
                    }
                } else {
                    _ui.value = _ui.value.copy(curSpeedKmh = speedInt, curRpm = lastRpm)
                }
            }
        }
        prev = sample
    }

    fun stop() {
        collectJob?.cancel()
        rpmJob?.cancel()
        val curve = buckets.toSortedMap().map { CurvePoint(it.key, it.value) }
        _ui.value = _ui.value.copy(
            phase = DynoPhase.FINISHED,
            curve = curve,
            message = if (curve.isEmpty())
                "No se capturó potencia. Haz una aceleración a fondo más larga."
            else null
        )
    }

    fun reset() {
        collectJob?.cancel()
        rpmJob?.cancel()
        _ui.value = DynoUiState()
    }

    override fun onCleared() {
        collectJob?.cancel()
        rpmJob?.cancel()
    }

    class Factory(
        private val speedProvider: SpeedProvider,
        private val transport: ObdTransport,
        private val vehicleRepo: VehicleProfileRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DynoViewModel(speedProvider, transport, vehicleRepo) as T
    }
}
