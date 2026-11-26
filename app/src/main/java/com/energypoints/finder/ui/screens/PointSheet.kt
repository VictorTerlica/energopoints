@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.energypoints.finder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.energypoints.finder.data.Point
import com.energypoints.finder.data.PointColors
import com.energypoints.finder.data.formatCoordinate
import com.energypoints.finder.ui.PointDraft
import com.energypoints.finder.ui.PointSheetState
import com.energypoints.finder.ui.components.GradientIcon
import com.energypoints.finder.ui.components.OwnershipBadge
import com.energypoints.finder.ui.components.colorFromHex

@Composable
fun PointSheet(
    sheet: PointSheetState,
    points: List<Point>,
    notes: Map<String, String>,
    isSaving: Boolean,
    isLocating: Boolean,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onEdit: (Point) -> Unit,
    onDeleteRequest: (Point) -> Unit,
    onSaveDraft: () -> Unit,
    onCancelEdit: () -> Unit,
    onDraftChange: (PointDraft) -> Unit,
    onSaveNote: (Point, String) -> Unit,
    onDeleteNote: (Point) -> Unit,
    onShowMap: (Point) -> Unit,
    onRequestLocation: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            when (sheet) {
                is PointSheetState.Details -> {
                    val point = points.firstOrNull { it.id == sheet.pointId }
                    if (point != null) {
                        DetailsContent(
                            point = point,
                            note = notes[point.noteKey],
                            onDismiss = onDismiss,
                            onEdit = onEdit,
                            onDeleteRequest = onDeleteRequest,
                            onSaveNote = onSaveNote,
                            onDeleteNote = onDeleteNote,
                            onShowMap = onShowMap
                        )
                    } else {
                        LaunchedEffect(sheet.pointId) {
                            onDismiss()
                        }
                    }
                }

                is PointSheetState.Editor -> EditorContent(
                    draft = sheet.draft,
                    isSaving = isSaving,
                    isLocating = isLocating,
                    onDraftChange = onDraftChange,
                    onSave = onSaveDraft,
                    onCancel = onCancelEdit,
                    onClose = onDismiss,
                    onRequestLocation = onRequestLocation
                )
            }

            if (isSaving) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .imePadding()
            )
        }
    }
}

