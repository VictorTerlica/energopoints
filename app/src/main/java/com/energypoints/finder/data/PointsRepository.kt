package com.example.energopoints.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File

object PointsRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }

    fun load(context: Context): Map<String, Map<String, List<Point>>> {
        val file = File(context.getExternalFilesDir(null), "points.json")
        val text = if (file.exists()) {
            file.readText()
        } else {
            // Если файла нет, пробуем загрузить из assets (если вы его туда положили)
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

    // Новая функция сохранения
    fun save(context: Context, data: Map<String, Map<String, List<Point>>>) {
        // Преобразуем обратно в структуру JSON
        val rootJson = JsonObject(data.mapValues { (_, types) ->
            JsonObject(types.mapValues { (type, points) ->
                val key = if (type == "РП") "рп" else "тп"
                json.encodeToJsonElement(points).jsonArray
            })
        })
        
        val file = File(context.getExternalFilesDir(null), "points.json")
        file.writeText(rootJson.toString())
    }
}
