package com.spotlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.spotlog.theme.Spacing
import com.spotlog.util.Categories
import com.spotlog.viewmodel.PlaceCardUi
import com.spotlog.viewmodel.PlacesViewModel
import com.spotlog.viewmodel.SortMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(
    viewModel: PlacesViewModel,
    onPlaceClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onAddHistoricalVisit: (Long) -> Unit = {}  // новый колбэк для добавления прошлого визита
) {
    val cards by viewModel.placeCards.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    var showSearch by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var contextMenuPlaceId by remember { mutableStateOf<Long?>(null) }
    var showDeletePlaceDialog by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            if (showSearch) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Поиск по названию…") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                focusedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                                cursorColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            viewModel.setSearchQuery("")
                            showSearch = false
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Закрыть поиск", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Мои места", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Поиск", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Filled.Sort, contentDescription = "Сортировать", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("По дате (новые сверху)") },
                                onClick = {
                                    viewModel.setSortMode(SortMode.BY_DATE)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("По расстоянию") },
                                onClick = {
                                    viewModel.setSortMode(SortMode.BY_DISTANCE)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("По названию") },
                                onClick = {
                                    viewModel.setSortMode(SortMode.BY_ALPHABET)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить место")
            }
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            cards.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchQuery.isNotBlank()) "Ничего не найдено по запросу «$searchQuery»"
                        else "Пока нет сохранённых мест",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(cards, key = { it.placeId }) { card ->
                        Box {
                            PlaceCard(
                                card = card,
                                dateFormat = dateFormat,
                                onClick = { onPlaceClick(card.placeId) },
                                onLongClick = { contextMenuPlaceId = card.placeId }
                            )
                            DropdownMenu(
                                expanded = contextMenuPlaceId == card.placeId,
                                onDismissRequest = { contextMenuPlaceId = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Редактировать") },
                                    onClick = {
                                        contextMenuPlaceId = null
                                        onPlaceClick(card.placeId) // переход в детали места
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Добавить прошлый визит") },
                                    onClick = {
                                        contextMenuPlaceId = null
                                        onAddHistoricalVisit(card.placeId)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Удалить") },
                                    onClick = {
                                        contextMenuPlaceId = null
                                        showDeletePlaceDialog = card.placeId
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Диалог подтверждения удаления места
    showDeletePlaceDialog?.let { placeId ->
        AlertDialog(
            onDismissRequest = { showDeletePlaceDialog = null },
            title = { Text("Удалить место?") },
            text = { Text("Будут удалены все визиты, фото и информация о месте.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlace(placeId)
                    showDeletePlaceDialog = null
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePlaceDialog = null }) { Text("Отмена") }
            }
        )
    }
}

/* ------------------------------- карточка места ------------------------------- */
@Composable
private fun PlaceCard(
    card: PlaceCardUi,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongClick() },
                    onTap = { onClick() }
                )
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (card.coverPhotoPath != null) {
                    AsyncImage(
                        model = File(card.coverPhotoPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Categories.resolveIcon(card.category),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(Spacing.md))

            Column(Modifier.weight(1f)) {
                Text(
                    card.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    pluralizeVisits(card.visitCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Показ расстояния (если рассчитано)
            card.distanceMeters?.let { distance ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        if (distance < 1000) "${distance.toInt()} м"
                        else "%.1f км".format(Locale.US, distance / 1000),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/* вспомогательная функция для склонения «визит» */
private fun pluralizeVisits(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "$count визит"
        mod10 in 2..4 && mod100 !in 12..14 -> "$count визита"
        else -> "$count визитов"
    }
}