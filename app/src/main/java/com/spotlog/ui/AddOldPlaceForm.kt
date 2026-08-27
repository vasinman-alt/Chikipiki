package com.spotlog.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spotlog.theme.Spacing
import com.spotlog.util.Categories
import java.text.SimpleDateFormat
import java.util.*

data class PendingVisit(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val comment: String = "",
    val photoUri: String? = null,
    var photoDecisionMade: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOldPlaceForm(
    onPickOnMap: () -> Unit,
    pickedLat: Double?,
    pickedLon: Double?,
    onSave: (name: String, lat: Double, lon: Double, category: String, visits: List<PendingVisit>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("custom") }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var manualLat by remember { mutableStateOf(pickedLat?.toString() ?: "") }
    var manualLon by remember { mutableStateOf(pickedLon?.toString() ?: "") }

    LaunchedEffect(pickedLat, pickedLon) {
        if (pickedLat != null && pickedLon != null) {
            manualLat = pickedLat.toString()
            manualLon = pickedLon.toString()
        }
    }

    val visits = remember { mutableStateListOf<PendingVisit>() }
    var showVisitDialog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Column(modifier = Modifier.padding(Spacing.md)) {
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Название места") },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Spacing.sm))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = manualLat, onValueChange = { manualLat = it },
                    label = { Text("Широта") }, singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = manualLon, onValueChange = { manualLon = it },
                    label = { Text("Долгота") }, singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            OutlinedButton(onClick = onPickOnMap, shape = MaterialTheme.shapes.small) {
                Icon(Icons.Filled.Map, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("На карте")
            }
        }

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
                        leadingIcon = { Icon(cat.icon, null, tint = MaterialTheme.colorScheme.primary) }
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))

        Text("Визиты (${visits.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier.heightIn(max = 240.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(visits, key = { it.id }) { visit ->
                val index = visits.indexOfFirst { it.id == visit.id }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (visit.photoUri != null) Icons.Filled.Photo else Icons.Filled.ImageNotSupported,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            dateFormat.format(Date(visit.timestamp)),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { visits.removeAt(index) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Удалить визит", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        TextButton(onClick = { showVisitDialog = true }) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Добавить дату посещения")
        }

        if (showVisitDialog) {
            AddHistoricalVisitDialog(
                onDismiss = { showVisitDialog = false },
                onComplete = { timestamp, comment -> // ИСПРАВЛЕНО: убран photoUri
                    visits.add(PendingVisit(timestamp = timestamp, comment = comment, photoUri = null))
                    showVisitDialog = false
                }
            )
        }

        Spacer(Modifier.height(Spacing.md))

        Button(
            enabled = name.isNotBlank() && manualLat.toDoubleOrNull() != null && manualLon.toDoubleOrNull() != null && visits.isNotEmpty(),
            shape = MaterialTheme.shapes.small,
            onClick = {
                onSave(
                    name.ifBlank { "Без названия" },
                    manualLat.toDoubleOrNull()!!,
                    manualLon.toDoubleOrNull()!!,
                    category,
                    visits.toList()
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Сохранить место с ${visits.size} визитами") }
    }
}