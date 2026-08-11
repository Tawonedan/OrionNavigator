package com.orion.app.ar.waypoints

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Waypoint file persistence only — not anchors, not navigation graph. */
object WaypointPersistence {
    private const val TAG = "WaypointPersistence"
    private const val FILE_NAME = "waypoints.json"

    fun save(context: Context, waypoints: List<Waypoint>) {
        val array = JSONArray()
        for (wp in waypoints) {
            array.put(
                JSONObject().apply {
                    put("id", wp.id)
                    put("name", wp.name)
                    put("type", wp.type.name)
                    put("backendAnchorId", wp.backendAnchorId ?: JSONObject.NULL)
                    put("cloudAnchorId", wp.backendAnchorId ?: JSONObject.NULL)
                    put("floor", wp.floor)
                    put(
                        "metadata",
                        JSONObject().also { meta ->
                            wp.metadata.forEach { (k, v) -> meta.put(k, v) }
                        },
                    )
                },
            )
        }
        writeAtomic(context, FILE_NAME, array.toString(2))
        Log.i(TAG, "Saved ${waypoints.size} waypoint(s)")
    }

    fun load(context: Context): List<Waypoint> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val backendId =
                        obj.optString("backendAnchorId", "").ifBlank {
                            obj.optString("cloudAnchorId", "")
                        }.ifBlank { null }
                    val metaObj = obj.optJSONObject("metadata")
                    val metadata = mutableMapOf<String, String>()
                    if (metaObj != null) {
                        metaObj.keys().forEach { key -> metadata[key] = metaObj.getString(key) }
                    }
                    add(
                        Waypoint(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            type =
                                runCatching { WaypointType.valueOf(obj.getString("type")) }
                                    .getOrDefault(WaypointType.OTHER),
                            backendAnchorId = backendId,
                            floor = obj.optInt("floor", 0),
                            metadata = metadata,
                        ),
                    )
                }
            }.also { Log.i(TAG, "Loaded ${it.size} waypoint(s)") }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load waypoints", e)
            emptyList()
        }
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }

    private fun writeAtomic(context: Context, name: String, text: String) {
        val dir = context.filesDir
        val target = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }
}
