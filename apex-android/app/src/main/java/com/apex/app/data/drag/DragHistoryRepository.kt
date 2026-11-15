package com.apex.app.data.drag

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Guarda y lee el historial de piques en un archivo JSON local
 * (en el almacenamiento interno de la app). Sin base de datos ni
 * dependencias externas: simple y confiable.
 */
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
                        time0to60Ms = if (o.isNull("t60")) null else o.getLong("t60"),
                        time0to100Ms = if (o.isNull("t100")) null else o.getLong("t100"),
                        topSpeedKmh = o.getInt("top")
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
            o.put("t60", r.time0to60Ms ?: JSONObject.NULL)
            o.put("t100", r.time0to100Ms ?: JSONObject.NULL)
            o.put("top", r.topSpeedKmh)
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
