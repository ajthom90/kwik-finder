package com.ajthom90.kwikfinder.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ajthom90.kwikfinder.KwikFinderApplication
import com.ajthom90.kwikfinder.data.LiveDataStatus
import kotlinx.coroutines.launch

private object Routes {
    const val LIST = "list"
    const val FILTERS = "filters"
    const val STORE = "store/{storeId}"

    fun store(id: Int) = "store/$id"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: StoresViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as KwikFinderApplication
    val locationRepo = app.container.locationRepository

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants.values.any { it }
        locationRepo.refreshPermissionState()
        if (granted) {
            viewModel.startLocationUpdates()
        }
    }

    LaunchedEffect(Unit) {
        if (locationRepo.hasLocationPermission()) {
            viewModel.startLocationUpdates()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    var hasCenteredOnUser by remember { mutableStateOf(false) }
    var mapCenterLat by remember { mutableStateOf(state.referenceLat) }
    var mapCenterLon by remember { mutableStateOf(state.referenceLon) }

    LaunchedEffect(state.usingActualLocation, state.referenceLat, state.referenceLon) {
        if (state.usingActualLocation && !hasCenteredOnUser) {
            mapCenterLat = state.referenceLat
            mapCenterLon = state.referenceLon
        }
    }

    fun openStore(storeId: Int) {
        navController.navigate(Routes.store(storeId)) {
            launchSingleTop = true
        }
        scope.launch {
            if (sheetState.currentValue == SheetValue.PartiallyExpanded) {
                // Expand sheet so detail is usable.
                sheetState.expand()
            }
        }
    }

    BottomSheetScaffold(
        modifier = modifier.fillMaxSize(),
        scaffoldState = scaffoldState,
        sheetPeekHeight = 120.dp,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = {
            Column(Modifier.fillMaxWidth()) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.LIST,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(Routes.LIST) {
                        StoreList(
                            state = state,
                            isRefreshing = state.status is LiveDataStatus.Refreshing,
                            onSearchChange = viewModel::onSearchTextChange,
                            onSortChange = viewModel::setSortOrder,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onStoreClick = { openStore(it) },
                            onFiltersClick = {
                                navController.navigate(Routes.FILTERS)
                                scope.launch { sheetState.expand() }
                            },
                            onRefresh = { viewModel.refresh(force = true) },
                            searchField = {
                                OutlinedTextField(
                                    value = state.searchText,
                                    onValueChange = viewModel::onSearchTextChange,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    placeholder = { Text("Name, city, or store #") },
                                    singleLine = true,
                                )
                            },
                        )
                    }
                    composable(Routes.FILTERS) {
                        FilterSheet(
                            selected = state.selectedFeatures,
                            matchCount = viewModel.matchCountForFilters(),
                            onToggle = viewModel::toggleFeature,
                            onClear = viewModel::clearFilters,
                            onDone = { navController.popBackStack() },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = Routes.STORE,
                        arguments = listOf(
                            navArgument("storeId") { type = NavType.IntType },
                        ),
                    ) { entry ->
                        val storeId = entry.arguments?.getInt("storeId") ?: return@composable
                        val store = viewModel.storeById(storeId)
                        StoreDetail(
                            store = store,
                            isFavorite = state.favoriteIds.contains(storeId),
                            usingActualLocation = state.usingActualLocation,
                            referenceLat = state.referenceLat,
                            referenceLon = state.referenceLon,
                            pricesAsOfEpochMs = viewModel.pricesAsOfEpochMs(storeId),
                            isLive = viewModel.isLive(storeId),
                            liveStatus = state.status,
                            onToggleFavorite = { viewModel.toggleFavorite(storeId) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            MapPane(
                stores = state.mapStores,
                centerLat = mapCenterLat,
                centerLon = mapCenterLon,
                followUserOnce = state.usingActualLocation && !hasCenteredOnUser,
                onFollowedUser = { hasCenteredOnUser = true },
                onStoreClick = { openStore(it) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
