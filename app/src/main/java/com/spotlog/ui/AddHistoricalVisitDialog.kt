package com.spotlog.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.spotlog.theme.Spacing
import java.util.*

enum class HistoricalVisitStep { DATE, TIME, COMMENT, PHOTO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHistoricalVisitDialog(
    onDismiss: () -> Unit,
    onComplete: (timestamp: Long, comment: String, photoUri: String?) -> Unit
) {
    var step by remember { mutableStateOf(HistoricalVisitStep.DATE) }
    var pendingDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var comment by remember { mutableStateOf("") }

    var isTimeValid by remember { mutableStateOf(true) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        onComplete(pendingDate, comment, uri?.toString())
    }

    when (step) {
        HistoricalVisitStep.DATE -> {
            val todayMillis = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val initialDate = if (pendingDate > todayMillis) todayMillis else pendingDate
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate)
            var isDateValid by remember { mutableStateOf(true) }

            DatePickerDialog(
                onDismissRequest = onDismiss,
                shape = MaterialTheme.shapes.medium,
                confirmButton = {
                    TextButton(
                        onClick = {
                            val selected = datePickerState.selectedDateMillis ?: return@TextButton
                            if (selected <= todayMillis) {
                                pendingDate = selected
                                step = HistoricalVisitStep.TIME
                                isDateValid = true
                            } else {
                                isDateValid = false
                            }
                        },
                        enabled = datePickerState.selectedDateMillis != null
                    ) { Text("Далее") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
            ) {
                Column {
                    DatePicker(state = datePickerState)
                    if (!isDateValid) {
                        Text(
                            "Нельзя выбрать дату из будущего",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = Spacing.sm, start = Spacing.md, end = Spacing.md)
                        )
                    }
                }
            }
        }

        HistoricalVisitStep.TIME -> {
            val timeState = rememberTimePickerState(initialHour = 12, initialMinute = 0, is24Hour = true)

            Dialog(onDismissRequest = onDismiss) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text("Время посещения", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(Spacing.md))
                        TimePicker(state = timeState)
                        if (!isTimeValid) {
                            Text(
                                "Нельзя выбрать время в будущем",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = Spacing.sm)
                            )
                        }
                        Spacer(Modifier.height(Spacing.md))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = onDismiss) { Text("Отмена") }
                            Spacer(Modifier.width(Spacing.sm))
                            Button(
                                onClick = {
                                    val cal = Calendar.getInstance().apply {
                                        timeInMillis = pendingDate
                                        set(Calendar.HOUR_OF_DAY, timeState.hour)
                                        set(Calendar.MINUTE, timeState.minute)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                    val chosen = cal.timeInMillis
                                    if (chosen > System.currentTimeMillis()) {
                                        isTimeValid = false
                                    } else {
                                        pendingDate = chosen
                                        step = HistoricalVisitStep.COMMENT
                                        isTimeValid = true
                                    }
                                }
                            ) { Text("Далее") }
                        }
                    }
                }
            }
        }

        HistoricalVisitStep.COMMENT -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Комментарий") },
                text = {
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Комментарий к визиту (необязательно)") },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = { step = HistoricalVisitStep.PHOTO }) { Text("Далее") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
            )
        }

        HistoricalVisitStep.PHOTO -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Фото визита") },
                text = { Text("Прикрепить фото к этому посещению? После сохранения добавить фото будет нельзя.") },
                confirmButton = {
                    TextButton(onClick = { imagePickerLauncher.launch("image/*") }) { Text("Выбрать фото") }
                },
                dismissButton = {
                    TextButton(onClick = { onComplete(pendingDate, comment, null) }) { Text("Без фото") }
                }
            )
        }
    }
}