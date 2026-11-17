package com.apex.app.data.trip

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Guarda y lee los viajes en un archivo JSON local. */
class TripRepository(context: Context) {

    private val file: File = File(context.filesDir, "trips.json")

    fun load(): List<Trip> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            val list = ArrayList<Trip>(array.length())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val routeArr = o.optJSONArray("route") ?: JSONArray()
                val route = ArrayList<LatLng>(routeArr.length())
                for (j in 0 until routeArr.length()) {
                    val p = routeArr.getJSONArray(j)
                    route.add(LatLng(p.getDouble(0), p.getDouble(1)))
                }
                val bandsArr = o.optJSONArray("rpmBands") ?: JSONArray()
                val bands = ArrayList<RpmBand>(bandsArr.length())
                for (k in 0 until bandsArr.length()) {
                    val b = bandsArr.getJSONArray(k)
                    bands.add(RpmBand(b.getInt(0), b.getDouble(1)))
                }
                val flagsArr = o.optJSONArray("routeFlags") ?: JSONArray()
                val flags = ArrayList<Int>(flagsArr.length())
                for (f in 0 until flagsArr.length()) flags.add(flagsArr.getInt(f))
                val anomArr = o.optJSONArray("anomalies") ?: JSONArray()
                val anomalies = ArrayList<Anomaly>(anomArr.length())
                for (a in 0 until anomArr.length()) {
                    val ao = anomArr.getJSONObject(a)
                    anomalies.add(
                        Anomaly(
                            lat = ao.getDouble("lat"),
                            lon = ao.getDouble("lon"),
                            type = ao.optString("type", ""),
                            label = ao.optString("label", ""),
                            timeMillis = ao.optLong("timeMillis", 0L),
                            address = ao.optString("address", "")
                        )
                    )
                }
                list.add(
                    Trip(
                        id = o.getLong("id"),
                        dateMillis = o.getLong("dateMillis"),
                        distanceKm = o.getDouble("distanceKm"),
                        durationMs = o.getLong("durationMs"),
                        avgKmh = o.getDouble("avgKmh"),
                        maxKmh = o.getInt("maxKmh"),
                        fuelLiters = if (o.isNull("fuelLiters")) null else o.getDouble("fuelLiters"),
                        kmPerL = if (o.isNull("kmPerL")) null else o.getDouble("kmPerL"),
                        route = route,
                        rpmBands = bands,
                        mode = o.optString("mode", ""),
                        coolantMax = if (o.isNull("coolantMax")) null else o.optInt("coolantMax"),
                        coolantMin = if (o.isNull("coolantMin")) null else o.optInt("coolantMin"),
                        oilMax = if (o.isNull("oilMax")) null else o.optInt("oilMax"),
                        oilMin = if (o.isNull("oilMin")) null else o.optInt("oilMin"),
                        fuelPressureMin = if (o.isNull("fuelPressureMin")) null else o.optInt("fuelPressureMin"),
                        fuelPressureAvg = if (o.isNull("fuelPressureAvg")) null else o.optInt("fuelPressureAvg"),
                        idleMs = o.optLong("idleMs", 0L),
                        stops = o.optInt("stops", 0),
                        hardAccel = o.optInt("hardAccel", 0),
                        hardBrake = o.optInt("hardBrake", 0),
                        climbM = o.optInt("climbM", 0),
                        dropM = o.optInt("dropM", 0),
                        avgRpm = o.optInt("avgRpm", 0),
                        ecoScore = o.optInt("ecoScore", 0),
                        analysis = o.optString("analysis", ""),
                        routeFlags = flags,
                        anomalies = anomalies
                    )
                )
            }
            list.sortedByDescending { it.dateMillis }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(trips: List<Trip>) {
        val array = JSONArray()
        trips.forEach { t ->
            val o = JSONObject()
            o.put("id", t.id)
            o.put("dateMillis", t.dateMillis)
            o.put("distanceKm", t.distanceKm)
            o.put("durationMs", t.durationMs)
            o.put("avgKmh", t.avgKmh)
            o.put("maxKmh", t.maxKmh)
            o.put("fuelLiters", t.fuelLiters ?: JSONObject.NULL)
            o.put("kmPerL", t.kmPerL ?: JSONObject.NULL)
            val routeArr = JSONArray()
            t.route.forEach { p -> routeArr.put(JSONArray().put(p.lat).put(p.lon)) }
            o.put("route", routeArr)
            val bandsArr = JSONArray()
            t.rpmBands.forEach { b -> bandsArr.put(JSONArray().put(b.rpm).put(b.lPerH)) }
            o.put("rpmBands", bandsArr)
            o.put("mode", t.mode)
            o.put("coolantMax", t.coolantMax ?: JSONObject.NULL)
            o.put("coolantMin", t.coolantMin ?: JSONObject.NULL)
            o.put("oilMax", t.oilMax ?: JSONObject.NULL)
            o.put("oilMin", t.oilMin ?: JSONObject.NULL)
            o.put("fuelPressureMin", t.fuelPressureMin ?: JSONObject.NULL)
            o.put("fuelPressureAvg", t.fuelPressureAvg ?: JSONObject.NULL)
            o.put("idleMs", t.idleMs)
            o.put("stops", t.stops)
            o.put("hardAccel", t.hardAccel)
            o.put("hardBrake", t.hardBrake)
            o.put("climbM", t.climbM)
            o.put("dropM", t.dropM)
            o.put("avgRpm", t.avgRpm)
            o.put("ecoScore", t.ecoScore)
            o.put("analysis", t.analysis)
            val flagsArr = JSONArray()
            t.routeFlags.forEach { flagsArr.put(it) }
            o.put("routeFlags", flagsArr)
            val anomArr = JSONArray()
            t.anomalies.forEach { a ->
                anomArr.put(
                    JSONObject()
                        .put("lat", a.lat).put("lon", a.lon)
                        .put("type", a.type).put("label", a.label)
                        .put("timeMillis", a.timeMillis).put("address", a.address)
                )
            }
            o.put("anomalies", anomArr)
            array.put(o)
        }
        runCatching { file.writeText(array.toString()) }
    }

    fun add(trip: Trip): List<Trip> {
        val updated = (load() + trip).sortedByDescending { it.dateMillis }
        save(updated)
        return updated
    }

    /** Reemplaza un viaje por su versión actualizada (ej. con direcciones del Excel). */
    fun update(trip: Trip): List<Trip> {
        val updated = load().map { if (it.id == trip.id) trip else it }
            .sortedByDescending { it.dateMillis }
        save(updated)
        return updated
    }

    fun clear() {
        runCatching { if (file.exists()) file.delete() }
    }
}
