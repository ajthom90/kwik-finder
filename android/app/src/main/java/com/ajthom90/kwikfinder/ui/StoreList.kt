package com.ajthom90.kwikfinder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ajthom90.kwikfinder.data.EvChargingStatus
import com.ajthom90.kwikfinder.data.LiveDataStatus
import com.ajthom90.kwikfinder.data.Store
import com.ajthom90.kwikfinder.data.StoreFeature
import com.ajthom90.kwikfinder.data.StoreSortOrder
import com.ajthom90.kwikfinder.data.badgeFeatures
import com.ajthom90.kwikfinder.data.brandedName
import com.ajthom90.kwikfinder.data.distanceMeters
import com.ajthom90.kwikfinder.data.label
import com.ajthom90.kwikfinder.data.shortAddress
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreList(
    state: StoresUiState,
    isRefreshing: Boolean,
    onSearchChange: (String) -> Unit,
    onSortChange: (StoreSortOrder) -> Unit,
    onToggleFavorite: (Int) -> Unit,
    onStoreClick: (Int) -> Unit,
    onFiltersClick: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    searchField: @Composable () -> Unit = {},
) {
    var showLiveSuccessBanner by remember { mutableStateOf(false) }

    LaunchedEffect(state.status) {
        when (state.status) {
            is LiveDataStatus.Live -> {
                showLiveSuccessBanner = true
                delay(2_500)
                showLiveSuccessBanner = false
            }
            else -> showLiveSuccessBanner = false
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Toolbar row: sort + title + filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SortMenu(
                sortOrder = state.sortOrder,
                onSortChange = onSortChange,
            )
            Text(
                text = "Kwik Trip Finder",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onFiltersClick) {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (state.filtersActive) {
                        "Filters (${state.selectedFeatures.size})"
                    } else {
                        "Filters"
                    },
                )
            }
        }

        searchField()

        PullToRefreshBox(
            isRefreshing = isRefreshing || state.status is LiveDataStatus.Refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(key = "status") {
                    StatusBanner(
                        status = state.status,
                        storeCount = state.allStores.size,
                        showLiveSuccess = showLiveSuccessBanner,
                    )
                }

                if (!state.usingActualLocation) {
                    item(key = "location-off") {
                        BannerRow(
                            icon = Icons.Filled.LocationOff,
                            text = "Location is off — distances are measured from the middle of " +
                                "Kwik Trip country. Allow location access for real distances.",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (state.filteredStores.isEmpty()) {
                    item(key = "empty") {
                        EmptyStores(
                            hasSearch = state.searchText.trim().isNotEmpty(),
                            filtersActive = state.filtersActive,
                        )
                    }
                } else {
                    item(key = "header") {
                        Text(
                            text = listHeader(
                                count = state.filteredStores.size,
                                filtersActive = state.filtersActive,
                                hasSearch = state.searchText.trim().isNotEmpty(),
                                sortOrder = state.sortOrder,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(
                        items = state.filteredStores,
                        key = { it.id },
                    ) { store ->
                        StoreRow(
                            store = store,
                            distanceMeters = store.distanceMeters(
                                state.referenceLat,
                                state.referenceLon,
                            ),
                            isFavorite = state.favoriteIds.contains(store.id),
                            onToggleFavorite = { onToggleFavorite(store.id) },
                            onClick = { onStoreClick(store.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenu(
    sortOrder: StoreSortOrder,
    onSortChange: (StoreSortOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) {
        Text(sortOrder.label)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        StoreSortOrder.entries.forEach { order ->
            DropdownMenuItem(
                text = { Text(order.label) },
                onClick = {
                    onSortChange(order)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun StatusBanner(
    status: LiveDataStatus,
    storeCount: Int,
    showLiveSuccess: Boolean,
) {
    when (status) {
        LiveDataStatus.SnapshotOnly -> {
            BannerRow(
                icon = Icons.Filled.Storage,
                text = if (storeCount == 0) {
                    "No store data yet. Connect once to download the catalog from the KwikFinder server."
                } else {
                    "Using cached store data. Pull to refresh when online."
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LiveDataStatus.Refreshing -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = "Updating store catalog…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        is LiveDataStatus.Live -> {
            if (showLiveSuccess) {
                BannerRow(
                    icon = Icons.Filled.CheckCircle,
                    text = "Catalog updated ${Format.asOf(status.atEpochMs)}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        is LiveDataStatus.Offline -> {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                BannerRow(
                    icon = Icons.Filled.WifiOff,
                    text = status.message,
                    tint = Color(0xFFE65100),
                    contentPadding = false,
                )
                status.lastSuccessEpochMs?.let { last ->
                    Text(
                        text = "Last catalog update ${Format.asOf(last)}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 34.dp, top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BannerRow(
    icon: ImageVector,
    text: String,
    tint: Color,
    contentPadding: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (contentPadding) {
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
        )
    }
}

@Composable
private fun EmptyStores(
    hasSearch: Boolean,
    filtersActive: Boolean,
) {
    val (title, description, icon) = when {
        hasSearch -> Triple(
            "No stores found",
            if (filtersActive) {
                "Nothing matches this search and your filters. Clear the search or remove a filter."
            } else {
                "Try a different name, city, or store number."
            },
            Icons.Filled.SearchOff,
        )
        filtersActive -> Triple(
            "No matching stores",
            "Try removing a filter — no store has every selected feature.",
            Icons.Filled.FilterList,
        )
        else -> Triple(
            "No stores",
            "Store catalog hasn’t loaded yet. Pull to refresh when the KwikFinder server is reachable.",
            Icons.Outlined.Place,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoreRow(
    store: Store,
    distanceMeters: Float,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(36.dp)
                    .semantics {
                        contentDescription = if (isFavorite) {
                            "Remove from favorites"
                        } else {
                            "Add to favorites"
                        }
                    },
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = null,
                    tint = if (isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = store.brandedName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = store.shortAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val badges = store.badgeFeatures.take(7)
                if (badges.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        badges.forEach { feature ->
                            FeatureBadge(
                                feature = feature,
                                evStatus = store.evCharging,
                            )
                        }
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(
                    text = Format.distance(distanceMeters),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (store.open24Hours) {
                    Text(
                        text = "24 hrs",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureBadge(
    feature: StoreFeature,
    evStatus: EvChargingStatus?,
    modifier: Modifier = Modifier,
) {
    val tint = when {
        feature == StoreFeature.EvCharging && evStatus == EvChargingStatus.Open -> Color(0xFF2E7D32)
        feature == StoreFeature.EvCharging -> Color(0xFFE65100)
        feature == StoreFeature.BitcoinATM -> Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.primary
    }
    val label = if (feature == StoreFeature.EvCharging) {
        evStatus?.label() ?: feature.label
    } else {
        feature.label
    }
    Surface(
        color = tint.copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.semantics { contentDescription = label },
    ) {
        Icon(
            imageVector = feature.icon(),
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .padding(4.dp)
                .size(14.dp),
        )
    }
}

private fun listHeader(
    count: Int,
    filtersActive: Boolean,
    hasSearch: Boolean,
    sortOrder: StoreSortOrder,
): String {
    val parts = mutableListOf("$count")
    if (filtersActive || hasSearch) parts.add("matching")
    parts.add("stores")
    parts.add(
        when (sortOrder) {
            StoreSortOrder.Nearest -> "nearest first"
            StoreSortOrder.FavoritesFirst -> "favorites first"
        },
    )
    return parts.joinToString(" ")
}
