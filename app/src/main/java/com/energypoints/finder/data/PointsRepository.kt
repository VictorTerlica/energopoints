package com.example.energopoints.data

import android.content.Context
import kotlinx.serialization.json.*
import java.io.File

object PointsRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }

    fun load(context: Context): Map<String, Map<String, List<Point>>> {
        val file = File(context.getExternalFilesDir(null), "points.json")
        val text = if (file.exists()) {
            file.readText()
        } else {
            try { context.assets.open("points.json").bufferedReader().use { it.readText() } } 
            catch (e: Exception) { "{}" }
        }

        if (text.isBlank()) return emptyMap()

        val root = json.parseToJsonElement(text).jsonObject
        val result = LinkedHashMap<String, Map<String, List<Point>>>()

        for ((district, districtValue) in root) {
            val districtObj = districtValue.jsonObject
            val byType = LinkedHashMap<String, List<Point>>()

            for ((typeKey, typeValue) in districtObj) {
                val type = when (typeKey.lowercase()) {
                    "рп" -> "РП"
                    "тп" -> "ТП"
                    else -> typeKey.uppercase()
                }
                val list = typeValue.jsonArray.map { elem ->
                    json.decodeFromJsonElement(Point.serializer(), elem)
                }
                val sorted = list.sortedBy { it.code.toIntOrNull() ?: Int.MAX_VALUE }
                byType[type] = sorted
            }
            result[district] = byType
        }
        return result
    }

    fun save(context: Context, data: Map<String, Map<String, List<Point>>>) {
        val rootJson = buildJsonObject {
            data.forEach { (district, types) ->
                put(district, buildJsonObject {
                    types.forEach { (type, points) ->
                        val key = if (type == "РП") "рп" else "тп"
                        put(key, buildJsonArray {
                            points.forEach { point ->
                                add(json.encodeToJsonElement(Point.serializer(), point))
                            }
                        })
                    }
                })
            }
        }
        
        val file = File(context.getExternalFilesDir(null), "points.json")
        file.writeText(rootJson.toString())
    }
}
