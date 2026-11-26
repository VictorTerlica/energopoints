package com.energypoints.finder.data

import java.util.Locale

/**
 * Проверка списка точек перед сохранением/импортом/экспортом.
 *
 * Возвращает готовые строки для AlertDialog. Проверяются:
 *  - обязательные поля (code, name, district, type);
 *  - допустимый тип (РП/ТП);
 *  - диапазоны координат;
 *  - дубликаты по связке code + district + type.
 */
object PointValidator {

    fun validate(points: List<Point>): List<String> {
        val missingFields = mutableListOf<String>()
        val badCoordinates = mutableListOf<String>()
        val badTypes = mutableListOf<String>()
        val duplicates = mutableListOf<String>()
        val seen = HashMap<String, Point>()

        for (point in points) {
            val label = point.code.trim().ifBlank { "(без кода)" }
            val districtLabel = point.district.trim().ifBlank { "без района" }

            val empty = buildList {
                if (point.code.isBlank()) add("code")
                if (point.name.isBlank()) add("name")
                if (point.district.isBlank()) add("district")
                if (point.type.isBlank()) add("type")
            }
            if (empty.isNotEmpty()) {
                missingFields += "$label ($districtLabel): ${empty.joinToString(", ")}"
            }

            if (point.type !in POINT_TYPES) {
                badTypes += "$label (${point.type.ifBlank { "пусто" }})"
            }

            if (!point.latitude.isFinite() || point.latitude !in -90.0..90.0) {
                badCoordinates += "$label: широта ${point.latitude}"
            }
            if (!point.longitude.isFinite() || point.longitude !in -180.0..180.0) {
                badCoordinates += "$label: долгота ${point.longitude}"
            }

            val key = listOf(point.code, point.district, point.type)
                .joinToString("|") { it.trim().lowercase(Locale.ROOT) }
            if (seen.containsKey(key)) {
                duplicates += "$label ($districtLabel)"
            } else {
                seen[key] = point
            }
        }

        val errors = mutableListOf<String>()
        if (duplicates.isNotEmpty()) {
            errors += "Ошибка: найдены дубликаты: " +
                duplicates.distinct().joinToString(", ")
        }
        if (missingFields.isNotEmpty()) {
            errors += "Ошибка: не заполнены обязательные поля: " +
                missingFields.distinct().joinToString("; ")
        }
        if (badCoordinates.isNotEmpty()) {
            errors += "Ошибка: неверные координаты: " +
                badCoordinates.distinct().joinToString("; ")
        }
        if (badTypes.isNotEmpty()) {
            errors += "Ошибка: неверный тип (нужно РП или ТП): " +
                badTypes.distinct().joinToString(", ")
        }
        return errors
    }
}
