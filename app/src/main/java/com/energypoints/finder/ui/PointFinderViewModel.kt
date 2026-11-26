package com.energypoints.finder.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.energypoints.finder.data.LoadOutcome
import com.energypoints.finder.data.OWNERSHIP_OPTIONS
import com.energypoints.finder.data.POINT_TYPES
import com.energypoints.finder.data.Point
import com.energypoints.finder.data.PointColors
import com.energypoints.finder.data.PointValidator
import com.energypoints.finder.data.PointsRepository
import com.energypoints.finder.data.PointsSource
import com.energypoints.finder.data.NotesRepository
import com.energypoints.finder.data.formatCoordinate
import com.energypoints.finder.location.LocationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class MissingInfo(
    val expectedPath: String,
    val demoAvailable: Boolean
)

data class PointFinderUiState(
    val isLoading: Boolean = true,
    val points: List<Point> = emptyList(),
    val source: PointsSource? = null,
    val missing: MissingInfo? = null,
    val fatalError: String? = null,
    val issues: List<String> = emptyList(),
    val sheet: PointSheetState? = null,
    val deleteTarget: Point? = null,
    val isSaving: Boolean = false,
    val isLocating: Boolean = false
)

sealed interface PointSheetState {
    data class Details(val pointId: String) : PointSheetState
    data class Editor(val draft: PointDraft) : PointSheetState
}

data class PointDraft(
    val originalId: String? = null,
    val code: String = "",
    val type: String = "рп",
    val name: String = "",
    val district: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val note: String = "",
    val ownership: String = "РОССЕТИ",
    val iconColor: String = PointColors.BLUE
) {
    val isNew: Boolean
        get() = originalId == null

    companion object {
        fun from(point: Point): PointDraft = PointDraft(
            originalId = point.id,
            code = point.code,
            type = point.type.takeIf { it in POINT_TYPES } ?: "рп",
            name = point.name,
            district = point.district,
            latitude = formatCoordinate(point.latitude),
            longitude = formatCoordinate(point.longitude),
            note = point.note.orEmpty(),
            ownership = point.ownership?.takeIf { it in OWNERSHIP_OPTIONS } ?: "РОССЕТИ",
            iconColor = point.resolvedColor
        )
    }
}

/**
 * Единственный источник состояния экрана. Благодаря ViewModel список, открытая
 * карточка, черновик формы и результаты GPS переживают поворот экрана.
 */
class PointFinderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PointsRepository(application)
    private val notesRepository = NotesRepository(application)
    private val locationProvider = LocationProvider(application)

    private val _state = MutableStateFlow(PointFinderUiState())
    val state: StateFlow<PointFinderUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val notes: StateFlow<Map<String, String>> = notesRepository.notes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    private var loadJob: Job? = null

    init {
        loadPoints()
    }

    fun loadPoints() {
        if (_state.value.isSaving) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    fatalError = null,
                    missing = null,
                    issues = emptyList(),
                    deleteTarget = null
                )
            }
            when (val outcome = repository.load()) {
                is LoadOutcome.Success -> applyLoaded(outcome)

                is LoadOutcome.Missing -> _state.update {
                    it.copy(
                        isLoading = false,
                        points = emptyList(),
                        source = null,
                        missing = MissingInfo(
                            expectedPath = outcome.expectedPath,
                            demoAvailable = outcome.demoAvailable
                        ),
                        fatalError = null,
                        sheet = null
                    )
                }

                is LoadOutcome.Error -> _state.update {
                    it.copy(
                        isLoading = false,
                        points = emptyList(),
                        source = null,
                        missing = null,
                        fatalError = outcome.message,
                        sheet = null
                    )
                }
            }
        }
    }

    fun loadDemo() {
        if (_state.value.isSaving) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    fatalError = null,
                    missing = null,
                    issues = emptyList(),
                    deleteTarget = null
                )
            }
            when (val outcome = repository.loadDemo()) {
                is LoadOutcome.Success -> {
                    applyLoaded(outcome)
                    _messages.trySend("Демо-данные загружены")
                }

                is LoadOutcome.Missing -> _state.update {
                    it.copy(
                        isLoading = false,
                        fatalError = "Демо-файл не найден.",
                        sheet = null
                    )
                }

                is LoadOutcome.Error -> _state.update {
                    it.copy(
                        isLoading = false,
                        fatalError = outcome.message,
                        sheet = null
                    )
                }
            }
        }
    }

    private fun applyLoaded(outcome: LoadOutcome.Success) {
        val issues = outcome.issues + PointValidator.validate(outcome.points)
        _state.update {
            it.copy(
                isLoading = false,
                points = outcome.points,
                source = outcome.source,
                missing = null,
                fatalError = null,
                issues = issues,
                sheet = if (it.sheet is PointSheetState.Editor) it.sheet else null,
                deleteTarget = null,
                isSaving = false
            )
        }
    }

    fun selectPoint(point: Point) {
        _state.update { it.copy(sheet = PointSheetState.Details(point.id)) }
    }

    fun closeSheet() {
        _state.update { it.copy(sheet = null) }
    }

    fun startAddPoint(
        defaultDistrict: String? = null,
        defaultType: String = "рп"
    ) {
        if (_state.value.isSaving) return
        val type = defaultType.takeIf { it in POINT_TYPES } ?: "рп"
        _state.update {
            it.copy(
                sheet = PointSheetState.Editor(
                    PointDraft(
                        type = type,
                        district = defaultDistrict.orEmpty(),
                        iconColor = PointColors.defaultFor(type)
                    )
                ),
                issues = emptyList()
            )
        }
    }

    fun startEditPoint(point: Point) {
        if (_state.value.isSaving) return
        _state.update {
            it.copy(
                sheet = PointSheetState.Editor(PointDraft.from(point)),
                issues = emptyList()
            )
        }
    }

    fun updateDraft(draft: PointDraft) {
        _state.update { state ->
            val editor = state.sheet as? PointSheetState.Editor ?: return@update state
            state.copy(sheet = editor.copy(draft = draft))
        }
    }

    fun cancelEditing() {
        _state.update { state ->
            val editor = state.sheet as? PointSheetState.Editor ?: return@update state
            val originalId = editor.draft.originalId
            val target = if (originalId != null && state.points.any { it.id == originalId }) {
                PointSheetState.Details(originalId)
            } else {
                null
            }
            state.copy(sheet = target)
        }
    }

    fun saveDraft() {
        if (_state.value.isSaving) return
        val current = _state.value
        val editor = current.sheet as? PointSheetState.Editor ?: return
        val draft = editor.draft

        val errors = mutableListOf<String>()
        val code = draft.code.trim()
        val name = draft.name.trim()
        val district = draft.district.trim()

        if (code.isEmpty()) errors += "Ошибка: поле «Код» не заполнено."
        if (name.isEmpty()) errors += "Ошибка: поле «Название» не заполнено."
        if (district.isEmpty()) errors += "Ошибка: поле «Район» не заполнено."
        if (draft.type !in POINT_TYPES) errors += "Ошибка: выберите тип РП или ТП."
        if (draft.ownership !in OWNERSHIP_OPTIONS) {
            errors += "Ошибка: выберите принадлежность РОССЕТИ или ССК."
        }
        if (!PointColors.isValid(draft.iconColor)) {
            errors += "Ошибка: выберите цвет иконки."
        }

        val latitude = parseCoordinate(draft.latitude)
        val longitude = parseCoordinate(draft.longitude)
        if (latitude == null) {
            errors += "Ошибка: широта должна быть числом."
        } else if (latitude !in -90.0..90.0) {
            errors += "Ошибка: широта должна быть от -90 до 90."
        }
        if (longitude == null) {
            errors += "Ошибка: долгота должна быть числом."
        } else if (longitude !in -180.0..180.0) {
            errors += "Ошибка: долгота должна быть от -180 до 180."
        }

        if (errors.isNotEmpty()) {
            _state.update { it.copy(issues = errors) }
            return
        }

        val point = Point(
            code = code,
            type = draft.type,
            name = name,
            district = district,
            latitude = latitude ?: 0.0,
            longitude = longitude ?: 0.0,
            note = draft.note.trim().ifBlank { null },
            ownership = draft.ownership,
            iconColor = draft.iconColor,
            id = draft.originalId ?: UUID.randomUUID().toString()
        )

        val originalPoint = current.points.firstOrNull { it.id == draft.originalId }
        val exists = draft.originalId != null && originalPoint != null
        val candidate = if (exists) {
            current.points.map { if (it.id == draft.originalId) point else it }
        } else {
            current.points + point
        }

        val validation = PointValidator.validate(candidate)
        if (validation.isNotEmpty()) {
            _state.update { it.copy(issues = validation) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val result = repository.save(candidate)
            if (result.isSuccess) {
                if (originalPoint != null && originalPoint.noteKey != point.noteKey) {
                    runCatching {
                        notesRepository.moveNote(originalPoint.noteKey, point.noteKey)
                    }
                }
                val message = if (draft.originalId == null) {
                    "Точка добавлена"
                } else {
                    "Изменения сохранены"
                }
                _state.update { state ->
                    val stillEditing = state.sheet is PointSheetState.Editor
                    state.copy(
                        isSaving = false,
                        points = candidate,
                        source = PointsSource.EXTERNAL,
                        missing = null,
                        fatalError = null,
                        issues = emptyList(),
                        sheet = if (stillEditing) {
                            PointSheetState.Details(point.id)
                        } else {
                            state.sheet
                        }
                    )
                }
                _messages.trySend(message)
            } else {
                _state.update {
                    it.copy(
                        isSaving = false,
                        issues = listOf(
                            "Не удалось сохранить points.json: " +
                                (result.exceptionOrNull()?.message
                                    ?: "неизвестная ошибка")
                        )
                    )
                }
            }
        }
    }

    fun requestDelete(point: Point) {
        _state.update { it.copy(deleteTarget = point) }
    }

    fun dismissDelete() {
        _state.update { it.copy(deleteTarget = null) }
    }

    fun confirmDelete() {
        if (_state.value.isSaving) return
        val target = _state.value.deleteTarget ?: return
        val candidate = _state.value.points.filterNot { it.id == target.id }
        val validation = PointValidator.validate(candidate)

        if (validation.isNotEmpty()) {
            _state.update {
                it.copy(
                    deleteTarget = null,
                    issues = validation
                )
            }
            return
        }

        _state.update { it.copy(deleteTarget = null, isSaving = true) }
        viewModelScope.launch {
            val result = repository.save(candidate)
            if (result.isSuccess) {
                runCatching { notesRepository.deleteNote(target.noteKey) }
                _state.update {
                    it.copy(
                        isSaving = false,
                        points = candidate,
                        source = PointsSource.EXTERNAL,
                        missing = null,
                        fatalError = null,
                        sheet = null
                    )
                }
                _messages.trySend("Точка удалена")
            } else {
                _state.update {
                    it.copy(
                        isSaving = false,
                        issues = listOf(
                            "Не удалось удалить точку: " +
                                (result.exceptionOrNull()?.message
                                    ?: "неизвестная ошибка")
                        )
                    )
                }
            }
        }
    }

    fun importPoints(uri: Uri) {
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val parsedResult = repository.readPoints(uri)
            val parsed = parsedResult.getOrNull()

            if (parsed == null) {
                val message = parsedResult.exceptionOrNull()?.message
                    ?: "Не удалось прочитать файл."
                _state.update {
                    it.copy(
                        isSaving = false,
                        issues = listOf("Ошибка импорта: $message")
                    )
                }
                return@launch
            }

            val issues = buildList {
                addAll(parsed.issues)
                addAll(PointValidator.validate(parsed.points))
                if (parsed.points.isEmpty()) {
                    add("Ошибка: в файле нет точек.")
                }
            }
            if (issues.isNotEmpty()) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        issues = issues
                    )
                }
                return@launch
            }

            val result = repository.save(parsed.points)
            if (result.isSuccess) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        points = parsed.points,
                        source = PointsSource.EXTERNAL,
                        missing = null,
                        fatalError = null,
                        issues = emptyList(),
                        sheet = null,
                        deleteTarget = null
                    )
                }
                _messages.trySend("Импортировано точек: ${parsed.points.size}")
            } else {
                _state.update {
                    it.copy(
                        isSaving = false,
                        issues = listOf(
                            "Не удалось сохранить импорт: " +
                                (result.exceptionOrNull()?.message
                                    ?: "неизвестная ошибка")
                        )
                    )
                }
            }
        }
    }

    fun exportPoints(uri: Uri) {
        if (_state.value.isSaving) return
        val points = _state.value.points
        val validation = PointValidator.validate(points)
        if (validation.isNotEmpty()) {
            _state.update { it.copy(issues = validation) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val result = repository.export(points, uri)
            if (result.isSuccess) {
                _state.update { it.copy(isSaving = false) }
                _messages.trySend("Файл сохранён")
            } else {
                _state.update {
                    it.copy(
                        isSaving = false,
                        issues = listOf(
                            "Не удалось сохранить файл: " +
                                (result.exceptionOrNull()?.message
                                    ?: "неизвестная ошибка")
                        )
                    )
                }
            }
        }
    }

    fun saveNote(point: Point, text: String) {
        viewModelScope.launch {
            try {
                notesRepository.saveNote(point.noteKey, text)
                _messages.trySend(
                    if (text.isBlank()) "Заметка удалена" else "Заметка сохранена"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.trySend("Не удалось сохранить заметку")
            }
        }
    }

    fun deleteNote(point: Point) {
        viewModelScope.launch {
            try {
                notesRepository.deleteNote(point.noteKey)
                _messages.trySend("Заметка удалена")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.trySend("Не удалось удалить заметку")
            }
        }
    }

    fun updateLocationFromGps() {
        if (_state.value.isSaving || _state.value.isLocating) return
        if (!locationProvider.hasPermission()) {
            _messages.trySend("Нет разрешения на геолокацию")
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLocating = true) }
            val location = try {
                locationProvider.getCurrentLocation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

            _state.update { state ->
                val editor = state.sheet as? PointSheetState.Editor
                if (location == null || editor == null) {
                    state.copy(isLocating = false)
                } else {
                    state.copy(
                        isLocating = false,
                        sheet = PointSheetState.Editor(
                            editor.draft.copy(
                                latitude = formatCoordinate(location.latitude),
                                longitude = formatCoordinate(location.longitude)
                            )
                        )
                    )
                }
            }

            if (location != null) {
                _messages.trySend("Координаты получены")
            } else {
                _messages.trySend(
                    "Не удалось определить координаты. Проверьте GPS и разрешение."
                )
            }
        }
    }

    fun onLocationPermissionDenied() {
        _messages.trySend("Разрешение на геолокацию не выдано")
    }

    fun showMessage(text: String) {
        _messages.trySend(text)
    }

    fun dataDirectoryPath(): String = repository.dataDirectory().absolutePath

    fun dismissIssues() {
        _state.update { it.copy(issues = emptyList()) }
    }

    private fun parseCoordinate(raw: String): Double? =
        raw.trim().replace(',', '.').toDoubleOrNull()
}
