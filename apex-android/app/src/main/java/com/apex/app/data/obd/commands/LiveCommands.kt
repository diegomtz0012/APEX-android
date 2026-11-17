package com.apex.app.data.obd.commands

import com.apex.app.core.Constants
import com.apex.app.data.obd.ObdCommand

/**
 * PID 0C — RPM.  Fórmula: (A * 256 + B) / 4
 */
class RpmCommand : ObdCommand<Int>(
    mode = Constants.ObdMode.LIVE_DATA, pid = "0C", name = "RPM", unit = "rpm"
) {
    override fun decode(bytes: List<Int>): Int = (bytes[0] * 256 + bytes[1]) / 4
}

/**
 * PID 0D — Velocidad.  Fórmula: A  (km/h)
 */
class SpeedCommand : ObdCommand<Int>(
    mode = Constants.ObdMode.LIVE_DATA, pid = "0D", name = "Velocidad", unit = "km/h"
) {
    override fun decode(bytes: List<Int>): Int = bytes[0]
}

/**
 * PID 05 — Temperatura de refrigerante.  Fórmula: A - 40  (°C)
 */
class CoolantCommand : ObdCommand<Int>(
    mode = Constants.ObdMode.LIVE_DATA, pid = "05", name = "Refrigerante", unit = "°C"
) {
    override fun decode(bytes: List<Int>): Int = bytes[0] - 40
}

/**
 * PID 10 — Flujo másico de aire (MAF).  Fórmula: (A * 256 + B) / 100  (g/s)
 * Se usa para estimar el consumo de combustible durante un viaje.
 */
class MafCommand : ObdCommand<Float>(
    mode = Constants.ObdMode.LIVE_DATA, pid = "10", name = "MAF", unit = "g/s"
) {
    override fun decode(bytes: List<Int>): Float = (bytes[0] * 256 + bytes[1]) / 100f
}

/** PID 11 — Posición del acelerador.  A * 100 / 255  (%) */
class ThrottleCommand : ObdCommand<Int>(
    mode = Constants.ObdMode.LIVE_DATA, pid = "11", name = "Acelerador", unit = "%"
) {
    override fun decode(bytes: List<Int>): Int = bytes[0] * 100 / 255
}

/** PID 04 — Carga calculada del motor.  A * 100 / 255  (%) */
class EngineLoadCommand : ObdCommand<Int>(
    mode = Constants.ObdMode.LIVE_DATA, pid = "04", name = "Carga", unit = "%"
) {
    override fun decode(bytes: List<Int>): Int = bytes[0] * 100 / 255
}

/** PID 0B — Presión del múltiple de admisión (MAP).  A  (kPa) */
class MapCommand : ObdCommand<Int>(
    mode = Constants.ObdMode.LIVE_DATA, pid = "0B", name = "MAP", unit = "kPa"
) {
    override fun decode(bytes: List<Int>): Int = bytes[0]
}

/** PID 0F — Temperatura del aire de admisión.  A - 40  (°C) */
class IntakeTempCommand : ObdCommand<Int>(
    mode = Constants.ObdMode.LIVE_DATA, pid = "0F", name = "Admisión", unit = "°C"
) {
    override fun decode(bytes: List<Int>): Int = bytes[0] - 40
}

/** PID 0E — Avance de chispa.  (A - 128) / 2  (°) */
class TimingAdvanceCommand : ObdCommand<Int>(
    mode = Constants.ObdMode.LIVE_DATA, pid = "0E", name = "Avance", unit = "°"
) {
    override fun decode(bytes: List<Int>): Int = (bytes[0] - 128) / 2
}

/** PID 42 — Voltaje del módulo de control.  (A*256 + B) / 1000  (V) */
class VoltageCommand : ObdCommand<Float>(
    mode = Constants.ObdMode.LIVE_DATA, pid = "42", name = "Voltaje", unit = "V"
) {
    override fun decode(bytes: List<Int>): Float = (bytes[0] * 256 + bytes[1]) / 1000f
}

/**
 * PID 5E — Caudal de combustible del motor.  (A*256 + B) / 20  (litros/hora)
 * El método MÁS preciso para el consumo (gasolina y diésel), si el carro lo da.
 */
class FuelRateCommand : ObdCommand<Float>(
    mode = Constants.ObdMode.LIVE_DATA, pid = "5E", name = "Caudal", unit = "L/h"
) {
    override fun decode(bytes: List<Int>): Float = (bytes[0] * 256 + bytes[1]) / 20f
}

/** PID 06 — Ajuste de mezcla a corto plazo (banco 1).  (A - 128) * 100 / 128  (%) */
class ShortFuelTrimCommand : ObdCommand<Int>(
    mode = Constants.ObdMode.LIVE_DATA, pid = "06", name = "STFT", unit = "%"
) {
    override fun decode(bytes: List<Int>): Int = (bytes[0] - 128) * 100 / 128
}

/** PID 07 — Ajuste de mezcla a largo plazo (banco 1).  (A - 128) * 100 / 128  (%) */
class LongFuelTrimCommand : ObdCommand<Int>(
    mode = Constants.ObdMode.LIVE_DATA, pid = "07", name = "LTFT", unit = "%"
) {
    override fun decode(bytes: List<Int>): Int = (bytes[0] - 128) * 100 / 128
}
