package com.apex.app.data.obd

/** Un código de falla con su texto. */
data class Dtc(val code: String, val description: String)

/**
 * Decodifica la respuesta del modo 03 (códigos de falla almacenados).
 * Convierte los bytes crudos del ELM327 en códigos tipo "P0301".
 */
object DtcDecoder {

    private val letters = arrayOf("P", "C", "B", "U")

    // Diccionario corto de los códigos más comunes. Los demás se muestran
    // con su categoría general (el usuario puede buscarlos en internet).
    private val common = mapOf(
        "P0300" to "Fallo de encendido múltiple",
        "P0301" to "Fallo de encendido - cilindro 1",
        "P0302" to "Fallo de encendido - cilindro 2",
        "P0303" to "Fallo de encendido - cilindro 3",
        "P0304" to "Fallo de encendido - cilindro 4",
        "P0305" to "Fallo de encendido - cilindro 5",
        "P0306" to "Fallo de encendido - cilindro 6",
        "P0171" to "Mezcla demasiado pobre (banco 1)",
        "P0172" to "Mezcla demasiado rica (banco 1)",
        "P0174" to "Mezcla demasiado pobre (banco 2)",
        "P0420" to "Catalizador: bajo rendimiento",
        "P0128" to "Termostato / refrigerante por debajo de lo normal",
        "P0113" to "Sensor de temperatura de aire (IAT) alto",
        "P0101" to "Sensor de flujo de aire (MAF) fuera de rango",
        "P0401" to "Flujo de EGR insuficiente",
        "P0442" to "Fuga pequeña en sistema EVAP",
        "P0455" to "Fuga grande en sistema EVAP",
        "P0135" to "Calentador del sensor de oxígeno (banco 1)",
        "P0700" to "Falla en el control de la transmisión"
    )

    fun decodeMode03(raw: String): List<Dtc> {
        val upper = raw.uppercase()
        if (upper.contains("NO DATA") || upper.contains("UNABLE") || upper.contains("ERROR")) {
            return emptyList()
        }
        val clean = upper.replace(Regex("[^0-9A-F]"), "")
        val idx = clean.indexOf("43")
        if (idx < 0) return emptyList()

        var after = clean.substring(idx + 2)
        // Pasar a bytes.
        val bytes = ArrayList<Int>()
        var i = 0
        while (i + 1 < after.length) {
            val b = after.substring(i, i + 2).toIntOrNull(16) ?: break
            bytes.add(b)
            i += 2
        }
        if (bytes.isEmpty()) return emptyList()

        // Formato CAN: el primer byte tras 43 puede ser la cantidad de códigos
        // (queda un número impar de bytes). Si es así, lo saltamos.
        val start = if (bytes.size % 2 == 1) 1 else 0

        val result = ArrayList<Dtc>()
        var j = start
        while (j + 1 < bytes.size) {
            val a = bytes[j]
            val b = bytes[j + 1]
            j += 2
            if (a == 0 && b == 0) continue // relleno / sin código
            val code = decodeOne(a, b)
            result.add(Dtc(code = code, description = describe(code)))
        }
        return result
    }

    private fun decodeOne(a: Int, b: Int): String {
        val first = letters[(a shr 6) and 0x3]
        val second = ((a shr 4) and 0x3).toString()
        val third = (a and 0x0F).toString(16).uppercase()
        val fourth = ((b shr 4) and 0x0F).toString(16).uppercase()
        val fifth = (b and 0x0F).toString(16).uppercase()
        return "$first$second$third$fourth$fifth"
    }

    private fun describe(code: String): String {
        common[code]?.let { return it }
        return when (code.firstOrNull()) {
            'P' -> "Motor / transmisión (busca el código para el detalle)"
            'C' -> "Chasis (frenos, ABS, dirección)"
            'B' -> "Carrocería (airbags, confort)"
            'U' -> "Red / comunicación entre módulos"
            else -> "Código de diagnóstico"
        }
    }
}
