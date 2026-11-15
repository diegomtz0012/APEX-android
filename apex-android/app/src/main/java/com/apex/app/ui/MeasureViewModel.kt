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

/** Tipos de prueba disponibles. */
enum class TestType(
    val label: String,
    val startKmh: Float,       // velocidad a la que arranca el cronómetro
    val endKmh: Float,         // velocidad a la que termina (si no es por distancia)
    val isDistance: Boolean = false,
    val distanceM: Double = 0.0,
    val fromStandstill: Boolean = true
) {
    ACCEL_0_100("0-100 km/h", 2f, 100f),
    ACCEL_0_200("0-200 km/h", 2f, 200f),
    QUARTER_MILE("¼ de milla", 2f, 0f, isDistance = true, distanceM = 402.336),
    ROLL_60_100("60-100 km/h", 60f, 100f, fromStandstill = false),
    ROLL_80_120("80-120 km/h", 80f, 120f, fromStandstill = false)
}

data class MeasureUiState(
    val phase: MeasurePhase = MeasurePhase.IDLE,
    val test: TestType = TestType.ACCEL_0_100,
    val currentSpeedKmh: Int = 0,
    val elapsedMs: Long = 0L,
    val resultMs: Long? = null,
    val note: String? = null,
    val topSpeedKmh: Int = 0,
    val distanceM: Double = 0.0,
    val message: String? = null
)

/**
 * Mide pruebas de aceleración con el GPS: 0-100, 0-200, ¼ de milla, 60-100, 80-120.
 * Detecta el arranque, cronometra con interpolación y guarda el resultado.
 */
class MeasureViewModel(
    private val speedProvider: SpeedProvider,
    private val history: DragHistoryRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(MeasureUiState())
    val ui: StateFlow<MeasureUiState> = _ui.asStateFlow()

    private val _history = MutableStateFlow<List<DragResult>>(emptyList())
    val historyList: StateFlow<List<DragResult>> = _history.asStateFlow()

    private var collectJob: Job? = null
    private var tickJob: Job? = null

    private var prev: SpeedSample? = null
    private var startNanos: Long = 0L
    private var distanceM: Double = 0.0
    private var secondaryMs: Long? = null // 0-60 dentro de 0-100 / 0-200

    init {
        _history.value = history.load()
    }

    fun selectTest(test: TestType) {
        if (_ui.value.phase == MeasurePhase.IDLE || _ui.value.phase == MeasurePhase.FINISHED) {
            _ui.value = MeasureUiState(test = test)
        }
    }

    fun arm() {
        if (!speedProvider.hasLocationPermission()) {
            _ui.value = _ui.value.copy(message = "Falta el permiso de ubicación (GPS).")
            return
        }
        if (!speedProvider.isGpsEnabled()) {
            _ui.value = _ui.value.copy(message = "Activa el GPS (ubicación) del teléfono.")
            return
        }
        val test = _ui.value.test
        resetInternal()
        val hint = if (test.fromStandstill) "Acelera a fondo desde parado."
        else "Alcanza ${test.startKmh.toInt()} km/h y acelera a fondo."
        _ui.value = MeasureUiState(phase = MeasurePhase.ARMED, test = test, message = hint)
        startCollecting()
    }

    fun reset() {
        collectJob?.cancel()
        tickJob?.cancel()
        val test = _ui.value.test
        resetInternal()
        _ui.value = MeasureUiState(test = test)
    }

    private fun resetInternal() {
        prev = null
        startNanos = 0L
        distanceM = 0.0
        secondaryMs = null
    }

    private fun startCollecting() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            speedProvider.speedFlow().collect { onSample(it) }
        }
    }

    private fun onSample(sample: SpeedSample) {
        val state = _ui.value
        val test = state.test
        val speedInt = sample.kmh.roundToInt()
        val top = maxOf(state.topSpeedKmh, speedInt)
        val p = prev

        when (state.phase) {
            MeasurePhase.ARMED -> {
                _ui.value = state.copy(currentSpeedKmh = speedInt, topSpeedKmh = top)
                if (p != null && p.kmh < test.startKmh && sample.kmh >= test.startKmh) {
                    startNanos = interpolateTime(p, sample, test.startKmh)
                    distanceM = 0.0
                    _ui.value = _ui.value.copy(phase = MeasurePhase.RUNNING, message = null)
                    startTicker()
                }
            }

            MeasurePhase.RUNNING -> {
                // Integrar distancia (para ¼ de milla)
                if (p != null) {
                    val dtSec = (sample.timeNanos - p.timeNanos) / 1_000_000_000.0
                    val avgMs = (p.kmh + sample.kmh) / 2f / 3.6f // m/s
                    distanceM += avgMs * dtSec
                }

                // Captura secundaria 0-60 en pruebas 0-100 / 0-200
                if (secondaryMs == null && (test == TestType.ACCEL_0_100 || test == TestType.ACCEL_0_200) &&
                    p != null && p.kmh < 60f && sample.kmh >= 60f
                ) {
                    secondaryMs = nanosToMs(interpolateTime(p, sample, 60f) - startNanos)
                }

                _ui.value = state.copy(currentSpeedKmh = speedInt, topSpeedKmh = top, distanceM = distanceM)

                if (test.isDistance) {
                    if (distanceM >= test.distanceM) {
                        val timeMs = nanosToMs(sample.timeNanos - startNanos)
                        finish(top, timeMs, "trap: $speedInt km/h")
                    }
                } else {
                    if (p != null && p.kmh < test.endKmh && sample.kmh >= test.endKmh) {
                        val timeMs = nanosToMs(interpolateTime(p, sample, test.endKmh) - startNanos)
                        val note = secondaryMs?.let { "0-60: ${formatMs(it)}" }
                        finish(top, timeMs, note)
                    }
                }
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
                _ui.value = _ui.value.copy(elapsedMs = nanosToMs(System.nanoTime() - startNanos))
                delay(40L)
            }
        }
    }

    private fun finish(top: Int, timeMs: Long, note: String?) {
        collectJob?.cancel()
        tickJob?.cancel()
        val result = DragResult(
            id = System.currentTimeMillis(),
            dateMillis = System.currentTimeMillis(),
            test = _ui.value.test.label,
            timeMs = timeMs,
            topSpeedKmh = top,
            note = note
        )
        _history.value = history.add(result)
        _ui.value = _ui.value.copy(
            phase = MeasurePhase.FINISHED,
            elapsedMs = timeMs,
            resultMs = timeMs,
            note = note,
            topSpeedKmh = top
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

    private fun formatMs(ms: Long): String {
        val s = ms / 1000.0
        val whole = s.toInt()
        val h = ((s - whole) * 100).toInt()
        val hh = if (h < 10) "0$h" else "$h"
        return "$whole.$hh s"
    }

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
