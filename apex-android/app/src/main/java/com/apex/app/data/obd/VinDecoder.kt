package com.apex.app.data.obd

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Resultado del barrido del vehículo. */
data class VehicleInfo(
    val vin: String?,
    val make: String?,
    val model: String?,
    val year: String?,
    val diesel: Boolean?
)

/**
 * Decodifica el VIN (modo 09 PID 02) y el tipo de combustible (modo 01 PID 51).
 * El VIN se intenta decodificar con la base pública de la NHTSA (requiere
 * internet en el teléfono); si falla, cae a un decodificado básico sin red.
 */
object VinDecoder {

    /** Extrae el VIN (17 caracteres) de la respuesta cruda del modo 09. */
    fun parseVin(raw: String): String? {
        // Quitar marcadores de trama tipo "0:" "1:" y dejar solo hex.
        val noMarkers = raw.replace(Regex("[0-9A-Fa-f]:"), " ")
        val hex = noMarkers.uppercase().replace(Regex("[^0-9A-F]"), "")
        val sb = StringBuilder()
        var i = 0
        while (i + 1 < hex.length) {
            val b = hex.substring(i, i + 2).toIntOrNull(16) ?: break
            if (b in 32..126) sb.append(b.toChar())
            i += 2
        }
        val ascii = sb.toString()
        // Un VIN válido: 17 caracteres alfanuméricos, sin I, O, Q.
        return Regex("[A-HJ-NPR-Z0-9]{17}").find(ascii)?.value
    }

    /** Extrae el tipo de combustible (modo 01 PID 51). true = diésel. */
    fun parseFuelType(raw: String): Boolean? {
        val hex = raw.uppercase().replace(Regex("[^0-9A-F]"), "")
        val idx = hex.indexOf("4151")
        if (idx < 0 || idx + 6 > hex.length) return null
        val code = hex.substring(idx + 4, idx + 6).toIntOrNull(16) ?: return null
        return when (code) {
            4, 5, 6 -> true   // diésel y derivados
            else -> false     // gasolina y otros
        }
    }

    /** Año aproximado a partir del 10º carácter del VIN. */
    fun yearFromVin(vin: String): String? {
        if (vin.length < 10) return null
        val c = vin[9]
        val map = mapOf(
            '1' to "2001", '2' to "2002", '3' to "2003", '4' to "2004", '5' to "2005",
            '6' to "2006", '7' to "2007", '8' to "2008", '9' to "2009",
            'A' to "2010", 'B' to "2011", 'C' to "2012", 'D' to "2013", 'E' to "2014",
            'F' to "2015", 'G' to "2016", 'H' to "2017", 'J' to "2018", 'K' to "2019",
            'L' to "2020", 'M' to "2021", 'N' to "2022", 'P' to "2023", 'R' to "2024",
            'S' to "2025", 'T' to "2026", 'V' to "2027", 'W' to "2028", 'X' to "2029", 'Y' to "2030"
        )
        return map[c]
    }

    /** Marca aproximada por el prefijo WMI (sin red). Tabla corta de los comunes. */
    fun makeFromVin(vin: String): String? {
        if (vin.length < 3) return null
        val wmi = vin.substring(0, 3).uppercase()
        val prefixes = mapOf(
            "KMH" to "Hyundai", "KMhH" to "Hyundai", "KNA" to "Kia", "KNB" to "Kia",
            "KND" to "Kia", "9BF" to "Ford", "MAJ" to "Ford", "JHM" to "Honda",
            "JN1" to "Nissan", "3N1" to "Nissan", "MR0" to "Toyota", "JTD" to "Toyota",
            "WVW" to "Volkswagen", "9BW" to "Volkswagen", "WDB" to "Mercedes-Benz",
            "WBA" to "BMW", "1FA" to "Ford", "1G1" to "Chevrolet", "KL1" to "Chevrolet",
            "9BG" to "Chevrolet", "WAU" to "Audi", "VF1" to "Renault", "93Y" to "Renault",
            "MALA" to "Hyundai", "8AW" to "Volkswagen", "9BD" to "Fiat"
        )
        prefixes[wmi]?.let { return it }
        // Por país (primer carácter) como último recurso.
        return null
    }

    /**
     * Intenta decodificar el VIN con la NHTSA (internet). Devuelve (marca, modelo, año)
     * o null si no hay red o falla. DEBE llamarse fuera del hilo principal.
     */
    fun decodeOnline(vin: String): Triple<String?, String?, String?>? {
        return try {
            val url = URL("https://vpic.nhtsa.dot.gov/api/vehicles/decodevinvalues/$vin?format=json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                requestMethod = "GET"
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val results = JSONObject(text).getJSONArray("Results").getJSONObject(0)
            val make = results.optString("Make", "").takeIf { it.isNotBlank() }
                ?.lowercase()?.replaceFirstChar { it.uppercase() }
            val model = results.optString("Model", "").takeIf { it.isNotBlank() }
            val year = results.optString("ModelYear", "").takeIf { it.isNotBlank() }
            Triple(make, model, year)
        } catch (e: Exception) {
            null
        }
    }
}
