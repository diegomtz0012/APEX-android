package com.apex.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apex.app.core.Result
import com.apex.app.data.bluetooth.ConnectionState
import com.apex.app.data.bluetooth.ObdTransport
import com.apex.app.data.location.GpsPoint
import com.apex.app.data.location.SpeedProvider
import com.apex.app.data.obd.commands.MafCommand
import com.apex.app.data.trip.LatLng
import com.apex.app.data.trip.Trip
import com.apex.app.data.trip.TripRepository
import com.apex.app.data.vehicle.VehicleProfileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class TripUiState(
    val recording: Boolean = false,
    val route: List<LatLng> = emptyList(),
    val distanceKm: Double = 0.0,
    val durationMs: Long = 0L,
    val curSpeedKmh: Int = 0,
    val maxKmh: Int = 0,
    val fuelLiters: Double? = null,
    val kmPerL: Double? = null,
    val finished: Boolean = false,
    val message: String? = null
)

/**
 * Graba un viaje: la ruta por GPS (distancia, velocidad) y, si el carro está
 * conectado y entrega MAF, una estimación del consumo de combustible.
 */
class TripViewModel(
    private val speedProvider: SpeedProvider,
    private val transport: ObdTransport,
    private val tripRepo: TripRepository,
    private val vehicleRepo: VehicleProfileRepository
) : ViewModel() {

    private companion object {
        const val EARTH_KM = 6371.0
        const val MIN_SEGMENT_KM = 0.0025   // < 2.5 m = ruido del GPS parado
        const val MAX_SEGMENT_KM = 0.5      // > 500 m en una muestra = salto/pérdida de señal
    }

    private val _ui = MutableStateFlow(TripUiState())
    val ui: StateFlow<TripUiState> = _ui.asStateFlow()

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private var collectJob: Job? = null
    private var tickJob: Job? = null
    private var mafJob: Job? = null

    // Estado interno de la grabación
    private val routePoints = ArrayList<LatLng>()
    private var distanceKm = 0.0
    private var maxKmh = 0
    private var startMillis = 0L
    private var last: LatLng? = null
    private var fuelLiters = 0.0
    private var hasFuel = false
    private var lastMafNanos = 0L
    private var afr = 14.7
    private var density = 745.0 // g/L gasolina

    init {
        _trips.value = tripRepo.load()
    }

    fun start() {
        if (!speedProvider.hasLocationPermission()) {
            _ui.value = TripUiState(message = "Falta el permiso de ubicación (GPS).")
            return
        }
        if (!speedProvider.isGpsEnabled()) {
            _ui.value = TripUiState(message = "Activa el GPS (ubicación) del teléfono.")
            return
        }

        // Constantes de combustible según el perfil del carro.
        val profile = vehicleRepo.load()
        if (profile.diesel) {
            afr = 14.5; density = 832.0
        } else {
            afr = 14.7; density = 745.0
        }

        routePoints.clear()
        distanceKm = 0.0
        maxKmh = 0
        last = null
        fuelLiters = 0.0
        hasFuel = false
        lastMafNanos = 0L
        startMillis = System.currentTimeMillis()

        _ui.value = TripUiState(recording = true, message = "Grabando viaje…")

        collectJob = viewModelScope.launch {
            speedProvider.locationFlow().collect { onPoint(it) }
        }
        tickJob = viewModelScope.launch {
            while (isActive && _ui.value.recording) {
                _ui.value = _ui.value.copy(durationMs = System.currentTimeMillis() - startMillis)
                delay(250L)
            }
        }
        mafJob = viewModelScope.launch {
            while (isActive && _ui.value.recording) {
                pollMaf()
                delay(1000L)
            }
        }
    }

    private fun onPoint(p: GpsPoint) {
        val cur = LatLng(p.lat, p.lon)
        val speedInt = p.kmh.roundToInt()
        if (speedInt > maxKmh) maxKmh = speedInt

        val prev = last
        if (prev == null) {
            routePoints.add(cur)
            last = cur
        } else {
            val seg = haversineKm(prev, cur)
            if (seg in MIN_SEGMENT_KM..MAX_SEGMENT_KM) {
                distanceKm += seg
                routePoints.add(cur)
                last = cur
            } else if (seg > MAX_SEGMENT_KM) {
                // salto grande (pérdida de señal): resincroniza sin sumar distancia
                last = cur
            }
            // seg muy pequeño: ruido estando parado, se ignora
        }

        _ui.value = _ui.value.copy(
            route = ArrayList(routePoints),
            distanceKm = distanceKm,
            curSpeedKmh = speedInt,
            maxKmh = maxKmh
        )
    }

    private suspend fun pollMaf() {
        if (transport.connectionState.value !is ConnectionState.Connected) return
        val cmd = MafCommand()
        when (val r = transport.send(cmd.command)) {
            is Result.Success -> {
                val parsed = cmd.parse(r.data)
                if (parsed is Result.Success) {
                    val maf = parsed.data // g/s
                    val now = System.nanoTime()
                    if (lastMafNanos != 0L) {
                        val dt = (now - lastMafNanos) / 1_000_000_000.0 // s
                        // g aire * s / AFR = g combustible; / densidad (g/L) = L
                        fuelLiters += maf * dt / afr / density
                        hasFuel = true
                        val kmPerL = if (fuelLiters > 0.0) distanceKm / fuelLiters else null
                        _ui.value = _ui.value.copy(fuelLiters = fuelLiters, kmPerL = kmPerL)
                    }
                    lastMafNanos = now
                }
            }
            is Result.Failure -> { /* el carro no entrega MAF: viaje sin consumo */ }
        }
    }

    fun stop() {
        collectJob?.cancel()
        tickJob?.cancel()
        mafJob?.cancel()

        val durationMs = System.currentTimeMillis() - startMillis
        val hours = durationMs / 3_600_000.0
        val avg = if (hours > 0.0) distanceKm / hours else 0.0
        val fuel = if (hasFuel) fuelLiters else null
        val kmPerL = if (hasFuel && fuelLiters > 0.0) distanceKm / fuelLiters else null

        val trip = Trip(
            id = System.currentTimeMillis(),
            dateMillis = System.currentTimeMillis(),
            distanceKm = distanceKm,
            durationMs = durationMs,
            avgKmh = avg,
            maxKmh = maxKmh,
            fuelLiters = fuel,
            kmPerL = kmPerL,
            route = ArrayList(routePoints)
        )
        _trips.value = tripRepo.add(trip)

        _ui.value = _ui.value.copy(
            recording = false,
            finished = true,
            durationMs = durationMs,
            fuelLiters = fuel,
            kmPerL = kmPerL,
            message = null
        )
    }

    fun dismissSummary() {
        _ui.value = TripUiState()
    }

    fun clearTrips() {
        tripRepo.clear()
        _trips.value = emptyList()
    }

    private fun haversineKm(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_KM * asin(sqrt(h))
    }

    override fun onCleared() {
        collectJob?.cancel()
        tickJob?.cancel()
        mafJob?.cancel()
    }

    class Factory(
        private val speedProvider: SpeedProvider,
        private val transport: ObdTransport,
        private val tripRepo: TripRepository,
        private val vehicleRepo: VehicleProfileRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TripViewModel(speedProvider, transport, tripRepo, vehicleRepo) as T
    }
}
