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

data class UiMessage(
    val text: String,
    val showIssuesAction: Boolean = false
)

data class PointFinderUiState(
    val isLoading: Boolean = true,
    val points: List<Point> = emptyList(),
    val source: PointsSource? = null,
    val missing: MissingInfo? = null,
    val fatalError: String? = null,
    val issues: List<String> = emptyList(),
    val issuesVisible: Boolean = false,
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

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

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

    private fun sendMessage(text: String, showIssuesAction: Boolean = false) {
        _messages.trySend(UiMessage(text, showIssuesAction))
    }

    private fun notifyIssues(issues: List<String>, prefix: String? = null) {
        if (issues.isEmpty()) return
        val text = if (prefix.isNullOrBlank()) {
            "Проверка данных: замечаний ${issues.size}"
        } else {
            "$prefix. Замечаний: ${issues.size}"
        }
        sendMessage(text, showIssuesAction = true)
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
                    issuesVisible = false,
                    deleteTarget = null
                )
            }
            when (val outcome = repository.load()) {
                is LoadOutcome.Success -> {
                    val issues = applyLoaded(outcome)
                    notifyIssues(issues)
                }

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
                        issues = emptyList(),
                        issuesVisible = false,
                        sheet = null
                    )
                }

                is LoadOutcome.Error -> {
                    val issues = listOf(outcome.message)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            points = emptyList(),
                            source = null,
                            missing = null,
                            fatalError = outcome.message,
                            issues = issues,
                            issuesVisible = false,
                            sheet = null
                        )
                    }
                    notifyIssues(issues)
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
                    issuesVisible = false,
                    deleteTarget = null
                )
            }
            when (val outcome = repository.loadDemo()) {
                is LoadOutcome.Success -> {
                    val issues = applyLoaded(outcome)
                    if (issues.isEmpty()) {
                        sendMessage("Демо-данные загружены")
                    } else {
                        notifyIssues(issues)
                    }
                }

                is LoadOutcome.Missing -> {
                    val issues = listOf("Демо-файл не найден.")
                    _state.update {
                        it.copy(
                            isLoading = false,
                            fatalError = issues.first(),
                            issues = issues,
                            issuesVisible = false,
                            sheet = null
                        )
                    }
                    notifyIssues(issues)
                }

                is LoadOutcome.Error -> {
                    val issues = listOf(outcome.message)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            fatalError = outcome.message,
                            issues = issues,
                            issuesVisible = false,
                            sheet = null
                        )
                    }
                    notifyIssues(issues)
                }
            }
        }
    }

    private fun applyLoaded(outcome: LoadOutcome.Success): List<String> {
        val issues = outcome.issues + PointValidator.validate(outcome.points)
        _state.update {
            it.copy(
                isLoading = false,
                points = outcome.points,
                source = outcome.source,
                missing = null,
                fatalError = null,
                issues = issues,
                issuesVisible = false,
                sheet = if (it.sheet is PointSheetState.Editor) it.sheet else null,
                deleteTarget = null,
                isSaving = false
            )
        }
        return issues
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

        val formIssues = mutableListOf<String>()
        val code = draft.code.trim()
        val name = draft.name.trim()
        val district = draft.district.trim()

        val type = draft.type.takeIf { it in POINT_TYPES } ?: "рп".also {
            formIssues += "Неизвестный тип «${draft.type}» заменён на РП."
        }
        val ownership = draft.ownership.takeIf { it in OWNERSHIP_OPTIONS } ?: "РОССЕТИ".also {
            formIssues += "Неизвестная принадлежность «${draft.ownership}» заменена на РОССЕТИ."
        }
        val iconColor = draft.iconColor
            .takeIf { PointColors.isValid(it) }
            ?: PointColors.defaultFor(type).also {
                formIssues += "Неизвестный цвет иконки заменён на стандартный."
            }

        val latitude = parseCoordinateOrZero(draft.latitude, "Широта", formIssues)
        val longitude = parseCoordinateOrZero(draft.longitude, "Долгота", formIssues)

        val point = Point(
            code = code,
            type = type,
            name = name,
            district = district,
            latitude = latitude,
            longitude = longitude,
            note = draft.note.trim().ifBlank { null },
            ownership = ownership,
            iconColor = iconColor,
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
        val issues = (formIssues + validation).distinct()

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val result = repository.save(candidate)
            if (result.isSuccess) {
                if (originalPoint != null && originalPoint.noteKey != point.noteKey) {
                    runCatching {
                        notesRepository.moveNote(originalPoint.noteKey, point.noteKey)
                    }
                }
                val baseMessage = if (exists) {
                    "Изменения сохранены"
                } else {
                    "Точка добавлена"
                }
                _state.update { state ->
                    val stillEditing = state.sheet is PointSheetState.Editor
                    state.copy(
                        isSaving = false,
                        points = candidate,
                        source = PointsSource.EXTERNAL,
                        missing = null,
                        fatalError = null,
                        issues = issues,
                        issuesVisible = false,
                        sheet = if (stillEditing) {
                            PointSheetState.Details(point.id)
                        } else {
                            state.sheet
                        }
                    )
                }
                if (issues.isEmpty()) {
                    sendMessage(baseMessage)
                } else {
                    sendMessage(
                        "$baseMessage. Замечаний: ${issues.size}",
                        showIssuesAction = true
                    )
                }
            } else {
                val technical = "Не удалось сохранить points.json: " +
                    (result.exceptionOrNull()?.message ?: "неизвестная ошибка")
                val allIssues = (issues + technical).distinct()
                _state.update {
                    it.copy(
                        isSaving = false,
                        issues = allIssues,
                        issuesVisible = false
                    )
                }
                sendMessage("Не удалось сохранить points.json", true)
            }
        }
    }

    private fun parseCoordinateOrZero(
        raw: String,
        label: String,
        issues: MutableList<String>
    ): Double {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            issues += "$label не заполнена — сохранено 0.0."
            return 0.0
        }
        val value = trimmed.replace(',', '.').toDoubleOrNull()
        if (value == null || !value.isFinite()) {
            issues += "$label «$trimmed» не число — сохранено 0.0."
            return 0.0
        }
        return value
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
        val issues = PointValidator.validate(candidate)

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
                        issues = issues,
                        issuesVisible = false,
                        sheet = null
                    )
                }
                if (issues.isEmpty()) {
                    sendMessage("Точка удалена")
                } else {
                    sendMessage(
                        "Точка удалена. Замечаний: ${issues.size}",
                        showIssuesAction = true
                    )
                }
            } else {
                val technical = "Не удалось удалить точку: " +
                    (result.exceptionOrNull()?.message ?: "неизвестная ошибка")
                val allIssues = (issues + technical).distinct()
                _state.update {
                    it.copy(
                        isSaving = false,
                        issues = allIssues,
                        issuesVisible = false
                    )
                }
                sendMessage("Не удалось удалить точку", true)
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
                val currentIssues = _state.value.issues
                val issues = (currentIssues + "Импорт: $message").distinct()
                _state.update {
                    it.copy(
                        isSaving = false,
                        issues = issues,
                        issuesVisible = false
                    )
                }
                notifyIssues(issues, prefix = "Импорт не выполнен")
                return@launch
            }

            val issues = buildList {
                addAll(parsed.issues)
                addAll(PointValidator.validate(parsed.points))
                if (parsed.points.isEmpty()) {
                    add("В файле нет точек.")
                }
            }.distinct()

            val result = repository.save(parsed.points)
            if (result.isSuccess) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        points = parsed.points,
                        source = PointsSource.EXTERNAL,
                        missing = null,
                        fatalError = null,
                        issues = issues,
                        issuesVisible = false,
                        sheet = null,
                        deleteTarget = null
                    )
                }
                if (issues.isEmpty()) {
                    sendMessage("Импортировано точек: ${parsed.points.size}")
                } else {
                    sendMessage(
                        "Импортировано точек: ${parsed.points.size}. " +
                            "Замечаний: ${issues.size}",
                        showIssuesAction = true
                    )
                }
            } else {
                val technical = "Не удалось сохранить импорт: " +
                    (result.exceptionOrNull()?.message ?: "неизвестная ошибка")
                val allIssues = (_state.value.issues + issues + technical).distinct()
                _state.update {
                    it.copy(
                        isSaving = false,
                        issues = allIssues,
                        issuesVisible = false
                    )
                }
                sendMessage("Не удалось сохранить импорт", true)
            }
        }
    }

    fun exportPoints(uri: Uri) {
        if (_state.value.isSaving) return
        val points = _state.value.points
        val currentIssues = _state.value.issues

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val result = repository.export(points, uri)
            if (result.isSuccess) {
                _state.update { it.copy(isSaving = false) }
                if (currentIssues.isEmpty()) {
                    sendMessage("Файл сохранён")
                } else {
                    sendMessage(
                        "Файл сохранён. Замечаний: ${currentIssues.size}",
                        showIssuesAction = true
                    )
                }
            } else {
                val technical = "Не удалось сохранить файл: " +
                    (result.exceptionOrNull()?.message ?: "неизвестная ошибка")
                val allIssues = (currentIssues + technical).distinct()
                _state.update {
                    it.copy(
                        isSaving = false,
                        issues = allIssues,
                        issuesVisible = false
                    )
                }
                sendMessage("Не удалось сохранить файл", true)
            }
        }
    }

    fun saveNote(point: Point, text: String) {
        viewModelScope.launch {
            try {
                notesRepository.saveNote(point.noteKey, text)
                sendMessage(
                    if (text.isBlank()) "Заметка удалена" else "Заметка сохранена"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sendMessage("Не удалось сохранить заметку")
            }
        }
    }

    fun deleteNote(point: Point) {
        viewModelScope.launch {
            try {
                notesRepository.deleteNote(point.noteKey)
                sendMessage("Заметка удалена")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sendMessage("Не удалось удалить заметку")
            }
        }
    }

    fun updateLocationFromGps() {
        if (_state.value.isSaving || _state.value.isLocating) return
        if (!locationProvider.hasPermission()) {
            sendMessage("Нет разрешения на геолокацию")
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
                sendMessage("Координаты получены")
            } else {
                sendMessage(
                    "Не удалось определить координаты. Проверьте GPS и разрешение."
                )
            }
        }
    }

    fun onLocationPermissionDenied() {
        sendMessage("Разрешение на геолокацию не выдано")
    }

    fun showMessage(text: String) {
        sendMessage(text)
    }

    fun dataDirectoryPath(): String = repository.dataDirectory().absolutePath

    fun openIssues() {
        _state.update { state ->
            if (state.issues.isEmpty()) state else state.copy(issuesVisible = true)
        }
    }

    fun dismissIssues() {
        _state.update { it.copy(issuesVisible = false) }
    }
}
