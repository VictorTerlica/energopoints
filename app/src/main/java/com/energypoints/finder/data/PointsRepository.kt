package com.energypoints.finder.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File
import java.util.Locale

const val POINTS_FILE_NAME = "points.json"
const val EXAMPLE_FILE_NAME = "points.example.json"

enum class PointsSource {
    /** Реальный файл из Android/data/<package>/files/points.json. */
    EXTERNAL,

    /** Файл, встроенный в APK: assets/points.json. */
    ASSETS,

    /** Демонстрационный assets/points.example.json. */
    DEMO;

    val label: String
        get() = when (this) {
            EXTERNAL -> "внешний файл"
            ASSETS -> "assets/$POINTS_FILE_NAME"
            DEMO -> "демо-пример"
        }
}

data class District(
    val name: String,
    val points: List<Point>
)

data class PointsData(
    val districts: List<District>,
    val source: PointsSource,
    val skippedCount: Int
) {
    val points: List<Point> = districts.flatMap { it.points }

    val totalCount: Int
        get() = points.size

    val isDemo: Boolean
        get() = source == PointsSource.DEMO
}

sealed interface PointsState {
    data object Loading : PointsState

    data class Ready(val data: PointsData) : PointsState

    data class Missing(
        val expectedPath: String,
        val demoAvailable: Boolean
    ) : PointsState

    data class Error(val message: String) : PointsState
}

/**
 * Загружает точки из двух мест по порядку:
 *  1) context.getExternalFilesDir(null)/points.json — внешняя приватная папка
 *     приложения, доступная файловому менеджеру и НЕ требующая разрешений;
 *  2) assets/points.json — если пользователь всё же встроил файл при сборке.
 *
 * Если points.json нет, возвращается Missing: UI покажет инструкцию.
 * Демо-файл assets/points.example.json автоматически не подставляется —
 * его можно открыть только явным действием пользователя.
 */
