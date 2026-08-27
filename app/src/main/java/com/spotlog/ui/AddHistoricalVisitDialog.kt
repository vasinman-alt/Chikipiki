// ==== ФАЙЛ: AddHistoricalVisitDialog.kt ====
package com.spotlog.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.spotlog.theme.Spacing
import java.util.*

// FIX: шаг PHOTO убран. Исторические визиты не могут иметь фото.
enum class HistoricalVisitStep { DATE, TIME, COMMENT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHistoricalVisitDialog(
    onDismiss: () -> Unit,
    // FIX: убрали photoUri из сигнатуры, т.к. фото для исторических визитов запрещено
    onComplete: (timestamp: Long, comment: String) -> Unit
) {
    var step by remember { mutableStateOf(HistoricalVisitStep.DATE) }
    var pendingDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var comment by remember { mutableStateOf("") }
    var isTimeValid by remember { mutableStateOf(true) }

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
                        Text(
                            "Время посещения",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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

        // FIX: кнопка «Сохранить» вызывает onComplete сразу, без шага фото
        HistoricalVisitStep.COMMENT -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                shape = MaterialTheme.shapes.medium,
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
                    TextButton(onClick = { onComplete(pendingDate, comment) }) {
                        Text("Сохранить")
                    }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
            )
        }
    }
}
