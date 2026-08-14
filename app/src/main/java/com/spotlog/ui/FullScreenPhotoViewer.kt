// ==== ФАЙЛ: FullScreenPhotoViewer.kt ====
package com.spotlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.spotlog.data.entity.PhotoEntity
import com.spotlog.theme.Spacing
import java.io.File
import kotlin.math.abs

/**
 * Полноэкранный просмотрщик фото.
 *
 * Архитектура жестов:
 *  - HorizontalPager отвечает за свайп между фото.
 *  - ZoomableImage отвечает за pinch‑to‑zoom (двумя пальцами) и панорамирование
 *    при увеличенном изображении (один палец + scale > 1f).
 *  - Жест «потребляется» (consume) только в тех случаях, когда он относится
 *    к ZoomableImage. В остальных случаях событие свободно доходит до родителя,
 *    и HorizontalPager листает страницы.
 */
@Composable
fun FullScreenPhotoViewer(
    photos: List<PhotoEntity>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    if (photos.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, photos.size - 1)
    ) { photos.size }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState) { page ->
                ZoomableImage(photo = photos[page])
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.md)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
            }

            if (photos.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${photos.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(Spacing.md)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = Spacing.sm, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(photo: PhotoEntity) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var imageSize by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                imageSize = Offset(size.width.toFloat(), size.height.toFloat())
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pointers = event.changes
                        val pressedCount = pointers.count { it.pressed }

                        when {
                            // ---- Двух‑пальцевый pinch‑to‑zoom + pan ----
                            pressedCount >= 2 -> {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()

                                val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                scale = newScale

                                if (newScale > 1f) {
                                    val maxOffsetX = (imageSize.x * (newScale - 1f)) / 2f
                                    val maxOffsetY = (imageSize.y * (newScale - 1f)) / 2f
                                    offset = Offset(
                                        x = (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                                        y = (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                    )
                                } else {
                                    offset = Offset.Zero
                                }

                                pointers.forEach { it.consume() }
                            }

                            // ---- Один палец при увеличенном изображении: pan внутри фото ----
                            pressedCount == 1 && scale > 1f -> {
                                val panChange = event.calculatePan()
                                val maxOffsetX = (imageSize.x * (scale - 1f)) / 2f
                                val maxOffsetY = (imageSize.y * (scale - 1f)) / 2f
                                offset = Offset(
                                    x = (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                                    y = (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                )
                                if (abs(panChange.x) > 0.5f || abs(panChange.y) > 0.5f) {
                                    pointers.forEach { it.consume() }
                                }
                            }
                            // ---- Один палец при scale == 1f: ничего не потребляем
                            //      → HorizontalPager получает жест и листает страницу ----
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        AsyncImage(
            model = File(photo.filePath),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit
        )
    }
}
