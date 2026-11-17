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
                        rpmBands = bands
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
            t.route.forEach { p ->
                routeArr.put(JSONArray().put(p.lat).put(p.lon))
            }
            o.put("route", routeArr)
            val bandsArr = JSONArray()
            t.rpmBands.forEach { b ->
                bandsArr.put(JSONArray().put(b.rpm).put(b.lPerH))
            }
            o.put("rpmBands", bandsArr)
            array.put(o)
        }
        runCatching { file.writeText(array.toString()) }
    }

    fun add(trip: Trip): List<Trip> {
        val updated = (load() + trip).sortedByDescending { it.dateMillis }
        save(updated)
        return updated
    }

    fun clear() {
        runCatching { if (file.exists()) file.delete() }
    }
}
