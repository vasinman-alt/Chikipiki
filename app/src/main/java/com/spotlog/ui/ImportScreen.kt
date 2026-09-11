package com.spotlog.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.spotlog.data.ImportResult
import com.spotlog.data.ImportValidationError
import com.spotlog.theme.Spacing
import com.spotlog.viewmodel.ImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onBack: () -> Unit,
    onPickOnMap: () -> Unit,
    pickedLat: Double?,
    pickedLon: Double?
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val importResult by viewModel.importResult.collectAsState()

    // Локальное состояние для диалога результата
    var dialogResult by remember { mutableStateOf<ImportResult?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importFromFile(it) }
    }

    // Перехватываем результат и показываем диалог
    LaunchedEffect(importResult) {
        importResult?.let { result ->
            dialogResult = result
            viewModel.clearResult()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Импорт данных", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                "Импортируйте данные из JSON-файла",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Поддерживаются: формат приложения и экспорт Foursquare.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.sm))

            Button(
                onClick = { filePickerLauncher.launch(arrayOf("application/json")) },
                enabled = !isLoading,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLoading) "Импорт..." else "Выбрать файл")
            }

            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = Spacing.md),
                color = MaterialTheme.colorScheme.outline
            )

            Text(
                "Или добавьте старое место вручную",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            AddOldPlaceForm(
                onPickOnMap = onPickOnMap,
                pickedLat = pickedLat,
                pickedLon = pickedLon,
                onSave = { name, lat, lon, category, visits ->
                    viewModel.addManualOldPlace(name, lat, lon, category, visits)
                }
            )
        }
    }

    // ==================== ДИАЛОГ РЕЗУЛЬТАТА ====================

    if (dialogResult != null) {
        val result = dialogResult!!
        val title = when (result) {
            is ImportResult.Success -> "Импорт завершён"
            is ImportResult.PartialSuccess -> "Импорт завершён с ошибками"
            is ImportResult.Error -> "Ошибка импорта"
        }

        AlertDialog(
            onDismissRequest = {
                dialogResult = null
                onBack()
            },
            title = { Text(title) },
            text = {
                Text(buildResultMessage(result))
            },
            confirmButton = {
                TextButton(onClick = {
                    dialogResult = null
                    onBack()
                }) {
                    Text("ОК")
                }
            }
        )
    }
}

/**
 * Формирует детальное сообщение о результате импорта.
 */
private fun buildResultMessage(result: ImportResult): String {
    return when (result) {
        is ImportResult.Success -> {
            val totalVisits = result.places.sumOf { it.visits.size }
            "Импортировано мест: ${result.places.size}\n" +
            "Импортировано визитов: $totalVisits"
        }
        is ImportResult.PartialSuccess -> {
            val totalVisits = result.places.sumOf { it.visits.size }
            val errorsText = result.errors.joinToString("\n") { "• ${errorDescription(it)}" }
            "Импортировано мест: ${result.places.size}\n" +
            "Импортировано визитов: $totalVisits\n\n" +
            "Ошибки (${result.errors.size}):\n$errorsText"
        }
        is ImportResult.Error -> {
            "Не удалось выполнить импорт.\n${errorDescription(result.error)}"
        }
    }
}

/**
 * Человекочитаемое описание ошибки.
 */
private fun errorDescription(error: ImportValidationError): String {
    return when (error) {
        is ImportValidationError.UnsupportedVersion ->
            "Неподдерживаемая версия файла: ${error.version}"
        is ImportValidationError.InvalidJson ->
            "Неверный формат JSON"
        is ImportValidationError.EmptyPlaceName ->
            "Пустое название места"
        is ImportValidationError.InvalidCoordinates ->
            "Неверные координаты"
        is ImportValidationError.InvalidTimestamp ->
            "Неверный формат времени"
        else ->
            "Неизвестная ошибка"
    }
}