@OptIn(ExperimentalSerializationApi::class)
class PointsRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
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

    suspend fun load(): PointsState = withContext(Dispatchers.IO) {
        val file = File(prepareDataDirectory(), POINTS_FILE_NAME)
        try {
            when {
                file.isFile -> parseState(
                    raw = file.readText(Charsets.UTF_8),
                    source = PointsSource.EXTERNAL,
                    path = file.absolutePath
                )

                assetExists(POINTS_FILE_NAME) -> parseState(
                    raw = readAsset(POINTS_FILE_NAME),
                    source = PointsSource.ASSETS,
                    path = "assets/$POINTS_FILE_NAME"
                )

                else -> PointsState.Missing(
                    expectedPath = file.absolutePath,
                    demoAvailable = assetExists(EXAMPLE_FILE_NAME)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось прочитать points.json", e)
            PointsState.Error(
                "Не удалось прочитать файл: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    /**
     * Копирует выбранный пользователем документ (SAF) в
     * Android/data/<package>/files/points.json.
     *
     * Это обходной путь для Android 11+, где системный файловый менеджер и
     * DocumentsUI больше не дают свободно открывать Android/data. Разрешения
     * на чтение хранилища не нужны: доступ даётся только к выбранному файлу.
     */
    suspend fun importPoints(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val directory = prepareDataDirectory()
            val destination = File(directory, POINTS_FILE_NAME)
            val temporary = File(directory, "$POINTS_FILE_NAME.importing")

            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Не удалось открыть выбранный файл.")
                input.use { source ->
                    temporary.outputStream().use { target ->
                        source.copyTo(target)
                    }
                }
                temporary.copyTo(destination, overwrite = true)
                Result.success(destination.absolutePath)
            } finally {
                if (temporary.exists()) {
                    temporary.delete()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось импортировать points.json", e)
            Result.failure(e)
        }
    }

    suspend fun loadDemo(): PointsState = withContext(Dispatchers.IO) {
        try {
            if (!assetExists(EXAMPLE_FILE_NAME)) {
                return@withContext PointsState.Error(
                    "Демо-файл assets/$EXAMPLE_FILE_NAME не найден."
                )
            }
            parseState(
                raw = readAsset(EXAMPLE_FILE_NAME),
                source = PointsSource.DEMO,
                path = "assets/$EXAMPLE_FILE_NAME"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось прочитать демо-файл", e)
            PointsState.Error(
                "Не удалось прочитать демо-файл: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    private fun parseState(raw: String, source: PointsSource, path: String): PointsState {
        return try {
            val outcome = parse(raw)
            if (outcome.districts.isEmpty()) {
                val detail = if (outcome.skipped > 0) {
                    "Все точки оказались некорректными (пропущено: ${outcome.skipped}). " +
                        "Подробности — в Logcat с тегом $TAG."
                } else {
                    "В файле нет ни одного района с точками."
                }
                throw PointsParseException(
                    "Файл $path прочитан, но не содержит валидных данных. $detail"
                )
            }
            PointsState.Ready(
                PointsData(
                    districts = outcome.districts,
                    source = source,
                    skippedCount = outcome.skipped
                )
            )
        } catch (e: PointsParseException) {
            Log.e(TAG, "Некорректный JSON в $path", e)
            PointsState.Error(e.message ?: "Некорректный JSON.")
        }
    }

    private fun parse(raw: String): ParseOutcome {
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

        var skipped = 0
        val districts = mutableListOf<District>()

        for ((rawDistrictName, districtElement) in root) {
            val districtName = rawDistrictName.trim()
            if (districtName.isEmpty()) {
                Log.w(TAG, "Пропущен район с пустым именем.")
                continue
            }
            val districtObject = districtElement as? JsonObject
            if (districtObject == null) {
                Log.w(TAG, "Район «$districtName» пропущен: значение не является объектом.")
                continue
            }

            val decoded = mutableListOf<Point>()
            decoded += decodeList(
                element = districtObject.arrayFor("рп", "rp"),
                fallbackType = "рп",
                districtName = districtName,
                onSkipped = { skipped++ }
            )
            decoded += decodeList(
                element = districtObject.arrayFor("тп", "tp"),
                fallbackType = "тп",
                districtName = districtName,
                onSkipped = { skipped++ }
            )

            // Защита от дублей: одинаковые type+code в одном районе ломают
            // уникальность LazyColumn-ключей и заметок.
            val unique = LinkedHashMap<String, Point>()
            for (point in decoded) {
                val key = "${point.type}|${point.code}"
                if (unique.containsKey(key)) {
                    Log.w(TAG, "Дубль ${point.typeLabel}-${point.code} в районе «$districtName» пропущен.")
                    skipped++
                } else {
                    unique[key] = point
                }
            }

            if (unique.isEmpty()) {
                Log.w(TAG, "В районе «$districtName» нет корректных точек.")
                continue
            }

            districts += District(
                name = districtName,
                points = unique.values.toList()
            )
        }

        return ParseOutcome(districts = districts, skipped = skipped)
    }

    private fun decodeList(
        element: JsonElement?,
        fallbackType: String,
        districtName: String,
        onSkipped: () -> Unit
    ): List<Point> {
        if (element == null || element is JsonNull) return emptyList()
        val array = element as? JsonArray ?: throw PointsParseException(
            "Поле «$fallbackType» района «$districtName» должно быть массивом."
        )

        val result = ArrayList<Point>(array.size)
        for (item in array) {
            try {
                val decoded = json.decodeFromJsonElement<Point>(item)
                val code = decoded.code.trim()
                if (code.isEmpty()) {
                    Log.w(TAG, "Пропущена точка без code (район «$districtName», тип «$fallbackType»).")
                    onSkipped()
                    continue
                }
                if (!decoded.latitude.isFinite() || decoded.latitude !in -90.0..90.0) {
                    Log.w(
                        TAG,
                        "Пропущена точка $code (район «$districtName»): " +
                            "широта ${decoded.latitude} вне диапазона -90..90."
                    )
                    onSkipped()
                    continue
                }
                if (!decoded.longitude.isFinite() || decoded.longitude !in -180.0..180.0) {
                    Log.w(
                        TAG,
                        "Пропущена точка $code (район «$districtName»): " +
                            "долгота ${decoded.longitude} вне диапазона -180..180."
                    )
                    onSkipped()
                    continue
                }

                val point = decoded.copy(
                    code = code,
                    name = decoded.name.trim().ifBlank { code },
                    type = normalizeType(decoded.type.ifBlank { fallbackType }),
                    district = districtName,
                    districtKey = districtName,
                    note = decoded.note?.trim()?.takeIf { it.isNotEmpty() },
                    ownership = normalizeOwnership(decoded.ownership)
                )
                result += point
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Пропущена некорректная точка в районе «$districtName»: ${e.message}",
                    e
                )
                onSkipped()
            }
        }
        return result
    }

    private fun JsonObject.arrayFor(vararg names: String): JsonElement? {
        for ((key, value) in this) {
            if (names.any { it.equals(key.trim(), ignoreCase = true) }) {
                return value
            }
        }
        return null
    }

    private fun normalizeType(value: String): String {
        return when (value.trim().lowercase(Locale.ROOT)) {
            "рп", "rp" -> "рп"
            "тп", "tp" -> "тп"
            else -> value.trim().lowercase(Locale.ROOT)
        }
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

    private fun assetExists(name: String): Boolean {
        return runCatching {
            context.assets.open(name).close()
        }.isSuccess
    }

    private fun readAsset(name: String): String {
        return context.assets.open(name)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    private data class ParseOutcome(
        val districts: List<District>,
        val skipped: Int
    )

    private companion object {
        const val TAG = "PointsRepository"
    }
}

private class PointsParseException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
