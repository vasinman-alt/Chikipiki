// ==== ФАЙЛ: NavGraph.kt ====
package com.spotlog.navigation

import android.app.Application
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.spotlog.ui.*
import com.spotlog.viewmodel.*

object Routes {
    const val PLACES = "places"
    const val MAP = "map?pickMode={pickMode}"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val IMPORT = "import"
    const val PHOTO_GALLERY = "photo_gallery/{placeId}"

    // NEW: маршрут с необязательным флагом открытия исторического визита
    const val PLACE_DETAIL = "place_detail/{placeId}?openHistorical={openHistorical}"
}

data class BottomNavItem(
    val route: String,
    val navigateRoute: String,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Routes.PLACES, Routes.PLACES, "Места", Icons.Filled.LocationCity),
    BottomNavItem(Routes.MAP, "map?pickMode=false", "Карта", Icons.Filled.Map),
    BottomNavItem(Routes.STATS, Routes.STATS, "Статистика", Icons.Filled.BarChart),
    BottomNavItem(Routes.SETTINGS, Routes.SETTINGS, "Настройки", Icons.Filled.Settings)
)

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val mapViewModel: MapViewModel = viewModel()
    val placesViewModel: PlacesViewModel = viewModel()
    val statsViewModel: StatisticsViewModel = viewModel()

    Scaffold(
        bottomBar = {
            if (currentRoute in listOf(Routes.PLACES, Routes.MAP, Routes.STATS, Routes.SETTINGS)) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.navigateRoute) {
                                        popUpTo(Routes.PLACES) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.PLACES,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.PLACES) {
                PlacesScreen(
                    viewModel = placesViewModel,
                    onPlaceClick = { placeId ->
                        navController.navigate("place_detail/$placeId")
                    },
                    onAddClick = {
                        navController.navigate("map?pickMode=false")
                    },
                    // FIX: передаём колбэк, который открывает экран места
                    // с флагом "открыть диалог исторического визита сразу".
                    onAddHistoricalVisit = { placeId ->
                        navController.navigate("place_detail/$placeId?openHistorical=true")
                    }
                )
            }

            composable(
                route = Routes.MAP,
                arguments = listOf(navArgument("pickMode") {
                    type = NavType.BoolType
                    defaultValue = false
                })
            ) { backStackEntry ->
                val pickMode = backStackEntry.arguments?.getBoolean("pickMode") ?: false
                MapScreen(
                    viewModel = mapViewModel,
                    savedStateHandle = backStackEntry.savedStateHandle,
                    pickMode = pickMode,
                    onPlaceClick = { placeId ->
                        navController.navigate("place_detail/$placeId")
                    },
                    onPickModeResult = { lat, lon ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("picked_lat", lat)
                        navController.previousBackStackEntry?.savedStateHandle?.set("picked_lon", lon)
                        navController.popBackStack()
                    }
                )
            }

            composable(Routes.STATS) {
                StatisticsScreen(
                    viewModel = statsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onImportClick = { navController.navigate(Routes.IMPORT) }
                )
            }

            composable(Routes.IMPORT) { backStackEntry ->
                val importViewModel: ImportViewModel = viewModel()
                val pickedLat = backStackEntry.savedStateHandle.get<Double>("picked_lat")
                val pickedLon = backStackEntry.savedStateHandle.get<Double>("picked_lon")
                ImportScreen(
                    viewModel = importViewModel,
                    onBack = { navController.popBackStack() },
                    onPickOnMap = {
                        navController.navigate("map?pickMode=true")
                    },
                    pickedLat = pickedLat,
                    pickedLon = pickedLon
                )
            }

            // FIX: маршрут теперь принимает query‑параметр openHistorical
            composable(
                route = Routes.PLACE_DETAIL,
                arguments = listOf(
                    navArgument("placeId") { type = NavType.LongType },
                    navArgument("openHistorical") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val placeId = backStackEntry.arguments?.getLong("placeId") ?: return@composable
                val openHistorical = backStackEntry.arguments?.getBoolean("openHistorical") ?: false
                val placeDetailViewModel: PlaceDetailViewModel = viewModel()
                PlaceDetailScreen(
                    placeId = placeId,
                    viewModel = placeDetailViewModel,
                    navController = navController,
                    onShowOnMap = {
                        navController.navigate("map?pickMode=false") {
                            popUpTo(Routes.PLACES) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                        navController.getBackStackEntry("map?pickMode=false")
                            .savedStateHandle["focus_place_id"] = placeId
                    },
                    onBack = { navController.popBackStack() },
                    // NEW: передаём флаг в экран
                    openHistoricalOnStart = openHistorical
                )
            }

            composable(
                route = Routes.PHOTO_GALLERY,
                arguments = listOf(navArgument("placeId") { type = NavType.LongType })
            ) { backStackEntry ->
                val placeId = backStackEntry.arguments?.getLong("placeId") ?: return@composable
                val context = LocalContext.current
                val application = context.applicationContext as Application
                val galleryViewModel = remember(placeId) {
                    PhotoGalleryViewModel(application, placeId)
                }

                var showFullScreen by remember { mutableStateOf(false) }
                var selectedPhotoIndex by remember { mutableIntStateOf(0) }
                val photos by galleryViewModel.photos.collectAsState()

                PhotoGalleryScreen(
                    viewModel = galleryViewModel,
                    onDismiss = { navController.popBackStack() },
                    onPhotoClick = { index ->
                        selectedPhotoIndex = index
                        showFullScreen = true
                    }
                )

                if (showFullScreen) {
                    FullScreenPhotoViewer(
                        photos = photos,
                        initialIndex = selectedPhotoIndex,
                        onDismiss = { showFullScreen = false }
                    )
                }
            }
        }
    }
}
