package com.spotlog.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class VisitDialogStep { DATE, TIME, COMMENT, PHOTO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVisitDialog(
    onDismiss: () -> Unit,
    onComplete: (timestamp: Long, comment: String, photoUri: Uri?) -> Unit,
    showPhotoOption: Boolean = true,
    initialName: String = "",
    @Suppress("UNUSED_PARAMETER") initialCategory: String = "",
    initialLat: Double? = null,
    initialLon: Double? = null
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(VisitDialogStep.DATE) }
    var pendingDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var comment by remember { mutableStateOf("") }
    var addPhoto by remember { mutableStateOf(false) }
    var capturedPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val photoFile = remember {
        File(context.cacheDir, "visit_photo_${System.currentTimeMillis()}.jpg")
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            capturedPhotoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
        }
    }

    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // Шаг 1: Выбор даты
    if (step == VisitDialogStep.DATE) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = pendingDate
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { pendingDate = it }
                        step = VisitDialogStep.TIME
                    },
                    enabled = datePickerState.selectedDateMillis != null
                ) {
                    Text("Далее")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
        return
    }

    // Шаг 2: Выбор времени
    if (step == VisitDialogStep.TIME) {
        val calendar = Calendar.getInstance().apply { timeInMillis = pendingDate }
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
            is24Hour = true
        )
        Dialog(onDismissRequest = onDismiss) {
            Surface(shape = MaterialTheme.shapes.extraLarge) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Время посещения", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    TimePicker(state = timePickerState)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Отмена") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    timeInMillis = pendingDate
                                    set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                    set(Calendar.MINUTE, timePickerState.minute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                pendingDate = cal.timeInMillis
                                step = VisitDialogStep.COMMENT
                            }
                        ) {
                            Text("Далее")
                        }
                    }
                }
            }
        }
        return
    }

    // Шаг 3: Комментарий
    if (step == VisitDialogStep.COMMENT) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Column {
                    Text("Комментарий")
                    if (initialName.isNotEmpty()) {
                        Text(
                            "Место: $initialName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (initialLat != null && initialLon != null) {
                        Text(
                            "Координаты: %.5f, %.5f".format(initialLat, initialLon),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column {
                    Text(
                        "Дата и время: ${dateFormat.format(Date(pendingDate))} ${timeFormat.format(Date(pendingDate))}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Комментарий к визиту (необязательно)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (showPhotoOption) {
                            step = VisitDialogStep.PHOTO
                        } else {
                            onComplete(pendingDate, comment, null)
                        }
                    }
                ) {
                    Text(if (showPhotoOption) "Далее" else "Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        )
        return
    }

    // Шаг 4: Фото
    if (step == VisitDialogStep.PHOTO) {
        AlertDialog(
            onDismissRequest = { /* не даём закрыть без явного решения */ },
            title = { Text("Фото визита") },
            text = {
                Column {
                    Text("Прикрепить фото к этому посещению?")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = addPhoto,
                            onCheckedChange = { addPhoto = it }
                        )
                        Text("Сделать фото")
                        Spacer(Modifier.weight(1f))
                        if (addPhoto) {
                            TextButton(
                                onClick = {
                                    if (!photoFile.exists()) photoFile.createNewFile()
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        photoFile
                                    )
                                    cameraLauncher.launch(uri)
                                }
                            ) {
                                Icon(Icons.Filled.CameraAlt, null)
                                Spacer(Modifier.width(4.dp))
                                Text(if (capturedPhotoUri != null) "Фото сделано" else "Снять фото")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalPhotoUri = if (addPhoto) capturedPhotoUri else null
                        onComplete(pendingDate, comment, finalPhotoUri)
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onComplete(pendingDate, comment, null)
                    }
                ) {
                    Text("Без фото")
                }
            }
        )
    }
}