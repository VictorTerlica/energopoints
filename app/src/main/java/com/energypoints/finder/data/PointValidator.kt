package com.energypoints.finder.data

import java.util.Locale

/**
 * Информационная проверка списка точек.
 *
 * Ничего не блокирует: результат используется только для баннера/уведомления
 * «Замечания: N», по нажатию на который открывается список.
 *
 * Дубликат — это полное совпадение code + district + type + name.
 * Одинаковый номер с разными названиями дубликатом не считается.
 * ПС — равноправный тип объекта наряду с РП и ТП.
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

            // Название входит в ключ: одинаковый номер с разным именем — не дубликат.
            val key = listOf(point.code, point.district, point.type, point.name)
                .joinToString("|") { it.trim().lowercase(Locale.ROOT) }
            if (seen.containsKey(key)) {
                duplicates += "$label «${point.name.ifBlank { "без названия" }}»"
            } else {
                seen[key] = point
            }
        }

        val issues = mutableListOf<String>()
        if (duplicates.isNotEmpty()) {
            issues += "Дубликаты: " + duplicates.distinct().joinToString(", ")
        }
        if (missingFields.isNotEmpty()) {
            issues += "Не заполнены обязательные поля: " +
                missingFields.distinct().joinToString("; ")
        }
        if (badCoordinates.isNotEmpty()) {
            issues += "Неверные координаты: " +
                badCoordinates.distinct().joinToString("; ")
        }
        if (badTypes.isNotEmpty()) {
            issues += "Неизвестный тип (нужно РП, ТП или ПС): " +
                badTypes.distinct().joinToString(", ")
        }
        return issues
    }
}
