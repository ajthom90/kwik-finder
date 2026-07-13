package com.ajthom90.kwikfinder.ui

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ajthom90.kwikfinder.AppContainer
import com.ajthom90.kwikfinder.data.FavoritesStore
import com.ajthom90.kwikfinder.data.LiveDataStatus
import com.ajthom90.kwikfinder.data.Store
import com.ajthom90.kwikfinder.data.StoreFeature
import com.ajthom90.kwikfinder.data.StoreRepository
import com.ajthom90.kwikfinder.data.StoreSortOrder
import com.ajthom90.kwikfinder.data.filterSearchAndSort
import com.ajthom90.kwikfinder.data.mapMarkersNearest
import com.ajthom90.kwikfinder.data.matchesAllFeatures
import com.ajthom90.kwikfinder.location.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StoresUiState(
    val allStores: List<Store> = emptyList(),
    val filteredStores: List<Store> = emptyList(),
    val mapStores: List<Store> = emptyList(),
    val status: LiveDataStatus = LiveDataStatus.SnapshotOnly,
    val selectedFeatures: Set<StoreFeature> = emptySet(),
    val favoriteIds: Set<Int> = emptySet(),
    val searchText: String = "",
    val sortOrder: StoreSortOrder = StoreSortOrder.Nearest,
    val referenceLat: Double = LocationRepository.FALLBACK_LATITUDE,
    val referenceLon: Double = LocationRepository.FALLBACK_LONGITUDE,
    val usingActualLocation: Boolean = false,
    val filtersActive: Boolean = false,
)

/**
 * Orchestrates catalog, location, favorites, search, filters, and sort for the main UI.
 */
class StoresViewModel(
    private val repository: StoreRepository,
    private val favoritesStore: FavoritesStore,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val searchText = MutableStateFlow("")
    private val sortOrder = MutableStateFlow(StoreSortOrder.Nearest)
    private val selectedFeatures = MutableStateFlow<Set<StoreFeature>>(emptySet())

    private data class CatalogSlice(
        val stores: List<Store>,
        val status: LiveDataStatus,
        val favorites: Set<Int>,
        val location: Location?,
    )

    private data class FilterSlice(
        val search: String,
        val sort: StoreSortOrder,
        val features: Set<StoreFeature>,
    )

    private val catalogSlice = combine(
        repository.stores,
        repository.status,
        favoritesStore.ids,
        locationRepository.location,
    ) { stores, status, favorites, location ->
        CatalogSlice(stores, status, favorites, location)
    }

    private val filterSlice = combine(
        searchText,
        sortOrder,
        selectedFeatures,
    ) { search, sort, features ->
        FilterSlice(search, sort, features)
    }

    val uiState: StateFlow<StoresUiState> = combine(catalogSlice, filterSlice) { catalog, filters ->
        val lat = catalog.location?.latitude ?: LocationRepository.FALLBACK_LATITUDE
        val lon = catalog.location?.longitude ?: LocationRepository.FALLBACK_LONGITUDE
        val filtered = catalog.stores.filterSearchAndSort(
            selectedFeatures = filters.features,
            searchQuery = filters.search,
            sortOrder = filters.sort,
            favoriteIds = catalog.favorites,
            referenceLat = lat,
            referenceLon = lon,
        )
        StoresUiState(
            allStores = catalog.stores,
            filteredStores = filtered,
            mapStores = filtered.mapMarkersNearest(lat, lon),
            status = catalog.status,
            selectedFeatures = filters.features,
            favoriteIds = catalog.favorites,
            searchText = filters.search,
            sortOrder = filters.sort,
            referenceLat = lat,
            referenceLon = lon,
            usingActualLocation = catalog.location != null,
            filtersActive = filters.features.isNotEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StoresUiState(),
    )

    init {
        viewModelScope.launch {
            repository.refresh(force = true)
        }
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            repository.refresh(force = force)
        }
    }

    fun onSearchTextChange(value: String) {
        searchText.value = value
    }

    fun setSortOrder(order: StoreSortOrder) {
        sortOrder.value = order
    }

    fun toggleFeature(feature: StoreFeature) {
        val current = selectedFeatures.value.toMutableSet()
        if (!current.add(feature)) {
            current.remove(feature)
        }
        selectedFeatures.value = current
    }

    fun clearFilters() {
        selectedFeatures.value = emptySet()
    }

    fun toggleFavorite(storeId: Int) {
        viewModelScope.launch {
            favoritesStore.toggle(storeId)
        }
    }

    fun storeById(id: Int): Store? =
        repository.stores.value.firstOrNull { it.id == id }
            ?: uiState.value.allStores.firstOrNull { it.id == id }

    fun pricesAsOfEpochMs(storeId: Int): Long? = repository.pricesAsOfEpochMs(storeId)

    fun isLive(storeId: Int): Boolean = repository.isLive(storeId)

    fun matchCountForFilters(): Int {
        val features = selectedFeatures.value
        return repository.stores.value.count { it.matchesAllFeatures(features) }
    }

    fun startLocationUpdates() {
        locationRepository.startUpdates()
    }

    class Factory(
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(StoresViewModel::class.java)) {
                return StoresViewModel(
                    repository = container.storeRepository,
                    favoritesStore = container.favoritesStore,
                    locationRepository = container.locationRepository,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
