package com.energypoints.finder.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.energypoints.finder.data.Point
import com.energypoints.finder.data.PointsRepository
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointFinderScreen() {
    val context = LocalContext.current
    var data by remember { mutableStateOf(PointsRepository.load(context)) }

    var selectedDistrict by remember(data) { mutableStateOf(data.keys.firstOrNull() ?: "") }
    var selectedType by remember(data) { mutableStateOf("РП") }
    var query by remember { mutableStateOf("") }
    var selectedPoint by remember { mutableStateOf<Point?>(null) }

    // Лончер для ВЫБОРА (Импорта) файла
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                // Копируем выбранный файл во внутреннюю папку приложения
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val destFile = File(context.getExternalFilesDir(null), "points.json")
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                // Перезагружаем данные
                data = PointsRepository.load(context)
                selectedDistrict = data.keys.firstOrNull() ?: ""
            } catch (e: Exception) {
                // Ошибка чтения файла (можно добавить Toast)
            }
        }
    }

    // Лончер для СОХРАНЕНИЯ (Экспорта) файла
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            try {
                val sourceFile = File(context.getExternalFilesDir(null), "points.json")
                if (sourceFile.exists()) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ошибка сохранения
            }
        }
    }

    val types = data[selectedDistrict]?.keys?.toList() ?: emptyList()
    val points = data[selectedDistrict]?.get(selectedType).orEmpty()
    val filtered = remember(query, points) {
        if (query.isBlank()) points
        else points.filter {
            it.code.contains(query.trim(), true) || it.name.contains(query.trim(), true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ЭнергоТочки", fontWeight = FontWeight.Bold) },
                actions = {
                    // Кнопка импорта
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                        Icon(Icons.Default.Add, contentDescription = "Загрузить JSON")
                    }
                    // Кнопка экспорта
                    if (data.isNotEmpty()) {
                        IconButton(onClick = { exportLauncher.launch("points_updated.json") }) {
                            Icon(Icons.Default.Share, contentDescription = "Сохранить/Выгрузить JSON")
                        }
                    }
                    // Кнопка обновления
                    IconButton(onClick = { data = PointsRepository.load(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (data.isEmpty()) {
                // ЭКРАН, ЕСЛИ ФАЙЛА НЕТ
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Warning, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Файл с точками не найден",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Нажмите кнопку ниже и выберите ваш points.json в памяти телефона (например, в папке «Загрузки»).\n\nИли скопируйте его вручную по пути: Android/data/com.energypoints.finder/files/",
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Выбрать points.json", fontSize = 16.sp)
                    }
                }
            } else {
                // ЭКРАН С ДАННЫМИ
                DropdownField(
                    label = "Район",
                    value = selectedDistrict,
                    options = data.keys.toList(),
                    onSelect = {
                        selectedDistrict = it
                        selectedType = data[it]?.keys?.firstOrNull() ?: ""
                    }
                )

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    types.forEach { t ->
                        FilterChip(
                            selected = selectedType == t,
                            onClick = { selectedType = t },
                            label = { Text(t) },
                            leadingIcon = {
                                Icon(if (t == "РП") Icons.Default.Home else Icons.Default.Star, null)
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Поиск по номеру") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(filtered, key = { it.name + it.latitude }) { p ->
                        PointCard(p) { selectedPoint = p }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }

        selectedPoint?.let { p ->
            PointDetailsSheet(
                p = p,
                onDismiss = { selectedPoint = null },
                onSave = { updatedPoint ->
                    val updatedData = data.toMutableMap().mapValues { (_, typesMap) ->
                        typesMap.mapValues { (_, pointsList) ->
                            pointsList.map { if (it.name == updatedPoint.name && it.latitude == updatedPoint.latitude) updatedPoint else it }
                        }
                    }
                    PointsRepository.save(context, updatedData)
                    data = updatedData
                    selectedPoint = updatedPoint
                }
            )
        }
    }
}

@Composable
private fun PointCard(p: Point, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(
                        try { Color(android.graphics.Color.parseColor(p.iconColor)) } catch (e: Exception) { Color.Blue },
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Place, null, tint = Color.White)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name.ifBlank { p.code }, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    OwnershipBadge(p.ownership == "РОССЕТИ")
                }
                Text("${p.district} • ${p.type}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.note.isNotBlank()) {
                    Text(p.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Icon(Icons.Default.KeyboardArrowRight, null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PointDetailsSheet(
    p: Point,
    onDismiss: () -> Unit,
    onSave: (Point) -> Unit
) {
    val context = LocalContext.current
    var isEditing by remember { mutableStateOf(false) }
    
    var editOwnership by remember { mutableStateOf(p.ownership) }
    var editColor by remember { mutableStateOf(p.iconColor) }
    var editNote by remember { mutableStateOf(p.note) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).background(
                        try { Color(android.graphics.Color.parseColor(if (isEditing) editColor else p.iconColor)) } catch (e: Exception) { Color.Blue },
                        RoundedCornerShape(10.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Place, null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Text(p.name.ifBlank { p.code }, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                if (!isEditing) OwnershipBadge(p.ownership == "РОССЕТИ")
            }
            Spacer(Modifier.height(8.dp))
            Text("${p.district} • ${p.type}", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(16.dp))

            if (isEditing) {
                Text("Принадлежность", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = editOwnership == "РОССЕТИ", onClick = { editOwnership = "РОССЕТИ" }, label = { Text("РОССЕТИ") })
                    FilterChip(selected = editOwnership == "ССК", onClick = { editOwnership = "ССК" }, label = { Text("ССК") })
                }

                Spacer(Modifier.height(16.dp))
                Text("Цвет иконки", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    val colors = listOf("#1565C0", "#2E7D32", "#F44336", "#FF9800", "#9C27B0", "#607D8B")
                    colors.forEach { hex ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .background(
                                    try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Blue },
                                    CircleShape
                                )
                                .clickable { editColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (editColor == hex) Icon(Icons.Default.Check, null, tint = Color.White)
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
                            onSave(p.copy(ownership = editOwnership, iconColor = editColor, note = editNote))
                            isEditing = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Сохранить")
                    }
                    OutlinedButton(onClick = { isEditing = false }) { Text("Отмена") }
                }

            } else {
                if (p.note.isNotBlank()) {
                    Text("📝 ${p.note}", fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Координаты", fontWeight = FontWeight.SemiBold)
                        Text("Широта:  ${p.latitude}", fontSize = 15.sp)
                        Text("Долгота: ${p.longitude}", fontSize = 15.sp)
                    }
                }

                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = { isEditing = true }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Редактировать данные")
                }

                Spacer(Modifier.height(8.dp))
                Button(onClick = { openYandexMaps(context, p.latitude, p.longitude, p.name) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Place, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Открыть в Яндекс.Картах")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OwnershipBadge(isRosseti: Boolean) {
    val (text, color) = if (isRosseti) "РОССЕТИ" to Color(0xFF2E7D32) else "ССК" to Color(0xFF1565C0)
    Box(Modifier.background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { onSelect(it); expanded = false }) }
        }
    }
}

private fun openYandexMaps(context: Context, lat: Double, lon: Double, name: String) {
    val uri = Uri.parse("https://yandex.ru/maps/?pt=$lon,$lat&z=17&l=map&text=${Uri.encode(name)}")
    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
