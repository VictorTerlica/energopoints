package com.energypoints.finder.data

object PointValidator {

    // Возвращает список ошибок. Если пусто — всё ок.
    fun validate(points: List<Point>): List<String> {
        val errors = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        points.forEach { p ->
            if (p.code.isBlank()) errors.add("Пустой код у точки: ${p.name}")
            if (p.name.isBlank()) errors.add("Пустое имя у точки с кодом: ${p.code}")
            if (p.district.isBlank()) errors.add("Пустой район у точки: ${p.code}")
            if (p.type.isBlank()) errors.add("Пустой тип у точки: ${p.code}")
            if (p.latitude !in -90.0..90.0) errors.add("Широта вне диапазона у ${p.code}: ${p.latitude}")
            if (p.longitude !in -180.0..180.0) errors.add("Долгота вне диапазона у ${p.code}: ${p.longitude}")

            val key = "${p.code.trim().lowercase()}_${p.district.trim().lowercase()}_${p.type.trim().lowercase()}"
            if (seen.contains(key)) {
                errors.add("Дубликат: ${p.code} (${p.district}, ${p.type})")
            } else {
                seen.add(key)
            }
        }
        return errors
    }

    // Мягкая проверка: только для отображения, не блокирует импорт
    fun softValidate(points: List<Point>): List<String> {
        val errors = mutableListOf<String>()
        points.forEach { p ->
            if (p.latitude !in -90.0..90.0) errors.add("Широта вне диапазона у ${p.code}")
            if (p.longitude !in -180.0..180.0) errors.add("Долгота вне диапазона у ${p.code}")
        }
        return errors
    }
}
