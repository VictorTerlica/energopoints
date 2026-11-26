package com.energypoints.finder.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale
import java.util.UUID

/** Допустимые типы точек (в JSON — строчными буквами). */
val POINT_TYPES: List<String> = listOf("рп", "тп")

/** Принадлежность, которую можно выбрать в форме. */
val OWNERSHIP_OPTIONS: List<String> = listOf("РОССЕТИ", "ССК")

/**
 * Шесть цветов палитры для иконки точки.
 *
 * Цвет хранится в JSON строкой вида "#1565C0". Если поле отсутствует или
 * повреждено, используется цвет по типу точки.
 */
object PointColors {
    const val BLUE = "#1565C0"
    const val GREEN = "#2E7D32"
    const val ORANGE = "#EF6C00"
    const val PURPLE = "#6A1B9A"
    const val TEAL = "#00838F"
    const val RED = "#C62828"

    val palette: List<String> = listOf(BLUE, GREEN, ORANGE, PURPLE, TEAL, RED)

    fun defaultFor(type: String): String = if (type == "тп") ORANGE else BLUE

    fun isValid(color: String?): Boolean {
        val candidate = color?.trim() ?: return false
        return palette.any { it.equals(candidate, ignoreCase = true) }
    }

    fun normalize(color: String?, fallback: String): String {
        val candidate = color?.trim()
        return if (candidate != null && isValid(candidate)) {
            candidate.uppercase(Locale.ROOT)
        } else {
            fallback
        }
    }
}

/**
 * Одна энергетическая точка.
 *
 * Поля code..iconColor сериализуются в points.json. [id] — transient-идентификатор
 * для LazyColumn и операций редактирования/удаления; в JSON он не попадает.
 */
@Serializable
data class Point(
    val code: String = "",
    val type: String = "",
    val name: String = "",
    val district: String = "",
    @Serializable(with = LenientDoubleSerializer::class)
    val latitude: Double,
    @Serializable(with = LenientDoubleSerializer::class)
    val longitude: Double,
    val note: String? = null,
    val ownership: String? = null,
    val iconColor: String? = null,
    @Transient
    val id: String = UUID.randomUUID().toString()
) {
    val displayName: String
        get() = name.ifBlank { code.ifBlank { "Без названия" } }

    val typeLabel: String
        get() = when (type) {
            "рп" -> "РП"
            "тп" -> "ТП"
            else -> type.uppercase(Locale.ROOT).ifBlank { "—" }
        }

    /** Ключ личной заметки: не путает одинаковые номера в разных районах. */
    val noteKey: String
        get() = "note_${district}_${type}_$code"

    /** Итоговый цвет иконки: сохранённый из палитры или цвет по типу. */
    val resolvedColor: String
        get() = PointColors.normalize(iconColor, PointColors.defaultFor(type))
}

/** Координата в едином формате для полей ввода и карточек. */
fun formatCoordinate(value: Double): String =
    String.format(Locale.US, "%.6f", value)

/**
 * Терпимый сериализатор координат: принимает число, строку с точкой или запятой.
 * При некорректном значении бросает SerializationException — репозиторий
 * превращает это в понятную ошибку импорта.
 */
object LenientDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            "com.energypoints.finder.data.LenientDouble",
            PrimitiveKind.DOUBLE
        )

    override fun deserialize(decoder: Decoder): Double {
        if (decoder !is JsonDecoder) {
            return decoder.decodeDouble()
        }
        val element = decoder.decodeJsonElement()
        val raw = (element as? JsonPrimitive)?.contentOrNull
            ?: throw SerializationException("Ожидалось число, получено: $element")
        val normalized = raw
            .replace('\u00A0', ' ')
            .trim()
            .replace(',', '.')
        return normalized.toDoubleOrNull()
            ?: throw SerializationException("Координата не является числом: «$raw»")
    }

    override fun serialize(encoder: Encoder, value: Double) {
        encoder.encodeDouble(value)
    }
}
