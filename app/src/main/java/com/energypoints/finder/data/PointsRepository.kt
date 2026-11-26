package com.energypoints.finder.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File
import java.util.Locale

const val POINTS_FILE_NAME = "points.json"
const val EXAMPLE_FILE_NAME = "points.example.json"

enum class PointsSource(val label: String) {
    EXTERNAL("внешний файл"),
    ASSETS("assets/points.json"),
    DEMO("демо-пример")
}

/** Результат разбора без учёта источника. */
data class ParsedPoints(
    val points: List<Point>,
    val issues: List<String>
)

sealed interface LoadOutcome {
    data class Success(
        val points: List<Point>,
        val source: PointsSource,
        val issues: List<String>
    ) : LoadOutcome

    data class Missing(
        val expectedPath: String,
        val demoAvailable: Boolean
    ) : LoadOutcome

    data class Error(val message: String) : LoadOutcome
}

/**
 * Чтение, запись, импорт и экспорт points.json.
 *
 * Формат остаётся прежним: { "Район": { "рп": [...], "тп": [...] } }.
 * Редактирование всегда сохраняется во внешнюю приватную папку приложения;
 * assets/points.json используется только как источник при первом запуске.
 *
 * Импорт/экспорт выполняются через SAF (ContentResolver) и не требуют
 * разрешений на чтение/запись внешнего хранилища.
 */
