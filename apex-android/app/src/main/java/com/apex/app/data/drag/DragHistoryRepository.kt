package com.apex.app.data.drag

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Guarda y lee el historial de mediciones en un archivo JSON local. */
class DragHistoryRepository(context: Context) {

    private val file: File = File(context.filesDir, "drag_history.json")

    fun load(): List<DragResult> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            val list = ArrayList<DragResult>(array.length())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                list.add(
                    DragResult(
                        id = o.getLong("id"),
                        dateMillis = o.getLong("dateMillis"),
                        test = o.optString("test", "0-100 km/h"),
                        timeMs = if (o.isNull("timeMs")) null else o.getLong("timeMs"),
                        topSpeedKmh = o.getInt("top"),
                        note = if (o.isNull("note")) null else o.optString("note", null)
                    )
                )
            }
            list.sortedByDescending { it.dateMillis }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(results: List<DragResult>) {
        val array = JSONArray()
        results.forEach { r ->
            val o = JSONObject()
            o.put("id", r.id)
            o.put("dateMillis", r.dateMillis)
            o.put("test", r.test)
            o.put("timeMs", r.timeMs ?: JSONObject.NULL)
            o.put("top", r.topSpeedKmh)
            o.put("note", r.note ?: JSONObject.NULL)
            array.put(o)
        }
        runCatching { file.writeText(array.toString()) }
    }

    fun add(result: DragResult): List<DragResult> {
        val updated = (load() + result).sortedByDescending { it.dateMillis }
        save(updated)
        return updated
    }

    fun clear() {
        runCatching { if (file.exists()) file.delete() }
    }
}
