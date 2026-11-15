package com.apex.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apex.app.data.drag.DragHistoryRepository
import com.apex.app.data.drag.DragResult
import com.apex.app.data.location.SpeedProvider
import com.apex.app.data.location.SpeedSample
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class MeasurePhase { IDLE, ARMED, RUNNING, FINISHED }

data class MeasureUiState(
    val phase: MeasurePhase = MeasurePhase.IDLE,
    val currentSpeedKmh: Int = 0,
    val elapsedMs: Long = 0L,
    val t60Ms: Long? = null,
    val t100Ms: Long? = null,
    val topSpeedKmh: Int = 0,
    val message: String? = null
)

/**
 * Mide el pique (0-60 y 0-100 km/h) usando la velocidad del GPS.
 * Detecta el arranque, cronometra y guarda el resultado en el historial.
 * Usa interpolación entre muestras para estimar el cruce exacto de cada marca.
 */
class MeasureViewModel(
    private val speedProvider: SpeedProvider,
    private val history: DragHistoryRepository
) : ViewModel() {

    private companion object {
        const val LAUNCH_KMH = 2f     // umbral de arranque (casi desde cero)
        const val SIXTY = 60f
        const val HUNDRED = 100f
    }

    private val _ui = MutableStateFlow(MeasureUiState())
    val ui: StateFlow<MeasureUiState> = _ui.asStateFlow()

    private val _history = MutableStateFlow<List<DragResult>>(emptyList())
    val historyList: StateFlow<List<DragResult>> = _history.asStateFlow()

    private var collectJob: Job? = null
    private var tickJob: Job? = null

    // Estado interno de la corrida
    private var prev: SpeedSample? = null
    private var launchNanos: Long = 0L

    init {
        _history.value = history.load()
    }

    /** El usuario toca "Armar": empieza a esperar el arranque. */
    fun arm() {
        if (!speedProvider.hasLocationPermission()) {
            _ui.value = MeasureUiState(message = "Falta el permiso de ubicación (GPS). Conéctalo para medir.")
            return
        }
        if (!speedProvider.isGpsEnabled()) {
            _ui.value = MeasureUiState(message = "Activa el GPS (ubicación) del teléfono para medir.")
            return
        }
        resetInternal()
        _ui.value = MeasureUiState(phase = MeasurePhase.ARMED, message = "Listo. Acelera a fondo desde parado.")
        startCollecting()
    }

    /** Cancelar / reiniciar a estado inicial. */
    fun reset() {
        collectJob?.cancel()
        tickJob?.cancel()
        resetInternal()
        _ui.value = MeasureUiState()
    }

    private fun resetInternal() {
        prev = null
        launchNanos = 0L
    }

    private fun startCollecting() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            speedProvider.speedFlow().collect { sample -> onSample(sample) }
        }
    }

    private fun onSample(sample: SpeedSample) {
        val state = _ui.value
        val speedInt = sample.kmh.roundToInt()
        val top = maxOf(state.topSpeedKmh, speedInt)
        val p = prev

        when (state.phase) {
            MeasurePhase.ARMED -> {
                _ui.value = state.copy(currentSpeedKmh = speedInt, topSpeedKmh = top)
                // ¿Cruzó el umbral de arranque hacia arriba?
                if (p != null && p.kmh < LAUNCH_KMH && sample.kmh >= LAUNCH_KMH) {
                    launchNanos = interpolateTime(p, sample, LAUNCH_KMH)
                    _ui.value = _ui.value.copy(phase = MeasurePhase.RUNNING, message = null)
                    startTicker()
                }
            }

            MeasurePhase.RUNNING -> {
                var t60 = state.t60Ms
                var t100 = state.t100Ms
                if (p != null) {
                    if (t60 == null && p.kmh < SIXTY && sample.kmh >= SIXTY) {
                        t60 = nanosToMs(interpolateTime(p, sample, SIXTY) - launchNanos)
                    }
                    if (t100 == null && p.kmh < HUNDRED && sample.kmh >= HUNDRED) {
                        t100 = nanosToMs(interpolateTime(p, sample, HUNDRED) - launchNanos)
                    }
                }
                _ui.value = state.copy(
                    currentSpeedKmh = speedInt,
                    topSpeedKmh = top,
                    t60Ms = t60,
                    t100Ms = t100
                )
                if (t100 != null) finish(top, t60, t100)
            }

            else -> {
                _ui.value = state.copy(currentSpeedKmh = speedInt)
            }
        }
        prev = sample
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive && _ui.value.phase == MeasurePhase.RUNNING) {
                val ms = nanosToMs(System.nanoTime() - launchNanos)
                _ui.value = _ui.value.copy(elapsedMs = ms)
                delay(40L)
            }
        }
    }

    private fun finish(top: Int, t60: Long?, t100: Long?) {
        collectJob?.cancel()
        tickJob?.cancel()
        val result = DragResult(
            id = System.currentTimeMillis(),
            dateMillis = System.currentTimeMillis(),
            time0to60Ms = t60,
            time0to100Ms = t100,
            topSpeedKmh = top
        )
        _history.value = history.add(result)
        _ui.value = _ui.value.copy(
            phase = MeasurePhase.FINISHED,
            elapsedMs = t100 ?: _ui.value.elapsedMs,
            t60Ms = t60,
            t100Ms = t100,
            topSpeedKmh = top,
            message = null
        )
    }

    fun clearHistory() {
        history.clear()
        _history.value = emptyList()
    }

    private fun interpolateTime(a: SpeedSample, b: SpeedSample, target: Float): Long {
        val range = b.kmh - a.kmh
        if (range <= 0f) return b.timeNanos
        val fraction = ((target - a.kmh) / range).coerceIn(0f, 1f)
        return a.timeNanos + (fraction * (b.timeNanos - a.timeNanos)).toLong()
    }

    private fun nanosToMs(nanos: Long): Long = nanos / 1_000_000L

    override fun onCleared() {
        collectJob?.cancel()
        tickJob?.cancel()
    }

    class Factory(
        private val speedProvider: SpeedProvider,
        private val history: DragHistoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MeasureViewModel(speedProvider, history) as T
    }
}
