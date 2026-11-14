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
