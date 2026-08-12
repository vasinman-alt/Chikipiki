package com.spotlog.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.spotlog.data.SettingsDataStore
import com.spotlog.data.dao.VisitWithPlace
import com.spotlog.premium.PremiumManager
import com.spotlog.theme.Spacing
import com.spotlog.util.Categories
import com.spotlog.viewmodel.PlaceDetailViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreen(
    placeId: Long,
    viewModel: PlaceDetailViewModel,
    navController: NavHostController,
    onShowOnMap: () -> Unit,
    onBack: () -> Unit,
    openHistoricalOnStart: Boolean = false
) {
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore.getInstance(context) }
    val canUsePhotos by PremiumManager(context).isPremiumFlow.collectAsState(initial = true)

    var askPhotoOnVisit by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        settingsDataStore.askPhotoOnVisit.collect { value -> askPhotoOnVisit = value }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.error.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    // Все состояния должны быть объявлены до их использования в LaunchedEffect
    var editCommentDialog by remember { mutableStateOf<VisitWithPlace?>(null) }
    var deleteVisitDialog by remember { mutableStateOf<Long?>(null) }
    var showAddPhotoDialog by remember { mutableStateOf(false) }
    var showEditPlaceDialog by remember { mutableStateOf(false) }
    var selectedPhotoIndex by remember { mutableIntStateOf(0) }
    var showFullScreenPhoto by remember { mutableStateOf(false) }
    var showAddVisitDialog by remember { mutableStateOf(false) }
    var showHistoricalVisitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(placeId, openHistoricalOnStart) {
        viewModel.init(placeId)
        if (openHistoricalOnStart) {
            showHistoricalVisitDialog = true
        }
    }

    val place by viewModel.place.collectAsState()
    val visits by viewModel.visits.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val canCheckin by viewModel.canCheckin.collectAsState()
    val canCheckinReason by viewModel.canCheckinReason.collectAsState()

    if (place == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentPlace = place!!
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addPhotoToPlace(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(currentPlace.name, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditPlaceDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Редактировать место")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // ---------- Заголовок ----------
            item {
                Column {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Categories.resolveIcon(currentPlace.category),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.width(Spacing.md))
                        Column {
                            Text(
                                currentPlace.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                Categories.PREDEFINED.find { it.id == currentPlace.category }?.label
                                    ?: currentPlace.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (currentPlace.comment.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.sm))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                currentPlace.comment,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(Spacing.md)
                            )
                        }
                    }
                }
            }

            // ---------- Кнопки действий ----------
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(
                        onClick = { showAddVisitDialog = true },
                        enabled = canCheckin,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.AddLocation, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(canCheckinReason ?: "Отметиться")
                    }

                    IconButton(
                        onClick = { showHistoricalVisitDialog = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Filled.DateRange,
                            contentDescription = "Прошлый визит",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedButton(
                        onClick = onShowOnMap,
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Filled.Map, null, Modifier.size(18.dp))
                    }
                }
            }

            // ---------- Блок фото ----------
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Фото",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (canUsePhotos) {
                        IconButton(onClick = { showAddPhotoDialog = true }) {
                            Icon(
                                Icons.Filled.AddAPhoto,
                                contentDescription = "Добавить фото",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (photos.isNotEmpty()) {
                        TextButton(onClick = { navController.navigate("photo_gallery/${currentPlace.id}") }) {
                            Text("Все фото (${photos.size})")
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                if (photos.isEmpty()) {
                    Text(
                        "Нет фото",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        photos.take(3).forEachIndexed { index, photo ->
                            Box {
                                AsyncImage(
                                    model = File(photo.filePath),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable {
                                            selectedPhotoIndex = index
                                            showFullScreenPhoto = true
                                        },
                                    contentScale = ContentScale.Crop
                                )
                                if (photo.isCover) {
                                    Icon(
                                        Icons.Filled.Star,
                                        contentDescription = "Обложка",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(18.dp)
                                            .padding(2.dp)
                                    )
                                }
                            }
                        }
                        if (photos.size > 3) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { navController.navigate("photo_gallery/${currentPlace.id}") },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "+${photos.size - 3}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ---------- История визитов ----------
            item {
                Text(
                    "История визитов",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (visits.isEmpty()) {
                item {
                    Text(
                        "Визитов пока нет",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                itemsIndexed(visits, key = { _, visit -> visit.visitId }) { index, visit ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(20.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            if (index != visits.lastIndex) {
                                Box(
                                    Modifier
                                        .width(1.dp)
                                        .weight(1f)
                                        .heightIn(min = 32.dp)
                                        .background(MaterialTheme.colorScheme.outline)
                                )
                            }
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Column(Modifier.weight(1f).padding(bottom = Spacing.md)) {
                            Text(
                                dateFormat.format(Date(visit.timestamp)),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (visit.comment.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    visit.comment,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            visit.systemNote?.let {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                        IconButton(
                            onClick = { editCommentDialog = visit },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Редактировать",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { deleteVisitDialog = visit.visitId },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.DeleteForever,
                                contentDescription = "Удалить",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // ---------- Диалог добавления фото ----------
        if (showAddPhotoDialog) {
            AlertDialog(
                shape = MaterialTheme.shapes.medium,
                onDismissRequest = { showAddPhotoDialog = false },
                title = { Text("Добавить фото") },
                text = { Text("Выберите фотографию из галереи.") },
                confirmButton = {
                    TextButton(onClick = {
                        imagePickerLauncher.launch("image/*")
                        showAddPhotoDialog = false
                    }) { Text("Выбрать") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddPhotoDialog = false }) { Text("Отмена") }
                }
            )
        }

        // ---------- Диалог добавления визита ----------
        if (showAddVisitDialog) {
            AddVisitDialog(
                onDismiss = { showAddVisitDialog = false },
                onComplete = { timestamp, comment, photoUri ->
                    viewModel.addVisit(timestamp, comment, photoUri)
                    showAddVisitDialog = false
                },
                showPhotoOption = askPhotoOnVisit && canUsePhotos,
                initialName = currentPlace.name,
                initialCategory = currentPlace.category,
                initialLat = currentPlace.latitude,
                initialLon = currentPlace.longitude
            )
        }

        // ---------- Диалог добавления исторического визита ----------
        if (showHistoricalVisitDialog) {
            AddHistoricalVisitDialog(
                onDismiss = { showHistoricalVisitDialog = false },
                onComplete = { timestamp, comment, photoUri ->
                    viewModel.addVisit(timestamp, comment, photoUri?.let { Uri.parse(it) })
                    showHistoricalVisitDialog = false
                }
            )
        }

        // ---------- Диалог редактирования места ----------
        if (showEditPlaceDialog) {
            val firstVisit = visits.firstOrNull()
            if (firstVisit != null) {
                EditPlaceDialog(
                    place = currentPlace,
                    visit = firstVisit,
                    onDismiss = { showEditPlaceDialog = false },
                    onSave = { newName, newCategory, newComment ->
                        viewModel.updatePlaceDetails(newName, newCategory, newComment)
                        showEditPlaceDialog = false
                    }
                )
            } else {
                showEditPlaceDialog = false
            }
        }

        // ---------- Полноэкранный просмотр фото ----------
        if (showFullScreenPhoto && photos.isNotEmpty()) {
            FullScreenPhotoViewer(
                photos = photos,
                initialIndex = selectedPhotoIndex,
                onDismiss = { showFullScreenPhoto = false }
            )
        }

        // ---------- Удаление визита ----------
        deleteVisitDialog?.let { visitId ->
            AlertDialog(
                shape = MaterialTheme.shapes.medium,
                onDismissRequest = { deleteVisitDialog = null },
                title = { Text("Удалить визит?") },
                text = { Text("Это действие нельзя отменить.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteVisit(visitId)
                        deleteVisitDialog = null
                    }) { Text("Удалить") }
                },
                dismissButton = {
                    TextButton(onClick = { deleteVisitDialog = null }) { Text("Отмена") }
                }
            )
        }

        // ---------- Редактирование комментария визита ----------
        editCommentDialog?.let { visit ->
            var comment by remember { mutableStateOf(visit.comment) }
            AlertDialog(
                shape = MaterialTheme.shapes.medium,
                onDismissRequest = { editCommentDialog = null },
                title = { Text("Комментарий") },
                text = {
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Комментарий") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateVisitComment(visit.visitId, comment)
                        editCommentDialog = null
                    }) { Text("Сохранить") }
                },
                dismissButton = {
                    TextButton(onClick = { editCommentDialog = null }) { Text("Отмена") }
                }
            )
        }
    }
}