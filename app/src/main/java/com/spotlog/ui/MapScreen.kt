package com.spotlog.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.spotlog.map.MarkerFactory
import com.spotlog.map.distanceBetween
import com.spotlog.theme.Spacing
import com.spotlog.util.FALLBACK_LOCATION
import com.spotlog.util.MAP_ZOOM
import com.spotlog.viewmodel.CategoryFilter
import com.spotlog.viewmodel.MapEvent
import com.spotlog.viewmodel.MapViewModel
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    savedStateHandle: SavedStateHandle,
    pickMode: Boolean = false,
    onPickModeResult: (Double, Double) -> Unit = { _, _ -> },
    onPlaceClick: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val places by viewModel.places.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val isLocating by viewModel.isLocating.collectAsState()
    val isInitialLocationLoaded by viewModel.isInitialLocationLoaded.collectAsState()

    var showSearchDialog by remember { mutableStateOf(false) }
    var showAddPlaceDialog by remember { mutableStateOf(false) }
    var pendingPlaceData by remember { mutableStateOf<Triple<Double, Double, String>?>(null) }

    var deletePlaceDialogData by remember { mutableStateOf<MapEvent.ShowDeletePlaceDialog?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.mapEvent.collect { ev ->
            when (ev) {
                is MapEvent.ShowDeletePlaceDialog -> deletePlaceDialogData = ev
            }
        }
    }

    var localMapView by remember { mutableStateOf<MapView?>(null) }
    val cachedMarkerDrawable = remember {
        MarkerFactory.getBitmap(context).toDrawable(context.resources)
    }

    var lastLocationTime by remember { mutableLongStateOf(0L) }
    var pendingAutoCenter by remember { mutableStateOf<GeoPoint?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) viewModel.fetchCurrentLocation()
        else Toast.makeText(context, "Разрешите доступ к геолокации в настройках", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> localMapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> localMapView?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!isInitialLocationLoaded && viewModel.hasLocationPermission()) {
            viewModel.fetchCurrentLocation(
                onResult = { pt ->
                    if (localMapView != null) localMapView?.controller?.animateTo(pt, MAP_ZOOM, 500L)
                    else pendingAutoCenter = pt
                    lastLocationTime = System.currentTimeMillis()
                },
                showError = false
            )
            viewModel.markInitialLocationLoaded()
        }
    }

    LaunchedEffect(localMapView) {
        pendingAutoCenter?.let { pt ->
            localMapView?.controller?.animateTo(pt, MAP_ZOOM, 500L)
            pendingAutoCenter = null
        }
    }

    LaunchedEffect(savedStateHandle, places, localMapView) {
        val placeId = savedStateHandle.get<Long>("focus_place_id")
        if (placeId != null && places.isNotEmpty() && localMapView != null) {
            savedStateHandle.remove<Long>("focus_place_id")
            val place = places.find { it.id == placeId }
            if (place != null) {
                localMapView?.controller?.animateTo(
                    GeoPoint(place.latitude, place.longitude),
                    MAP_ZOOM,
                    500L
                )
            }
        }
    }

    // ------------------- Создание маркеров -------------------
    LaunchedEffect(places) {
        val mapView = localMapView ?: return@LaunchedEffect
        mapView.overlays.removeAll { it is Marker }
        places.forEach { place ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(place.latitude, place.longitude)
                title = place.name
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = cachedMarkerDrawable
                setOnMarkerClickListener { _, _ ->
                    onPlaceClick(place.id)
                    true
                }
            }
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }

    // ------------------- Диалог поиска POI -------------------
    if (!pickMode && showSearchDialog) {
        val poiQuery by viewModel.poiSearchQuery.collectAsState()
        val activeFilterId by viewModel.activeFilterId.collectAsState()
        val filteredResults by viewModel.filteredSearchResults.collectAsState()
        val isSearching by viewModel.isSearching.collectAsState()

        Dialog(
            onDismissRequest = { showSearchDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.75f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                Column(Modifier.padding(Spacing.md)) {
                    Text("Рядом с вами", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(
                        value = poiQuery,
                        onValueChange = { viewModel.setPoiSearchQuery(it) },
                        placeholder = { Text("Поиск среди найденного…") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        item {
                            FilterChip(
                                selected = activeFilterId == null,
                                onClick = { viewModel.setCategoryFilter(null) },
                                label = { Text("Все") }
                            )
                        }
                        items(CategoryFilter.ALL_FILTERS) { filter ->
                            FilterChip(
                                selected = activeFilterId == filter.id,
                                onClick = {
                                    viewModel.setCategoryFilter(
                                        if (activeFilterId == filter.id) null else filter.id
                                    )
                                },
                                label = { Text(filter.label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    if (isSearching) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else if (filteredResults.isEmpty()) {
                        Text(
                            "Ничего не найдено поблизости.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.md)
                        )
                    } else {
                        LazyColumn(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            items(filteredResults, key = { "${it.name}_${it.lat}_${it.lon}" }) { poi ->
                                val dist = distanceBetween(
                                    currentLocation?.latitude ?: 0.0,
                                    currentLocation?.longitude ?: 0.0,
                                    poi.lat,
                                    poi.lon
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        pendingPlaceData = Triple(poi.lat, poi.lon, poi.name)
                                        showSearchDialog = false
                                        showAddPlaceDialog = true
                                    }
                                ) {
                                    Column(Modifier.padding(horizontal = Spacing.md, vertical = 10.dp)) {
                                        Text(poi.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                        Text(
                                            "${poi.category} · ${
                                                if (dist < 1000) "${dist.roundToInt()} м"
                                                else "%.1f км".format(Locale.US, dist / 1000)
                                            }",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showSearchDialog = false }) { Text("Закрыть") }
                    }
                }
            }
        }
    }

    // ------------------- Диалог добавления места (long press / POI click) -------------------
    if (!pickMode && showAddPlaceDialog) {
        val (lat, lon, name) = pendingPlaceData ?: Triple(0.0, 0.0, "")
        Dialog(
            onDismissRequest = { showAddPlaceDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                AddOldPlaceForm(
                    onPickOnMap = { showAddPlaceDialog = false },
                    pickedLat = lat,
                    pickedLon = lon,
                    onSave = { placeName, placeLat, placeLon, category, visits ->
                        if (visits.isEmpty()) {
                            viewModel.addManualVisit(
                                lat = placeLat,
                                lon = placeLon,
                                name = placeName,
                                category = category,
                                comment = "",
                                timestamp = System.currentTimeMillis()
                            )
                        } else {
                            visits.forEach { visit ->
                                viewModel.addManualVisit(
                                    lat = placeLat,
                                    lon = placeLon,
                                    name = placeName,
                                    category = category,
                                    comment = visit.comment,
                                    timestamp = visit.timestamp
                                )
                            }
                        }
                        showAddPlaceDialog = false
                        pendingPlaceData = null
                        Toast.makeText(context, "Место добавлено!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // ------------------- Диалог подтверждения удаления места -------------------
    deletePlaceDialogData?.let { data ->
        AlertDialog(
            onDismissRequest = { deletePlaceDialogData = null },
            title = { Text("Удалить место?") },
            text = { Text("Удалить место «${data.placeName}» и все связанные визиты/фото?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlace(data.placeId)
                    deletePlaceDialogData = null
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletePlaceDialogData = null }) { Text("Отмена") }
            }
        )
    }

    // ------------------- Сам экран карты -------------------
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (pickMode) "Выберите точку" else "Карта",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (pickMode) {
                        TextButton(onClick = { onPickModeResult(0.0, 0.0) }) {
                            Text("Отмена", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Карта
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(MAP_ZOOM)
                        controller.setCenter(FALLBACK_LOCATION)
                        localMapView = this
                        val eventsReceiver = object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                if (pickMode) {
                                    onPickModeResult(p.latitude, p.longitude)
                                    return true
                                }
                                return false
                            }

                            override fun longPressHelper(p: GeoPoint): Boolean {
                                if (pickMode) return false
                                pendingPlaceData = Triple(p.latitude, p.longitude, "")
                                showAddPlaceDialog = true
                                return true
                            }
                        }
                        overlays.add(MapEventsOverlay(eventsReceiver))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Оверлей поверх карты с кнопками и перекрестием
            Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {
                if (pickMode) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(32.dp)) {
                            val strokeWidth = 1.dp.toPx()
                            val len = size.width
                            val half = len / 2
                            drawLine(Color.White, Offset(half, 0f), Offset(half, len), strokeWidth, cap = StrokeCap.Round)
                            drawLine(Color.White, Offset(0f, half), Offset(len, half), strokeWidth, cap = StrokeCap.Round)
                            drawLine(Color.Black.copy(alpha = 0.3f), Offset(half + 1.dp.toPx(), 0f), Offset(half + 1.dp.toPx(), len), strokeWidth, cap = StrokeCap.Round)
                            drawLine(Color.Black.copy(alpha = 0.3f), Offset(0f, half + 1.dp.toPx()), Offset(len, half + 1.dp.toPx()), strokeWidth, cap = StrokeCap.Round)
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(24.dp)) {
                            val sw = 2.dp.toPx(); val len = size.width; val off = 4.dp.toPx()
                            drawLine(Color.White, Offset(len / 2, off), Offset(len / 2, len - off), sw, cap = StrokeCap.Round)
                            drawLine(Color.White, Offset(off, len / 2), Offset(len - off, len / 2), sw, cap = StrokeCap.Round)
                            drawLine(Color.Black.copy(alpha = 0.3f), Offset(len / 2 + 1.dp.toPx(), off + 1.dp.toPx()), Offset(len / 2 + 1.dp.toPx(), len - off + 1.dp.toPx()), sw, cap = StrokeCap.Round)
                            drawLine(Color.Black.copy(alpha = 0.3f), Offset(off + 1.dp.toPx(), len / 2 + 1.dp.toPx()), Offset(len - off + 1.dp.toPx(), len / 2 + 1.dp.toPx()), sw, cap = StrokeCap.Round)
                        }
                    }

                    // Кнопка «моё местоположение»
                    FloatingActionButton(
                        onClick = {
                            if (!viewModel.hasLocationPermission()) {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            } else {
                                viewModel.fetchCurrentLocation(
                                    onResult = { pt ->
                                        localMapView?.controller?.animateTo(pt, MAP_ZOOM, 500L)
                                        lastLocationTime = System.currentTimeMillis()
                                    }
                                )
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.md),
                        shape = MaterialTheme.shapes.large,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
                    ) {
                        if (isLocating) {
                            CircularProgressIndicator(
                                Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.MyLocation, contentDescription = "Местоположение")
                        }
                    }

                    // Кнопка «поиск POI»
                    FloatingActionButton(
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (now - lastLocationTime > 10_000) {
                                viewModel.fetchCurrentLocation(
                                    onResult = { pt ->
                                        viewModel.searchNearby(pt)
                                        showSearchDialog = true
                                        lastLocationTime = now
                                    }
                                )
                            } else {
                                viewModel.searchNearby(currentLocation ?: FALLBACK_LOCATION)
                                showSearchDialog = true
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.md),
                        shape = MaterialTheme.shapes.large,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Поиск POI")
                    }
                }
            }
        }
    }
}