package com.ajthom90.kwikfinder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ajthom90.kwikfinder.data.StoreFeature

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    selected: Set<StoreFeature>,
    matchCount: Int,
    onToggle: (StoreFeature) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtersActive = selected.isNotEmpty()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Filters") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (filtersActive) {
                        TextButton(onClick = onClear) {
                            Text("Clear All")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    if (filtersActive) {
                        "Show $matchCount Stores"
                    } else {
                        "Show All $matchCount Stores"
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
        ) {
            FeatureSection("Popular", StoreFeature.headline, selected, onToggle)
            FeatureSection("Fuel Types", StoreFeature.fuel, selected, onToggle)
            FeatureSection("Professional Driver Services", StoreFeature.truck, selected, onToggle)
            FeatureSection("Amenities", StoreFeature.amenity, selected, onToggle)

            Text(
                text = "Stores must have every selected feature. Family restroom and EV charging " +
                    "data come from Kwik Trip's published location lists; EV results include " +
                    "sites marked “Coming Soon.” Bitcoin ATM is a separate amenity from cash ATMs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun FeatureSection(
    title: String,
    features: List<StoreFeature>,
    selected: Set<StoreFeature>,
    onToggle: (StoreFeature) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
    features.forEach { feature ->
        val isOn = selected.contains(feature)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(feature) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = feature.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(feature.label, style = MaterialTheme.typography.bodyLarge)
            }
            if (isOn) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    HorizontalDivider(Modifier.padding(top = 4.dp))
    Spacer(Modifier.height(4.dp))
}
