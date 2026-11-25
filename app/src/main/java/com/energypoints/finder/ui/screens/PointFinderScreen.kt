package com.energypoints.finder.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointFinderScreen() {
    val context = LocalContext.current
    var data by remember { mutableStateOf(PointsRepository.load(context)) }
    var reloadTrigger by remember { mutableStateOf(0) } // Триггер для перезагрузки

    // Перезагружаем данные при нажатии на кнопку
    LaunchedEffect(reloadTrigger) {
        if (reloadTrigger > 0) {
            data = PointsRepository.load(context)
        }
    }

    var selectedDistrict by remember(data) { mutableStateOf(data.keys.firstOrNull() ?: "") }
    var selectedType by remember(data) { mutableStateOf("РП") }
    var query by remember { mutableStateOf("") }

    val types = data[selectedDistrict]?.keys?.toList() ?: emptyList()
    val points = data[selectedDistrict]?.get(selectedType).orEmpty()
    val filtered = remember(query, points) {
        if (query.isBlank()) points
        else points.filter {
            it.code.contains(query.trim(), true) || it.name.contains(query.trim(), true)
        }
    }
    var selectedPoint by remember { mutableStateOf<Point?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ЭнергоТочки", fontWeight = FontWeight.Bold) },
                actions = {
                    // Кнопка обновления
                    IconButton(onClick = { reloadTrigger++ }) {
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
                // Если данных нет — показываем инструкцию
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Warning, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Файл points.json не найден",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Скопируйте ваш файл points.json по пути:\n\nAndroid/data/com.energypoints.finder/files/\n\nПосле этого нажмите на значок обновления (круглая стрелка) в правом верхнем углу.",
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp
                    )
                }
            } else {
                // Если данные есть — показываем список
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
