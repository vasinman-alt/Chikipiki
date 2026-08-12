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
    val snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importFromFile(it) }
    }

    LaunchedEffect(importResult) {
        importResult?.let { result ->
            val message = when (result) {
                is ImportResult.Success -> "Импортировано ${result.places.size} мест"
                is ImportResult.PartialSuccess -> "Импортировано ${result.places.size} мест, ошибок: ${result.errors.size}"
                is ImportResult.Error -> when (val error = result.error) {
                    is ImportValidationError.UnsupportedVersion -> "Неподдерживаемая версия: ${error.version}"
                    is ImportValidationError.InvalidJson -> "Неверный JSON"
                    is ImportValidationError.EmptyPlaceName -> "Пустое название места"
                    is ImportValidationError.InvalidCoordinates -> "Неверные координаты"
                    is ImportValidationError.InvalidTimestamp -> "Неверный формат времени"
                    else -> "Ошибка импорта"
                }
            }
            snackbarHostState.showSnackbar(message)
            viewModel.clearResult()
            onBack()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Импорт данных", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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
                "Файл должен соответствовать формату, описанному в документации.",
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
}
