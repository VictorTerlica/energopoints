@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.energypoints.finder.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.energypoints.finder.data.Point
import com.energypoints.finder.data.PointsSource
import com.energypoints.finder.ui.MissingInfo
import com.energypoints.finder.ui.PointFinderViewModel
import com.energypoints.finder.ui.components.IssuesDialog
import com.energypoints.finder.ui.components.PointCard
import com.energypoints.finder.ui.theme.BrandBlue
import kotlinx.coroutines.flow.collect
import java.io.File

@Composable
fun PointFinderScreen(viewModel: PointFinderViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuExpanded by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importPoints(uri)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportPoints(uri)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.updateLocationFromGps()
        } else {
            viewModel.onLocationPermissionDenied()
        }
    }

    val requestLocation: () -> Unit = {
        if (hasLocationPermission(context)) {
            viewModel.updateLocationFromGps()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val openImport: () -> Unit = {
        importLauncher.launch(
            arrayOf(
                "application/json",
                "text/plain",
                "application/octet-stream",
                "*/*"
            )
        )
    }

    val requestImport: () -> Unit = {
        if (state.points.isEmpty()) {
            openImport()
        } else {
            showImportConfirm = true
        }
    }

    val openExport: () -> Unit = {
        if (state.points.isNotEmpty()) {
            exportLauncher.launch("points.json")
        } else {
            viewModel.showMessage("Нет точек для экспорта")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EnergyPointsTopBar(
                isBusy = state.isLoading || state.isSaving,
                hasPoints = state.points.isNotEmpty(),
                menuExpanded = menuExpanded,
                onMenuExpandedChange = { menuExpanded = it },
                onAdd = { viewModel.startAddPoint() },
                onImport = requestImport,
                onExport = openExport
            )
        },
        snackbarHost = {
            if (state.sheet == null) {
                SnackbarHost(snackbarHostState)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val missing = state.missing
            val fatalError = state.fatalError

            when {
                state.isLoading -> LoadingContent()

                fatalError != null -> ErrorContent(
                    message = fatalError,
                    onRetry = viewModel::loadPoints,
                    onImport = requestImport,
                    onAdd = { viewModel.startAddPoint() }
                )

                missing != null -> MissingContent(
                    missing = missing,
                    onImport = requestImport,
                    onAdd = { viewModel.startAddPoint() },
                    onOpenFolder = {
                        openDataFolder(context, viewModel.dataDirectoryPath())
                    },
                    onRetry = viewModel::loadPoints,
                    onDemo = viewModel::loadDemo
                )

                else -> PointsContent(
                    points = state.points,
                    notes = notes,
                    source = state.source,
                    onPointClick = viewModel::selectPoint,
                    onAddPoint = { district, type ->
                        viewModel.startAddPoint(district, type)
                    },
                    onImport = requestImport
                )
            }

            if (state.isSaving) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }

    val sheet = state.sheet
    if (sheet != null) {
        PointSheet(
            sheet = sheet,
            points = state.points,
            notes = notes,
            isSaving = state.isSaving,
            isLocating = state.isLocating,
            snackbarHostState = snackbarHostState,
            onDismiss = viewModel::closeSheet,
            onEdit = viewModel::startEditPoint,
            onDeleteRequest = viewModel::requestDelete,
            onSaveDraft = viewModel::saveDraft,
            onCancelEdit = viewModel::cancelEditing,
            onDraftChange = viewModel::updateDraft,
            onSaveNote = viewModel::saveNote,
            onDeleteNote = viewModel::deleteNote,
            onShowMap = { point ->
                openMap(context, point) { message ->
                    viewModel.showMessage(message)
                }
            },
            onRequestLocation = requestLocation
        )
    }

    val deleteTarget = state.deleteTarget
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Удалить точку?") },
            text = { Text("Вы уверены, что хотите удалить точку?") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDelete,
                    enabled = !state.isSaving
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("Импортировать points.json?") },
            text = {
                Text(
                    "Текущие точки (${state.points.size}) будут заменены " +
                        "данными из файла."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirm = false
                        openImport()
                    }
                ) {
                    Text("Продолжить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    IssuesDialog(
        issues = state.issues,
        onDismiss = viewModel::dismissIssues
    )
}

@Composable
private fun EnergyPointsTopBar(
    isBusy: Boolean,
    hasPoints: Boolean,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit
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
            Box {
                IconButton(onClick = { onMenuExpandedChange(true) }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Меню"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) }
                ) {
                    DropdownMenuItem(
                        text = { Text("Добавить точку") },
                        enabled = !isBusy,
                        onClick = {
                            onMenuExpandedChange(false)
                            onAdd()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Импорт из папки Download") },
                        enabled = !isBusy,
                        onClick = {
                            onMenuExpandedChange(false)
                            onImport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Экспорт в папку Download") },
                        enabled = hasPoints && !isBusy,
                        onClick = {
                            onMenuExpandedChange(false)
                            onExport()
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun PointsContent(
    points: List<Point>,
    notes: Map<String, String>,
    source: PointsSource?,
    onPointClick: (Point) -> Unit,
    onAddPoint: (String?, String) -> Unit,
    onImport: () -> Unit
) {
    if (points.isEmpty()) {
        EmptyDataState(
            onAdd = { onAddPoint(null, "рп") },
            onImport = onImport
        )
        return
    }

    var selectedDistrict by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var rpEnabled by rememberSaveable { mutableStateOf(true) }
    var tpEnabled by rememberSaveable { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current

    val districtNames = remember(points) {
        points.map { it.district }
            .filter { it.isNotBlank() }
            .distinct()
    }

    LaunchedEffect(districtNames) {
        if (selectedDistrict != null && districtNames.none { it == selectedDistrict }) {
            selectedDistrict = null
        }
    }

    val filtered = remember(points, selectedDistrict, query, rpEnabled, tpEnabled) {
        val normalizedQuery = query.trim()
        points.filter { point ->
            val districtMatches = selectedDistrict == null ||
                point.district == selectedDistrict
            val typeMatches = when (point.type) {
                "рп" -> rpEnabled
                "тп" -> tpEnabled
                else -> true
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

    val filtersActive = selectedDistrict != null ||
        query.isNotBlank() ||
        !rpEnabled ||
        !tpEnabled

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
                            rpEnabled && tpEnabled -> rpEnabled = false
                            rpEnabled && !tpEnabled -> {
                                rpEnabled = true
                                tpEnabled = true
                            }
                            else -> rpEnabled = true
                        }
                    },
                    label = { Text("РП") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = tpEnabled,
                    onClick = {
                        when {
                            rpEnabled && tpEnabled -> tpEnabled = false
                            tpEnabled && !rpEnabled -> {
                                rpEnabled = true
                                tpEnabled = true
                            }
                            else -> tpEnabled = true
                        }
                    },
                    label = { Text("ТП") }
                )
                Spacer(modifier = Modifier.weight(1f))
                if (filtersActive) {
                    TextButton(
                        onClick = resetFilters,
                        contentPadding = PaddingValues(
                            horizontal = 8.dp,
                            vertical = 4.dp
                        )
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
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
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
                text = source?.label.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (source == PointsSource.DEMO) {
            DemoBanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filtered.isEmpty()) {
                item(key = "empty-results") {
                    NoResultsState(onReset = resetFilters)
                }
            } else {
                items(items = filtered, key = { it.id }) { point ->
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
                        Icon(Icons.Default.Check, contentDescription = null)
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
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DemoBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Демо-данные. Изменения будут сохранены во внешний points.json.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun EmptyDataState(
    onAdd: () -> Unit,
    onImport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Пока нет точек",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Добавьте точку вручную или импортируйте points.json " +
                "из папки Download.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Добавить точку")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Импортировать points.json")
        }
    }
}

@Composable
private fun NoResultsState(onReset: () -> Unit) {
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
                imageVector = Icons.Default.Search,
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
private fun MissingContent(
    missing: MissingInfo,
    onImport: () -> Unit,
    onAdd: () -> Unit,
    onOpenFolder: () -> Unit,
    onRetry: () -> Unit,
    onDemo: () -> Unit
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
                imageVector = Icons.Default.Warning,
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
            text = "Импортируйте JSON из папки Download или положите файл в " +
                "Android/data/$packageName/files/ и перезапустите приложение.",
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
                        text = missing.expectedPath,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "На Android 11+ прямой доступ к Android/data ограничен — " +
                        "используйте импорт. Разрешения на хранилище не нужны.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { copyPath(context, missing.expectedPath) },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 6.dp)
                ) {
                    Text("Скопировать путь")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Импортировать points.json")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Добавить точку")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenFolder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Открыть папку")
        }
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = onRetry) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Проверить снова")
        }
        if (missing.demoAvailable) {
            TextButton(onClick = onDemo) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Посмотреть демо-пример")
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onImport: () -> Unit,
    onAdd: () -> Unit
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
                imageVector = Icons.Default.Warning,
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
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Повторить")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Импортировать points.json")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Добавить точку")
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
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

private fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun openMap(
    context: Context,
    point: Point,
    onError: (String) -> Unit
) {
    val label = Uri.encode(point.displayName)
    val uri = Uri.parse(
        "geo:${point.latitude},${point.longitude}" +
            "?q=${point.latitude},${point.longitude}($label)"
    )
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        onError("Не найдено приложение для открытия карт")
    } catch (e: Exception) {
        onError("Не удалось открыть карту")
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
    clipboard.setPrimaryClip(ClipData.newPlainText("points.json", path))
    Toast.makeText(context, "Путь скопирован", Toast.LENGTH_SHORT).show()
}
