@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.energypoints.finder.ui.screens

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.energypoints.finder.data.District
import com.energypoints.finder.data.POINTS_FILE_NAME
import com.energypoints.finder.data.Point
import com.energypoints.finder.data.PointsData
import com.energypoints.finder.data.PointsRepository
import com.energypoints.finder.data.PointsState
import com.energypoints.finder.data.NotesRepository
import com.energypoints.finder.ui.theme.BrandAmber
import com.energypoints.finder.ui.theme.BrandAmberLight
import com.energypoints.finder.ui.theme.BrandBlue
import com.energypoints.finder.ui.theme.BrandNavy
import com.energypoints.finder.ui.theme.BrandSky
import com.energypoints.finder.ui.theme.RossetiGreen
import com.energypoints.finder.ui.theme.RossetiGreenContainer
import com.energypoints.finder.ui.theme.RossetiGreenContainerDark
import com.energypoints.finder.ui.theme.SskBlue
import com.energypoints.finder.ui.theme.SskBlueContainer
import com.energypoints.finder.ui.theme.SskBlueContainerDark
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Векторная молния для карточки и шапки деталей.
 * Намеренно нарисована кодом, чтобы не тянуть material-icons-extended.
 */
private val BoltIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Bolt",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(13.5f, 2f)
            lineTo(5f, 13.5f)
            horizontalLineTo(11f)
            lineTo(9.5f, 22f)
            lineTo(19f, 10f)
            horizontalLineTo(12.5f)
            close()
        }
    }.build()
}

class PointFinderViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PointsRepository(application)
    private val notesRepository = NotesRepository(application)

    private val _state = MutableStateFlow<PointsState>(PointsState.Loading)
    val state: StateFlow<PointsState> = _state.asStateFlow()

    val notes: StateFlow<Map<String, String>> = notesRepository.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var loadJob: Job? = null
    private var demoJob: Job? = null

    init {
        reload()
    }

    fun reload() {
        demoJob?.cancel()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = PointsState.Loading
            _state.value = repository.load()
        }
    }

    fun loadDemo() {
        loadJob?.cancel()
        demoJob?.cancel()
        demoJob = viewModelScope.launch {
            _state.value = PointsState.Loading
            _state.value = repository.loadDemo()
        }
    }

    fun importPoints(uri: Uri) {
        demoJob?.cancel()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = PointsState.Loading
            val result = repository.importPoints(uri)
            val failure = result.exceptionOrNull()
            _state.value = if (failure == null) {
                repository.load()
            } else {
                PointsState.Error(
                    "Не удалось скопировать points.json: " +
                        (failure.message ?: failure.javaClass.simpleName)
                )
            }
        }
    }

    fun saveNote(point: Point, text: String) {
        viewModelScope.launch {
            notesRepository.saveNote(point.noteKey, text)
        }
    }

    fun deleteNote(point: Point) {
        viewModelScope.launch {
            notesRepository.deleteNote(point.noteKey)
        }
    }

    fun dataDirectoryPath(): String = repository.dataDirectory().absolutePath

    fun expectedFilePath(): String = repository.expectedFilePath()
}