@OptIn(ExperimentalSerializationApi::class)
class PointsRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
    }

    fun dataDirectory(): File =
        context.getExternalFilesDir(null) ?: File(context.filesDir, "points_data")

    fun prepareDataDirectory(): File {
        val directory = dataDirectory()
        if (!directory.exists() && !directory.mkdirs()) {
            Log.w(TAG, "Не удалось создать папку данных: ${directory.absolutePath}")
        }
        return directory
    }

    fun expectedFilePath(): String =
        File(dataDirectory(), POINTS_FILE_NAME).absolutePath

    suspend fun load(): LoadOutcome = withContext(Dispatchers.IO) {
        val file = File(prepareDataDirectory(), POINTS_FILE_NAME)
        try {
            when {
                file.isFile -> parseToOutcome(
                    raw = file.readText(Charsets.UTF_8),
                    source = PointsSource.EXTERNAL,
                    path = file.absolutePath
                )

                assetExists(POINTS_FILE_NAME) -> parseToOutcome(
                    raw = readAsset(POINTS_FILE_NAME),
                    source = PointsSource.ASSETS,
                    path = "assets/$POINTS_FILE_NAME"
                )

                else -> LoadOutcome.Missing(
                    expectedPath = file.absolutePath,
                    demoAvailable = assetExists(EXAMPLE_FILE_NAME)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось загрузить points.json", e)
            LoadOutcome.Error(
                "Не удалось прочитать файл: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    suspend fun loadDemo(): LoadOutcome = withContext(Dispatchers.IO) {
        try {
            if (!assetExists(EXAMPLE_FILE_NAME)) {
                return@withContext LoadOutcome.Error(
                    "Демо-файл assets/$EXAMPLE_FILE_NAME не найден."
                )
            }
            parseToOutcome(
                raw = readAsset(EXAMPLE_FILE_NAME),
                source = PointsSource.DEMO,
                path = "assets/$EXAMPLE_FILE_NAME"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось прочитать демо-файл", e)
            LoadOutcome.Error(
                "Не удалось прочитать демо-файл: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    /** Читает и разбирает JSON, выбранный через OpenDocument. */
    suspend fun readPoints(uri: Uri): Result<ParsedPoints> = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Не удалось открыть выбранный файл.")
            val raw = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            Result.success(parse(raw))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось импортировать points.json", e)
            Result.failure(e)
        }
    }

    /** Атомарно сохраняет точки во внешнюю приватную папку приложения. */
    suspend fun save(points: List<Point>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val text = serialize(points)
            val directory = prepareDataDirectory()
            val destination = File(directory, POINTS_FILE_NAME)
            val backup = File(directory, "$POINTS_FILE_NAME.bak")
            val temporary = File(directory, "$POINTS_FILE_NAME.tmp")
            try {
                // Одна резервная копия предыдущего файла на случай неудачного импорта/правки.
                if (destination.isFile) {
                    runCatching {
                        destination.copyTo(backup, overwrite = true)
                    }
                }
                temporary.writeText(text, Charsets.UTF_8)
                temporary.copyTo(destination, overwrite = true)
            } finally {
                if (temporary.exists()) {
                    temporary.delete()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось сохранить points.json", e)
            Result.failure(e)
        }
    }

    /** Экспортирует текущие точки в URI, выбранный через CreateDocument. */
    suspend fun export(points: List<Point>, uri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val text = serialize(points)
                val output = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IllegalStateException("Не удалось открыть файл для записи.")
                output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось экспортировать points.json", e)
                Result.failure(e)
            }
        }

    fun serialize(points: List<Point>): String {
        require(points.all { it.type in POINT_TYPES }) {
            "Нельзя сохранить точку с неизвестным типом."
        }
        require(points.all { it.district.isNotBlank() }) {
            "Нельзя сохранить точку без района."
        }

        val grouped = LinkedHashMap<String, MutableList<Point>>()
        for (point in points) {
            grouped.getOrPut(point.district) { mutableListOf() }.add(point)
        }

        val root = LinkedHashMap<String, DistrictBlock>()
        for ((district, districtPoints) in grouped) {
            root[district] = DistrictBlock(
                rp = districtPoints.filter { it.type == "рп" },
                tp = districtPoints.filter { it.type == "тп" }
            )
        }
        return json.encodeToString<Map<String, DistrictBlock>>(root)
    }

    private fun parseToOutcome(
        raw: String,
        source: PointsSource,
        path: String
    ): LoadOutcome {
        return try {
            val parsed = parse(raw)
            LoadOutcome.Success(
                points = parsed.points,
                source = source,
                issues = parsed.issues
            )
        } catch (e: PointsParseException) {
            Log.e(TAG, "Некорректный JSON в $path", e)
            LoadOutcome.Error("Файл $path: ${e.message}")
        }
    }

    private fun parse(raw: String): ParsedPoints {
        val cleaned = raw.removePrefix("\uFEFF").trim()
        if (cleaned.isEmpty()) {
            throw PointsParseException("Файл пустой.")
        }

        val rootElement = try {
            json.parseToJsonElement(cleaned)
        } catch (e: SerializationException) {
            throw PointsParseException(
                "JSON синтаксически некорректен: ${e.message}",
                e
            )
        } catch (e: IllegalArgumentException) {
            throw PointsParseException(
                "JSON синтаксически некорректен: ${e.message}",
                e
            )
        }

        val root = rootElement as? JsonObject
            ?: throw PointsParseException(
                "Корень JSON должен быть объектом вида " +
                    "{ \"Район\": { \"рп\": [ ... ], \"тп\": [ ... ] } }."
            )

        val points = mutableListOf<Point>()
        val issues = mutableListOf<String>()

        for ((rawDistrict, element) in root) {
            val district = rawDistrict.trim()
            if (district.isEmpty()) {
                issues += "Пропущен район с пустым именем."
                continue
            }
            val districtObject = element as? JsonObject
            if (districtObject == null) {
                issues += "Район «$district»: значение должно быть объектом."
                continue
            }

            val rpElement = districtObject.arrayFor("рп", "rp")
            val tpElement = districtObject.arrayFor("тп", "tp")
            if (rpElement == null && tpElement == null) {
                issues += "Район «$district»: нет массивов «рп» или «тп»."
                continue
            }

            decodeArray(rpElement, "рп", district, points, issues)
            decodeArray(tpElement, "тп", district, points, issues)
        }

        return ParsedPoints(points = points, issues = issues)
    }

    private fun decodeArray(
        element: JsonElement?,
        fallbackType: String,
        district: String,
        target: MutableList<Point>,
        issues: MutableList<String>
    ) {
        if (element == null || element is JsonNull) return
        val array = element as? JsonArray
        if (array == null) {
            issues += "Район «$district»: поле «$fallbackType» должно быть массивом."
            return
        }

        for (item in array) {
            val rawCode = ((item as? JsonObject)?.get("code") as? JsonPrimitive)
                ?.contentOrNull
                ?.trim()
                .orEmpty()
            val label = rawCode.ifBlank { "(без кода)" }
            try {
                val decoded = json.decodeFromJsonElement<Point>(item)
                val type = normalizeType(decoded.type.ifBlank { fallbackType })
                target += decoded.copy(
                    code = decoded.code.trim(),
                    name = decoded.name.trim(),
                    district = district,
                    type = type,
                    note = decoded.note?.trim()?.takeIf { it.isNotEmpty() },
                    ownership = normalizeOwnership(decoded.ownership),
                    iconColor = PointColors.normalize(
                        decoded.iconColor,
                        PointColors.defaultFor(type)
                    )
                )
            } catch (e: Exception) {
                issues += "Район «$district»: точка $label — ${describeDecodeError(e)}"
            }
        }
    }

    private fun JsonObject.arrayFor(vararg names: String): JsonElement? {
        for ((key, value) in this) {
            if (names.any { it.equals(key.trim(), ignoreCase = true) }) {
                return value
            }
        }
        return null
    }

    private fun normalizeType(value: String): String =
        when (value.trim().lowercase(Locale.ROOT)) {
            "рп", "rp" -> "рп"
            "тп", "tp" -> "тп"
            else -> value.trim().lowercase(Locale.ROOT)
        }

    private fun normalizeOwnership(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val upper = trimmed.uppercase(Locale.ROOT)
        return when {
            upper.contains("РОС") || upper.contains("ROSSETI") -> "РОССЕТИ"
            upper.contains("ССК") || upper.contains("SSK") -> "ССК"
            else -> trimmed
        }
    }

    private fun describeDecodeError(error: Exception): String {
        return when (error) {
            is MissingFieldException -> "не заполнены координаты"
            is SerializationException -> error.message ?: "некорректные данные"
            else -> error.message ?: "некорректные данные"
        }
    }

    private fun assetExists(name: String): Boolean =
        runCatching { context.assets.open(name).close() }.isSuccess

    private fun readAsset(name: String): String =
        context.assets.open(name)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    @Serializable
    private data class DistrictBlock(
        @SerialName("рп") val rp: List<Point> = emptyList(),
        @SerialName("тп") val tp: List<Point> = emptyList()
    )

    private companion object {
        const val TAG = "PointsRepository"
    }
}

private class PointsParseException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
