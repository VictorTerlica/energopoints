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

/**
 * Одна энергетическая точка (РП или ТП).
 *
 * Поля соответствуют формату points.json:
 * {"code","type","name","district","latitude","longitude","note","ownership"}.
 *
 * [districtKey] — ключ района из корня JSON. Он не сериализуется, а проставляется
 * репозиторием после разбора, потому что именно ключ района (а не поле district
 * внутри точки) образует уникальный ключ заметки.
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
    @Transient
    val districtKey: String = ""
) {
    /** Уникальный идентификатор внутри загруженного файла. */
    val id: String
        get() = "$districtKey|$type|$code"

    /**
     * Ключ заметки в DataStore.
     * Формат: note_<район>_<тип>_<код>, чтобы одинаковые номера ТП в разных
     * районах не перезаписывали заметки друг друга.
     */
    val noteKey: String
        get() = "note_${districtKey}_${type}_$code"

    val displayName: String
        get() = name.ifBlank { code }

    val typeLabel: String
        get() = when (type.lowercase(Locale.ROOT)) {
            "рп" -> "РП"
            "тп" -> "ТП"
            else -> type.uppercase(Locale.ROOT)
        }

    val isRp: Boolean
        get() = type == "рп"

    val hasValidCoordinates: Boolean
        get() = latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}

/**
 * Терпимый к формату сериализатор координат.
 *
 * Принимает числа, строки с числом ("53.1959"), строки с запятой ("53,1959")
 * и неразрывными пробелами. Если значение не является числом — бросает
 * SerializationException, репозиторий логирует точку и пропускает её.
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