@Composable
fun PointFinderScreen(viewModel: PointFinderViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importPoints(uri)
        }
    }
    val pickPointsFile: () -> Unit = {
        try {
            filePicker.launch(
                arrayOf(
                    "application/json",
                    "text/plain",
                    "application/octet-stream",
                    "*/*"
                )
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "На устройстве не найден системный файловый менеджер",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    var selectedPointId by rememberSaveable { mutableStateOf<String?>(null) }
    var showInstructions by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var errorDialogVisible by remember(state) { mutableStateOf(state is PointsState.Error) }

    val data = (state as? PointsState.Ready)?.data
    val selectedPoint = data?.points?.firstOrNull { it.id == selectedPointId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EnergyPointsTopBar(
                isLoading = state is PointsState.Loading,
                onReload = viewModel::reload,
                onOpenFolder = {
                    openDataFolder(context, viewModel.dataDirectoryPath())
                },
                onPickFile = pickPointsFile,
                onShowInstructions = { showInstructions = true },
                menuExpanded = menuExpanded,
                onMenuExpandedChange = { menuExpanded = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val current = state) {
                is PointsState.Loading -> LoadingContent()

                is PointsState.Missing -> MissingDataContent(
                    expectedPath = current.expectedPath,
                    demoAvailable = current.demoAvailable,
                    onOpenFolder = {
                        openDataFolder(context, viewModel.dataDirectoryPath())
                    },
                    onPickFile = pickPointsFile,
                    onReload = viewModel::reload,
                    onLoadDemo = viewModel::loadDemo
                )

                is PointsState.Error -> ErrorContent(
                    message = current.message,
                    onRetry = viewModel::reload,
                    onPickFile = pickPointsFile,
                    onShowInstructions = { showInstructions = true }
                )

                is PointsState.Ready -> PointsSearchContent(
                    data = current.data,
                    notes = notes,
                    onPointClick = { selectedPointId = it.id },
                    onShowInstructions = { showInstructions = true }
                )
            }
        }
    }

  selectedPoint?.let { p ->
    PointDetailsSheet(
        p = p,
        rossetiCodes = rossetiCodes,
        onDismiss = { selectedPoint = null },
        onSave = { updatedPoint ->
            // Обновляем список в памяти и сохраняем в файл
            val updatedData = data.toMutableMap().mapValues { (_, types) ->
                types.mapValues { (_, points) ->
                    points.map { if (it.name == updatedPoint.name && it.latitude == updatedPoint.latitude) updatedPoint else it }
                }
            }
            PointsRepository.save(context, updatedData)
            selectedPoint = updatedPoint // Обновляем открытую карточку
            // Обновляем основной список на экране
            // (потребуется сделать data изменяемой переменной var data by remember...)
        }
    )
}

    if (showInstructions) {
        DataInstructionsDialog(
            expectedPath = viewModel.expectedFilePath(),
            onDismiss = { showInstructions = false },
            onOpenFolder = {
                showInstructions = false
                openDataFolder(context, viewModel.dataDirectoryPath())
            },
            onPickFile = {
                showInstructions = false
                pickPointsFile()
            }
        )
    }

    if (state is PointsState.Error && errorDialogVisible) {
        val error = state as PointsState.Error
        AlertDialog(
            onDismissRequest = { errorDialogVisible = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            title = { Text("Ошибка чтения данных") },
            text = { Text(error.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        errorDialogVisible = false
                        viewModel.reload()
                    }
                ) {
                    Text("Повторить")
                }
            },
            dismissButton = {
                TextButton(onClick = { errorDialogVisible = false }) {
                    Text("Закрыть")
                }
            }
        )
    }
}

@Composable
private fun EnergyPointsTopBar(
    isLoading: Boolean,
    onReload: () -> Unit,
    onOpenFolder: () -> Unit,
    onPickFile: () -> Unit,
    onShowInstructions: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "ЭнергоТочки",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "РП и ТП по координатам",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = BrandBlue,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White
        ),
        actions = {
            IconButton(onClick = onReload, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Перезагрузить данные"
                    )
                }
            }
            Box {
                IconButton(onClick = { onMenuExpandedChange(true) }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Меню"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) }
                ) {
                    DropdownMenuItem(
                        text = { Text("Перезагрузить данные") },
                        leadingIcon = {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                        },
                        onClick = {
                            onMenuExpandedChange(false)
                            onReload()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Импортировать points.json") },
                        leadingIcon = {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        },
                        onClick = {
                            onMenuExpandedChange(false)
                            onPickFile()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Открыть папку с данными") },
                        leadingIcon = {
                            Icon(Icons.Filled.Place, contentDescription = null)
                        },
                        onClick = {
                            onMenuExpandedChange(false)
                            onOpenFolder()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Как добавить points.json") },
                        leadingIcon = {
                            Icon(Icons.Filled.Info, contentDescription = null)
                        },
                        onClick = {
                            onMenuExpandedChange(false)
                            onShowInstructions()
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun PointsSearchContent(
    data: PointsData,
    notes: Map<String, String>,
    onPointClick: (Point) -> Unit,
    onShowInstructions: () -> Unit
) {
    var selectedDistrict by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var rpEnabled by rememberSaveable { mutableStateOf(true) }
    var tpEnabled by rememberSaveable { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current

    val districtNames = remember(data.districts) {
        data.districts
            .map(District::name)
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    LaunchedEffect(data.districts) {
        if (selectedDistrict != null && data.districts.none { it.name == selectedDistrict }) {
            selectedDistrict = null
        }
    }

    val filtered = remember(data, selectedDistrict, query, rpEnabled, tpEnabled) {
        val normalizedQuery = query.trim()
        data.points.filter { point ->
            val districtMatches = selectedDistrict == null || point.district == selectedDistrict
            val typeMatches = when (point.type) {
                "рп" -> rpEnabled
                "тп" -> tpEnabled
                else -> rpEnabled || tpEnabled
            }
            val queryMatches = normalizedQuery.isEmpty() ||
                point.code.contains(normalizedQuery, ignoreCase = true) ||
                point.name.contains(normalizedQuery, ignoreCase = true)
            districtMatches && typeMatches && queryMatches
        }
    }

    val resetFilters: () -> Unit = {
        selectedDistrict = null
        query = ""
        rpEnabled = true
        tpEnabled = true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 6.dp)
        ) {
            DistrictSelector(
                districts = districtNames,
                selected = selectedDistrict,
                onSelect = { selectedDistrict = it }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = rpEnabled,
                    onClick = {
                        when {
                            rpEnabled && tpEnabled -> tpEnabled = false
                            rpEnabled -> {
                                rpEnabled = true
                                tpEnabled = true
                            }
                            else -> {
                                rpEnabled = true
                                tpEnabled = true
                            }
                        }
                    },
                    label = { Text("РП") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = tpEnabled,
                    onClick = {
                        when {
                            rpEnabled && tpEnabled -> rpEnabled = false
                            tpEnabled -> {
                                rpEnabled = true
                                tpEnabled = true
                            }
                            else -> {
                                rpEnabled = true
                                tpEnabled = true
                            }
                        }
                    },
                    label = { Text("ТП") }
                )
                Spacer(modifier = Modifier.weight(1f))
                val filtersActive = selectedDistrict != null ||
                    query.isNotBlank() ||
                    !rpEnabled ||
                    !tpEnabled
                if (filtersActive) {
                    TextButton(
                        onClick = resetFilters,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Сброс")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Номер или название") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Очистить поиск"
                            )
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { focusManager.clearFocus() }
                )
            )
        }

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Найдено: ${filtered.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = data.source.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 2.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (data.isDemo) {
                item(key = "demo-banner") {
                    DemoBanner(onShowInstructions = onShowInstructions)
                }
            }
            if (data.skippedCount > 0) {
                item(key = "skipped-banner") {
                    SkippedBanner(skippedCount = data.skippedCount)
                }
            }
            if (filtered.isEmpty()) {
                item(key = "empty-state") {
                    EmptyResults(onReset = resetFilters)
                }
            } else {
                itemsIndexed(
                    items = filtered,
                    key = { index, point -> "${point.id}|$index" }
                ) { _, point ->
                    PointCard(
                        point = point,
                        note = notes[point.noteKey],
                        onClick = { onPointClick(point) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DistrictSelector(
    districts: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected ?: "Все районы",
            onValueChange = {},
            readOnly = true,
            label = { Text("Район") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Все районы") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
                trailingIcon = {
                    if (selected == null) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    }
                }
            )
            districts.forEach { district ->
                DropdownMenuItem(
                    text = { Text(district) },
                    onClick = {
                        onSelect(district)
                        expanded = false
                    },
                    trailingIcon = {
                        if (selected == district) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PointCard(
    point: Point,
    note: String?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(pointGradient(point)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = BoltIcon,
                    contentDescription = null,
                    tint = boltTint(point),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = point.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${point.district} • ${point.typeLabel}",
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!point.ownership.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        OwnershipBadge(ownership = point.ownership)
                    }
                }
                if (!note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = note.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun OwnershipBadge(ownership: String?) {
    val label = ownership?.trim().orEmpty()
    if (label.isEmpty()) return

    val darkTheme = isSystemInDarkTheme()
    val (background, foreground) = when {
        label.contains("РОС", ignoreCase = true) -> {
            if (darkTheme) {
                RossetiGreenContainerDark to Color(0xFFA5D6A7)
            } else {
                RossetiGreenContainer to RossetiGreen
            }
        }

        label.contains("ССК", ignoreCase = true) -> {
            if (darkTheme) {
                SskBlueContainerDark to Color(0xFF90CAF9)
            } else {
                SskBlueContainer to SskBlue
            }
        }

        else -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DemoBanner(onShowInstructions: () -> Unit) {
    val packageName = LocalContext.current.packageName
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Показаны демо-данные",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Чтобы загрузить свои точки, положите points.json в папку " +
                        "Android/data/$packageName/files/ и нажмите «Перезагрузить».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                TextButton(
                    onClick = onShowInstructions,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Text("Инструкция")
                }
            }
        }
    }
}

@Composable
private fun SkippedBanner(skippedCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Пропущено точек: $skippedCount",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "У этих точек некорректные координаты, обязательные поля " +
                        "или обнаружены дубли. Подробности — в Logcat с тегом PointsRepository.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun EmptyResults(onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Ничего не найдено",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Измените запрос или сбросьте фильтры.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onReset) {
            Text("Сбросить фильтры")
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Читаем points.json…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MissingDataContent(
    expectedPath: String,
    demoAvailable: Boolean,
    onOpenFolder: () -> Unit,
    onPickFile: () -> Unit,
    onReload: () -> Unit,
    onLoadDemo: () -> Unit
) {
    val context = LocalContext.current
    val packageName = context.packageName

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Нужен файл points.json",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Положите points.json в папку Android/data/$packageName/files/ " +
                "и перезапустите приложение (или нажмите «Проверить снова»). " +
                "На Android 11+ проще нажать «Выбрать points.json» — приложение " +
                "само скопирует файл в нужную папку.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Путь к файлу",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = expectedPath,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Папка уже создана приложением. На Android 11+ доступ к " +
                        "Android/data ограничен: используйте «Выбрать points.json» — " +
                        "приложение скопирует файл само. Также подойдут USB или ADB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { copyPath(context, expectedPath) },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 6.dp)
                ) {
                    Text("Скопировать путь")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onPickFile,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Выбрать points.json")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onOpenFolder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Открыть папку")
        }

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(onClick = onReload) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Проверить снова")
        }

        if (demoAvailable) {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onLoadDemo) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Посмотреть демо-пример")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Для локальной сборки можно также положить файл в " +
                "app/src/main/assets/points.json. Не публикуйте этот файл на GitHub.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onPickFile: () -> Unit,
    onShowInstructions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Не удалось прочитать данные",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Исправьте JSON и нажмите «Повторить». Технические подробности " +
                "пишутся в Logcat с тегом PointsRepository.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Повторить")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onPickFile,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Выбрать другой points.json")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onShowInstructions,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Инструкция")
        }
    }
}

@Composable
private fun DataInstructionsDialog(
    expectedPath: String,
    onDismiss: () -> Unit,
    onOpenFolder: () -> Unit,
    onPickFile: () -> Unit
) {
    val context = LocalContext.current
    val packageName = context.packageName

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Info, contentDescription = null) },
        title = { Text("Как добавить points.json") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "1. Установите APK и запустите приложение один раз — " +
                        "папка данных создастся автоматически.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "2. Нажмите «Выбрать points.json» и укажите файл — приложение " +
                        "само скопирует его в приватную папку. На Android 11+ это самый " +
                        "простой способ, разрешения на хранилище не нужны.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "3. Либо подключите телефон к компьютеру в режиме передачи " +
                        "файлов (MTP) или нажмите «Открыть папку» и скопируйте файл вручную.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "4. Вернитесь в приложение и нажмите «Перезагрузить данные» " +
                        "в правом верхнем углу.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(14.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Полный путь",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                text = expectedPath,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        TextButton(
                            onClick = { copyPath(context, expectedPath) },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Скопировать путь")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onPickFile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выбрать points.json")
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Файл читается только локально, аналитики нет. Координаты " +
                        "уходят в карты только когда вы сами нажимаете кнопку " +
                        "«Открыть в Яндекс.Картах» или «Открыть в Google Maps».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenFolder) {
                Text("Открыть папку")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Понятно")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PointDetailsSheet(
    p: Point,
    rossetiCodes: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Point) -> Unit // Коллбэк для сохранения
) {
    val context = LocalContext.current
    var isEditing by remember { mutableStateOf(false) }
    
    // Локальный стейт для редактирования
    var editOwnership by remember { mutableStateOf(p.ownership) }
    var editColor by remember { mutableStateOf(p.iconColor) }
    var editNote by remember { mutableStateOf(p.note) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
            
            // Заголовок
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).background(Color(android.graphics.Color.parseColor(if (isEditing) editColor else p.iconColor)), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocationOn, null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Text(p.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("${p.district} • ${p.type}", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(16.dp))

            if (isEditing) {
                // --- РЕЖИМ РЕДАКТИРОВАНИЯ ---
                Text("Принадлежность", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = editOwnership == "РОССЕТИ", onClick = { editOwnership = "РОССЕТИ" }, label = { Text("РОССЕТИ") })
                    FilterChip(selected = editOwnership == "ССК", onClick = { editOwnership = "ССК" }, label = { Text("ССК") })
                }

                Spacer(Modifier.height(16.dp))
                Text("Цвет иконки", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    // Предустановленные цвета
                    val colors = listOf("#F44336", "#2196F3", "#4CAF50", "#FF9800", "#9C27B0", "#607D8B")
                    colors.forEach { hex ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .background(Color(android.graphics.Color.parseColor(hex)), RoundedCornerShape(50))
                                .clickable { editColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (editColor == hex) {
                                Icon(Icons.Default.Check, null, tint = Color.White)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Примечание", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = editNote,
                    onValueChange = { editNote = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = 2
                )

                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val updatedPoint = p.copy(
                                ownership = editOwnership,
                                iconColor = editColor,
                                note = editNote
                            )
                            onSave(updatedPoint)
                            isEditing = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Сохранить в JSON")
                    }
                    OutlinedButton(onClick = { isEditing = false }) {
                        Text("Отмена")
                    }
                }

            } else {
                // --- РЕЖИМ ПРОСМОТРА ---
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Принадлежность: ", fontWeight = FontWeight.SemiBold)
                    OwnershipBadge(p.ownership == "РОССЕТИ")
                }

                if (p.note.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("📝 ${p.note}", fontSize = 14.sp)
                }

                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Координаты", fontWeight = FontWeight.SemiBold)
                        Text("Широта:  ${p.latitude}", fontSize = 15.sp)
                        Text("Долгота: ${p.longitude}", fontSize = 15.sp)
                    }
                }

                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = { isEditing = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Редактировать данные")
                }

                Spacer(Modifier.height(8.dp))
                Button(onClick = { openYandexMaps(context, p.latitude, p.longitude, p.name) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.Map, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Открыть в Яндекс.Картах")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CoordinatesCard(point: Point) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Координаты",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SelectionContainer {
                Column {
                    CoordinateRow(
                        label = "Широта",
                        value = formatCoordinate(point.latitude)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    CoordinateRow(
                        label = "Долгота",
                        value = formatCoordinate(point.longitude)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "${formatCoordinate(point.latitude)}, " +
                    formatCoordinate(point.longitude),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun CoordinateRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun MyNoteCard(
    note: String?,
    editing: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Моя заметка",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            when {
                editing -> {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8,
                        label = { Text("Текст заметки") },
                        placeholder = {
                            Text("Например: проверить нагрузку в вечерний максимум")
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = onSave,
                            enabled = draft.isNotBlank()
                        ) {
                            Text("Сохранить")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(onClick = onCancel) {
                            Text("Отмена")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = onDelete,
                            enabled = note != null
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Удалить заметку",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                note.isNullOrBlank() -> {
                    Text(
                        text = "Заметки пока нет",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(onClick = onStartEdit) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Добавить заметку")
                    }
                }

                else -> {
                    Text(
                        text = note.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(onClick = onStartEdit) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Изменить")
                    }
                }
            }
        }
    }
}

@Composable
private fun MapButtons(point: Point) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { openYandexMaps(context, point) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Открыть в Яндекс.Картах")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { openGoogleMaps(context, point) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Открыть в Google Maps")
        }
    }
}

private fun pointGradient(point: Point): Brush {
    return if (point.isRp) {
        Brush.linearGradient(listOf(BrandBlue, BrandSky))
    } else {
        Brush.linearGradient(listOf(BrandAmber, BrandAmberLight))
    }
}

private fun boltTint(point: Point): Color {
    return if (point.isRp) Color.White else BrandNavy
}

private fun formatCoordinate(value: Double): String =
    String.format(Locale.US, "%.6f", value)

private fun openYandexMaps(context: Context, point: Point) {
    // В Яндекс.Картах сначала долгота, потом широта!
    val url = "https://yandex.ru/maps/?pt=${point.longitude},${point.latitude}" +
        "&z=17&l=map&text=${Uri.encode(point.displayName)}"
    openUrl(context, url, "Не удалось открыть Яндекс.Карты")
}

private fun openGoogleMaps(context: Context, point: Point) {
    val geoUri = Uri.parse(
        "geo:${point.latitude},${point.longitude}" +
            "?q=${point.latitude},${point.longitude}"
    )
    val geoIntent = Intent(Intent.ACTION_VIEW, geoUri)

    if (geoIntent.resolveActivity(context.packageManager) != null) {
        try {
            context.startActivity(geoIntent)
            return
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "geo: не обработан, открываю Google Maps в браузере", e)
        } catch (e: Exception) {
            Log.w(TAG, "geo: не обработан, открываю Google Maps в браузере", e)
        }
    }

    openUrl(
        context = context,
        url = "https://maps.google.com/?q=${point.latitude},${point.longitude}",
        errorMessage = "Не удалось открыть Google Maps"
    )
}

private fun openUrl(context: Context, url: String, errorMessage: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Log.e(TAG, "Не удалось открыть ссылку $url", e)
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
    }
}

@Suppress("DEPRECATION")
private fun openDataFolder(context: Context, path: String) {
    val directory = File(path)
    if (!directory.exists()) {
        directory.mkdirs()
    }

    val relativePath = try {
        val storageRoot = Environment.getExternalStorageDirectory().absolutePath
        directory.absolutePath
            .removePrefix(storageRoot)
            .trimStart('/')
            .takeIf { it.startsWith("Android/") }
            ?: "Android/data/${context.packageName}/files"
    } catch (e: Exception) {
        "Android/data/${context.packageName}/files"
    }

    val documentId = if (relativePath.isBlank()) {
        "primary:Android/data/${context.packageName}/files"
    } else {
        "primary:$relativePath"
    }

    val uri = DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents",
        documentId
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "Не удалось открыть папку. Путь: ${directory.absolutePath}",
            Toast.LENGTH_LONG
        ).show()
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Не удалось открыть папку. Путь: ${directory.absolutePath}",
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun copyPath(context: Context, path: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard == null) {
        Toast.makeText(context, "Буфер обмена недоступен", Toast.LENGTH_SHORT).show()
        return
    }
    clipboard.setPrimaryClip(ClipData.newPlainText(POINTS_FILE_NAME, path))
    Toast.makeText(context, "Путь скопирован", Toast.LENGTH_SHORT).show()
}

private const val TAG = "PointFinderScreen"