@Composable
private fun DetailsContent(
    point: Point,
    note: String?,
    onDismiss: () -> Unit,
    onEdit: (Point) -> Unit,
    onDeleteRequest: (Point) -> Unit,
    onSaveNote: (Point, String) -> Unit,
    onDeleteNote: (Point) -> Unit,
    onShowMap: (Point) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var noteEditing by rememberSaveable(point.noteKey) { mutableStateOf(false) }
    var noteDraft by rememberSaveable(point.noteKey) {
        mutableStateOf(note.orEmpty())
    }

    LaunchedEffect(note, noteEditing) {
        if (!noteEditing) {
            noteDraft = note.orEmpty()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            GradientIcon(
                colorHex = point.resolvedColor,
                size = 52.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = point.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!point.ownership.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(5.dp))
                    OwnershipBadge(ownership = point.ownership)
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Меню точки"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Редактировать") },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onEdit(point)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDeleteRequest(point)
                        }
                    )
                }
            }

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть"
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "${point.district} • ${point.typeLabel}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!point.note.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Примечание",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = point.note.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        CoordinatesCard(point = point)

        Spacer(modifier = Modifier.height(12.dp))

        MyNoteCard(
            note = note,
            editing = noteEditing,
            draft = noteDraft,
            onDraftChange = { noteDraft = it },
            onStartEdit = {
                noteDraft = note.orEmpty()
                noteEditing = true
            },
            onSave = {
                onSaveNote(point, noteDraft)
                noteEditing = false
            },
            onCancel = {
                noteDraft = note.orEmpty()
                noteEditing = false
            },
            onDelete = {
                onDeleteNote(point)
                noteDraft = ""
                noteEditing = false
            }
        )

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = { onShowMap(point) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Показать на карте")
        }

        Spacer(modifier = Modifier.height(8.dp))
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
                    imageVector = Icons.Default.LocationOn,
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
                    imageVector = Icons.Default.Edit,
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
                                imageVector = Icons.Default.Delete,
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
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                            alpha = 0.75f
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(onClick = onStartEdit) {
                        Icon(
                            imageVector = Icons.Default.Add,
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
                            imageVector = Icons.Default.Edit,
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
private fun EditorContent(
    draft: PointDraft,
    isSaving: Boolean,
    isLocating: Boolean,
    onDraftChange: (PointDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    onRequestLocation: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradientIcon(
                colorHex = draft.iconColor,
                size = 52.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (draft.isNew) "Новая точка" else "Редактирование",
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Заполните поля и сохраните",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onClose,
                enabled = !isSaving
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть"
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = draft.code,
            onValueChange = { onDraftChange(draft.copy(code = it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
            label = { Text("Код") },
            placeholder = { Text("РП-102") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
            label = { Text("Название") },
            placeholder = { Text("РП-102 «Центральная»") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = draft.district,
            onValueChange = { onDraftChange(draft.copy(district = it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
            label = { Text("Район") },
            placeholder = { Text("Центральный") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Тип",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = draft.type == "рп",
                onClick = {
                    if (draft.type != "рп") {
                        val oldDefault = PointColors.defaultFor(draft.type)
                        val color = if (draft.iconColor == oldDefault) {
                            PointColors.defaultFor("рп")
                        } else {
                            draft.iconColor
                        }
                        onDraftChange(draft.copy(type = "рп", iconColor = color))
                    }
                },
                enabled = !isSaving,
                label = { Text("РП") }
            )
            FilterChip(
                selected = draft.type == "тп",
                onClick = {
                    if (draft.type != "тп") {
                        val oldDefault = PointColors.defaultFor(draft.type)
                        val color = if (draft.iconColor == oldDefault) {
                            PointColors.defaultFor("тп")
                        } else {
                            draft.iconColor
                        }
                        onDraftChange(draft.copy(type = "тп", iconColor = color))
                    }
                },
                enabled = !isSaving,
                label = { Text("ТП") }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Координаты",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = draft.latitude,
                onValueChange = { onDraftChange(draft.copy(latitude = it)) },
                modifier = Modifier.weight(1f),
                enabled = !isSaving,
                label = { Text("Широта") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = draft.longitude,
                onValueChange = { onDraftChange(draft.copy(longitude = it)) },
                modifier = Modifier.weight(1f),
                enabled = !isSaving,
                label = { Text("Долгота") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onRequestLocation,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving && !isLocating,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLocating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isLocating) {
                    "Определяем координаты…"
                } else {
                    "Уточнить по GPS"
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = draft.note,
            onValueChange = { onDraftChange(draft.copy(note = it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
            label = { Text("Примечание (необязательно)") },
            minLines = 3,
            maxLines = 8,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Принадлежность",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = draft.ownership == "РОССЕТИ",
                onClick = {
                    onDraftChange(draft.copy(ownership = "РОССЕТИ"))
                },
                enabled = !isSaving,
                label = { Text("РОССЕТИ") }
            )
            FilterChip(
                selected = draft.ownership == "ССК",
                onClick = {
                    onDraftChange(draft.copy(ownership = "ССК"))
                },
                enabled = !isSaving,
                label = { Text("ССК") }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Цвет иконки",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PointColors.palette.forEach { hex ->
                val selected = draft.iconColor.equals(hex, ignoreCase = true)
                val color = colorFromHex(hex)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape
                        )
                        .clickable(enabled = !isSaving) {
                            onDraftChange(draft.copy(iconColor = hex))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (color.luminance() > 0.5f) {
                                Color.Black
                            } else {
                                Color.White
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving && !isLocating,
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isSaving) "Сохранение…" else "Сохранить")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Отмена")
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
