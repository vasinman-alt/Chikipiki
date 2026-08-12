package com.spotlog.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spotlog.data.dao.VisitWithPlace
import com.spotlog.data.entity.PlaceEntity
import com.spotlog.theme.Spacing
import com.spotlog.util.Categories

@Composable
fun EditPlaceDialog(
    place: PlaceEntity,
    visit: VisitWithPlace,
    onDismiss: () -> Unit,
    onSave: (name: String, category: String, comment: String) -> Unit
) {
    var name by remember { mutableStateOf(place.name) }
    var category by remember { mutableStateOf(place.category) }
    var comment by remember { mutableStateOf(visit.comment) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.medium,
        title = { Text("Редактировать место") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Spacing.sm))

                Box {
                    OutlinedButton(onClick = { showCategoryMenu = true }, shape = MaterialTheme.shapes.small) {
                        Text("Категория: ${Categories.PREDEFINED.find { it.id == category }?.label ?: category}")
                    }
                    DropdownMenu(expanded = showCategoryMenu, onDismissRequest = { showCategoryMenu = false }) {
                        Categories.PREDEFINED.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.label) },
                                onClick = { category = cat.id; showCategoryMenu = false },
                                leadingIcon = { Icon(cat.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                        }
                        var customCategory by remember { mutableStateOf("") }
                        DropdownMenuItem(
                            text = {
                                OutlinedTextField(
                                    value = customCategory,
                                    onValueChange = { customCategory = it },
                                    label = { Text("Своя категория") },
                                    shape = MaterialTheme.shapes.small,
                                    singleLine = true
                                )
                            },
                            onClick = {
                                if (customCategory.isNotBlank()) {
                                    category = customCategory
                                    showCategoryMenu = false
                                    customCategory = ""
                                }
                            },
                            leadingIcon = { Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Комментарий к месту") },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Координаты: %.5f, %.5f".format(place.latitude, place.longitude),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(name.ifBlank { "Без названия" }, category, comment)
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
