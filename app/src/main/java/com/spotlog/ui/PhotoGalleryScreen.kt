// ==== ФАЙЛ: PhotoGalleryScreen.kt ====
package com.spotlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.spotlog.theme.Spacing
import com.spotlog.viewmodel.PhotoGalleryViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoGalleryScreen(
    viewModel: PhotoGalleryViewModel,
    onDismiss: () -> Unit,
    onPhotoClick: (Int) -> Unit
) {
    val photos by viewModel.photos.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.error.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    var deletePhotoId by remember { mutableStateOf<Long?>(null) }
    // NEW: состояние для открытия полноэкранного просмотрщика
    var fullScreenStartIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Галерея", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Закрыть")
                    }
                },
                actions = {
                    // NEW: открыть слайдер (полноэкранный просмотрщик) по первой фотографии
                    if (photos.isNotEmpty()) {
                        IconButton(onClick = { fullScreenStartIndex = 0 }) {
                            Icon(Icons.Filled.Slideshow, contentDescription = "Открыть слайдер")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.padding(padding)
        ) {
            itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.small)
                ) {
                    AsyncImage(
                        model = File(photo.filePath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onPhotoClick(index) }
                    )
                    if (photo.isCover) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Обложка",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f))
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleCoverPhoto(photo.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (photo.isCover) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (photo.isCover) "Убрать обложку" else "Сделать обложкой",
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { deletePhotoId = photo.id },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.DeleteForever,
                                contentDescription = "Удалить",
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Подтверждение удаления
        deletePhotoId?.let { photoId ->
            AlertDialog(
                onDismissRequest = { deletePhotoId = null },
                shape = MaterialTheme.shapes.medium,
                title = { Text("Удалить фото?") },
                text = { Text("Фото будет удалено навсегда.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deletePhoto(photoId)
                        deletePhotoId = null
                    }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { deletePhotoId = null }) { Text("Отмена") } }
            )
        }

        // NEW: полноэкранный просмотрщик
        fullScreenStartIndex?.let { startIndex ->
            FullScreenPhotoViewer(
                photos = photos,
                initialIndex = startIndex,
                onDismiss = { fullScreenStartIndex = null }
            )
        }
    }
}
