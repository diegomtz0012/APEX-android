package com.apex.app.data.vehicle

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Guarda el perfil del vehículo en un JSON local (un solo objeto). */
class VehicleProfileRepository(context: Context) {

    private val file: File = File(context.filesDir, "vehicle_profile.json")

    fun load(): VehicleProfile {
        if (!file.exists()) return VehicleProfile()
        return try {
            val o = JSONObject(file.readText())
            VehicleProfile(
                nickname = o.optString("nickname", ""),
                make = o.optString("make", ""),
                model = o.optString("model", ""),
                year = o.optString("year", ""),
                weightKg = o.optString("weightKg", ""),
                diesel = o.optBoolean("diesel", false),
                vin = o.optString("vin", ""),
                displacementL = o.optString("displacementL", "")
            )
        } catch (e: Exception) {
            VehicleProfile()
        }
    }

    fun save(profile: VehicleProfile) {
        val o = JSONObject()
        o.put("nickname", profile.nickname)
        o.put("make", profile.make)
        o.put("model", profile.model)
        o.put("year", profile.year)
        o.put("weightKg", profile.weightKg)
        o.put("diesel", profile.diesel)
        o.put("vin", profile.vin)
        o.put("displacementL", profile.displacementL)
        runCatching { file.writeText(o.toString()) }
    }
}
