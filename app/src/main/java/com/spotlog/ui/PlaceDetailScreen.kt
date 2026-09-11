package com.spotlog.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.spotlog.data.SettingsDataStore
import com.spotlog.data.dao.VisitWithPlace
import com.spotlog.data.entity.VisitSource
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
    val canUsePhotos by PremiumManager.isPremiumFlow(context).collectAsState(initial = true)
    var askPhotoOnVisit by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        settingsDataStore.askPhotoOnVisit.collect { value -> askPhotoOnVisit = value }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.error.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    // Состояния диалогов
    var editCommentDialog by remember { mutableStateOf<VisitWithPlace?>(null) }
    var editCommentText by remember { mutableStateOf("") }
    var deleteVisitDialog by remember { mutableStateOf<Long?>(null) }
    var showAddPhotoDialog by remember { mutableStateOf(false) }
    var showEditPlaceDialog by remember { mutableStateOf(false) }
    var selectedPhotoIndex by remember { mutableIntStateOf(0) }
    var showFullScreenPhoto by remember { mutableStateOf(false) }
    var showHistoricalVisitDialog by remember { mutableStateOf(false) }

    // Состояния для диалога редактирования места
    var editPlaceName by remember { mutableStateOf("") }
    var editPlaceCategory by remember { mutableStateOf("") }
    var editPlaceComment by remember { mutableStateOf("") }

    // Состояние камеры для конкретного визита
    var pendingCameraVisitId by remember { mutableStateOf<Long?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

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

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val visitId = pendingCameraVisitId
        val file = pendingCameraFile
        if (success && visitId != null && file != null) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            viewModel.addPhotoToVisit(visitId, uri)
        } else if (success && visitId == null && file != null) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            viewModel.addPhotoToPlace(uri)
        }
        pendingCameraVisitId = null
        pendingCameraFile = null
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
                    IconButton(onClick = {
                        editPlaceName = currentPlace.name
                        editPlaceCategory = currentPlace.category
                        editPlaceComment = currentPlace.comment
                        showEditPlaceDialog = true
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Редактировать место")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
                        onClick = { viewModel.addVisit() },
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
                        TextButton(onClick = {
                            navController.navigate("photo_gallery/${currentPlace.id}")
                        }) {
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
                                    .clickable {
                                        navController.navigate("photo_gallery/${currentPlace.id}")
                                    },
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
                itemsIndexed(
                    items = visits,
                    key = { _, visit -> visit.visitId }
                ) { _, visit ->
                    val isManual = visit.source == VisitSource.MANUAL.name
                    ListItem(
                        headlineContent = {
                            Text(
                                dateFormat.format(Date(visit.timestamp)),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        supportingContent = {
                            Column {
                                if (visit.comment.isNotBlank()) {
                                    Text(
                                        visit.comment,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                if (visit.systemNote != null) {
                                    Text(
                                        visit.systemNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            Row {
                                if (canUsePhotos && isManual) {
                                    IconButton(onClick = {
                                        val file = createTempImageFile(context)
                                        if (file != null) {
                                            pendingCameraVisitId = visit.visitId
                                            pendingCameraFile = file
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            cameraLauncher.launch(uri)
                                        }
                                    }) {
                                        Icon(
                                            Icons.Filled.PhotoCamera,
                                            contentDescription = "Фото к визиту",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    editCommentText = visit.comment
                                    editCommentDialog = visit
                                }) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = "Редактировать"
                                    )
                                }
                                IconButton(onClick = {
                                    deleteVisitDialog = visit.visitId
                                }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Удалить визит",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // ==================== ДИАЛОГИ ====================

    // 1. Редактирование комментария визита
    if (editCommentDialog != null) {
        AlertDialog(
            onDismissRequest = { editCommentDialog = null },
            title = { Text("Редактировать комментарий") },
            text = {
                OutlinedTextField(
                    value = editCommentText,
                    onValueChange = { editCommentText = it },
                    label = { Text("Комментарий") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editCommentDialog?.let { visit ->
                        viewModel.updateVisitComment(visit.visitId, editCommentText)
                    }
                    editCommentDialog = null
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editCommentDialog = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    // 2. Подтверждение удаления визита
    if (deleteVisitDialog != null) {
        AlertDialog(
            onDismissRequest = { deleteVisitDialog = null },
            title = { Text("Удалить визит") },
            text = { Text("Удалить этот визит? Если это единственный визит, место тоже будет удалено.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteVisitDialog?.let { visitId ->
                        viewModel.deleteVisit(visitId)
                    }
                    deleteVisitDialog = null
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteVisitDialog = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    // 3. Добавление фото к месту (выбор: галерея или камера)
    if (showAddPhotoDialog) {
        AlertDialog(
            onDismissRequest = { showAddPhotoDialog = false },
            title = { Text("Добавить фото") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showAddPhotoDialog = false
                            imagePickerLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Из галереи")
                    }
                    TextButton(
                        onClick = {
                            showAddPhotoDialog = false
                            val file = createTempImageFile(context)
                            if (file != null) {
                                pendingCameraVisitId = null
                                pendingCameraFile = file
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                cameraLauncher.launch(uri)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PhotoCamera, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Сделать фото")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddPhotoDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // 4. Редактирование места
    if (showEditPlaceDialog) {
        AlertDialog(
            onDismissRequest = { showEditPlaceDialog = false },
            title = { Text("Редактировать место") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = editPlaceName,
                        onValueChange = { editPlaceName = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    )
                    OutlinedTextField(
                        value = editPlaceCategory,
                        onValueChange = { editPlaceCategory = it },
                        label = { Text("Категория") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    )
                    OutlinedTextField(
                        value = editPlaceComment,
                        onValueChange = { editPlaceComment = it },
                        label = { Text("Комментарий") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updatePlaceDetails(
                        editPlaceName,
                        editPlaceCategory,
                        editPlaceComment
                    )
                    showEditPlaceDialog = false
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditPlaceDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // 5. Полноэкранный просмотр фото
    if (showFullScreenPhoto && photos.isNotEmpty()) {
        val photo = photos.getOrElse(selectedPhotoIndex) { null }
        if (photo != null) {
            Dialog(onDismissRequest = { showFullScreenPhoto = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AsyncImage(
                        model = File(photo.filePath),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        if (!photo.isCover) {
                            IconButton(onClick = {
                                viewModel.setCoverPhoto(photo.id)
                            }) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = "Сделать обложкой",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = {
                            viewModel.deletePhoto(photo.id)
                            showFullScreenPhoto = false
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Удалить фото",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(onClick = { showFullScreenPhoto = false }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Закрыть"
                            )
                        }
                    }
                }
            }
        }
    }

    // 6. Диалог добавления исторического визита
    if (showHistoricalVisitDialog) {
        AddHistoricalVisitDialog(
            onDismiss = { showHistoricalVisitDialog = false },
            onComplete = { timestamp, comment ->
                viewModel.addHistoricalVisit(timestamp, comment)
                showHistoricalVisitDialog = false
            }
        )
    }
}

private fun createTempImageFile(context: android.content.Context): File? {
    val storageDir = File(context.cacheDir, "camera_photos")
    storageDir.mkdirs()
    return try {
        File.createTempFile(
            "IMG_${System.currentTimeMillis()}_",
            ".jpg",
            storageDir
        )
    } catch (e: Exception) {
        null
    }
}