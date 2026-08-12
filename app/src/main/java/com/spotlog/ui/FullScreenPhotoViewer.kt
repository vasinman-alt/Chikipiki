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
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
 * Ключевая идея: HorizontalPager отвечает за свайп между фото, а ZoomableImage — за зум/пан
 * внутри увеличенного изображения. Чтобы оба работали одновременно, мы вручную разбираем
 * жесты: потребляем событие только когда нужно (реальный pinch‑to‑zoom или pan при scale>1).
 *
 * Когда пользователь делает один‑пальцевый горизонтальный свайп при scale==1f,
 * событие НЕ потребляется – оно всплывает к HorizontalPager и переключает страницу.
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

/**
 * Изображение, которое умеет зум‑иться двумя пальцами и панорамироваться при увеличении.
 * Один‑пальцевый свайп при scale == 1f НЕ потребляется → передаётся родительскому HorizontalPager.
 */
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
                // Ручная обработка жестов: реагируем только на pinch‑to‑zoom или pan при увеличении.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pointers = event.changes
                        val pressedCount = pointers.count { it.pressed }

                        if (pressedCount >= 2) {
                            // ---- Двух‑пальцевый жест (pinch‑to‑zoom + pan) ----
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            // Применяем зум
                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            scale = newScale

                            // Применяем pan только если изображение увеличено
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

                            // Помечаем все события как потреблённые — они не должны уйти в Pager
                            pointers.forEach { it.consume() }
                        } else if (pressedCount == 1 && scale > 1f) {
                            // ---- Один палец при увеличенном изображении: pan внутри фото ----
                            val panChange = event.calculatePan()
                            val maxOffsetX = (imageSize.x * (scale - 1f)) / 2f
                            val maxOffsetY = (imageSize.y * (scale - 1f)) / 2f
                            offset = Offset(
                                x = (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                                y = (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                            )
                            // Потребляем только те изменения, которые относятся к pan‑у.
                            // Горизонтальное смещение > вертикального → блокируем весь жест,
                            // иначе Pager всё равно сработает и будет «рвать» картинку.
                            if (abs(panChange.x) > 0.5f || abs(panChange.y) > 0.5f) {
                                pointers.forEach { it.consume() }
                            }
                        }
                        // ---- Один палец при scale == 1f: НЕ потребляем → HorizontalPager сработает ----
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
