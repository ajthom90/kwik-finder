package com.ajthom90.kwikfinder.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ajthom90.kwikfinder.data.EvChargingStatus
import com.ajthom90.kwikfinder.data.LiveDataStatus
import com.ajthom90.kwikfinder.data.Store
import com.ajthom90.kwikfinder.data.StoreFeature
import com.ajthom90.kwikfinder.data.brandedName
import com.ajthom90.kwikfinder.data.display
import com.ajthom90.kwikfinder.data.displayName
import com.ajthom90.kwikfinder.data.distanceMeters
import com.ajthom90.kwikfinder.data.formattedPrice
import com.ajthom90.kwikfinder.data.fullAddress
import com.ajthom90.kwikfinder.data.phoneDigits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreDetail(
    store: Store?,
    isFavorite: Boolean,
    usingActualLocation: Boolean,
    referenceLat: Double,
    referenceLon: Double,
    pricesAsOfEpochMs: Long?,
    isLive: Boolean,
    liveStatus: LiveDataStatus,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = store?.brandedName ?: "Store",
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (store != null) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (isFavorite) {
                                    "Remove from favorites"
                                } else {
                                    "Add to favorites"
                                },
                                tint = if (isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (store == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Store not found", style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Header
            Text(store.fullAddress, style = MaterialTheme.typography.bodyMedium)
            if (usingActualLocation) {
                Text(
                    text = "${Format.distance(store.distanceMeters(referenceLat, referenceLon))} away",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            HeadlineBadges(store)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        val label = Uri.encode(store.brandedName)
                        val uri = Uri.parse(
                            "geo:${store.latitude},${store.longitude}?q=${store.latitude},${store.longitude}($label)",
                        )
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(Intent.createChooser(intent, "Directions"))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Directions")
                }
                val digits = phoneDigits(store.phone)
                if (digits != null) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Call")
                    }
                }
            }

            SectionTitle("Fuel")
            if (store.fuels.isEmpty()) {
                Muted("No fuel information for this store.")
            } else {
                store.fuels.forEach { fuel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(fuel.displayName, style = MaterialTheme.typography.bodyLarge)
                        fuel.formattedPrice?.let { price ->
                            Text(
                                text = price,
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            pricesAsOfEpochMs?.let { asOf ->
                val base = if (isLive) {
                    "Prices from catalog ${Format.asOf(asOf)}. The price posted at the pump always governs."
                } else {
                    "Prices from cached catalog (as of ${Format.asOf(asOf)}). Connect or pull to refresh. " +
                        "The price posted at the pump always governs."
                }
                Text(
                    text = base,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
            }
            if (liveStatus is LiveDataStatus.Offline && !isLive) {
                Text(
                    text = liveStatus.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE65100),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SectionTitle("Professional Driver Services")
            // iOS: truck features + DEF if present (DEF is in fuel group; still shown under truck when has)
            val truckServices = buildList {
                addAll(StoreFeature.truck.filter { store.has(it) })
                if (store.has(StoreFeature.Def)) add(StoreFeature.Def)
            }.distinct()
            if (truckServices.isEmpty()) {
                Muted("No truck stop services at this store.")
            } else {
                truckServices.forEach { feature ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = feature.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(feature.label, modifier = Modifier.weight(1f))
                        if (feature == StoreFeature.TruckParking && store.truckParkingSpaces > 0) {
                            Text(
                                "${store.truckParkingSpaces} stalls",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SectionTitle("Amenities")
            val amenities = StoreFeature.amenity.filter { store.has(it) }
            if (amenities.isEmpty()) {
                Muted("No listed amenities.")
            } else {
                amenities.forEach { feature ->
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = feature.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(feature.label)
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SectionTitle("Hours")
            when {
                store.open24Hours -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Open 24 hours")
                    }
                }
                store.hours.isNotEmpty() -> {
                    store.hours.forEach { day ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(day.dayOfWeek.ifEmpty { "Hours" })
                            Text(
                                day.display,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> Muted("Hours unavailable — call ahead.")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun HeadlineBadges(store: Store) {
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (store.familyRestroom) {
            HeadlineLabel(
                text = "Family restroom available",
                icon = StoreFeature.FamilyRestroom.icon(),
                color = Color(0xFF1565C0),
            )
        }
        store.evCharging?.let { ev ->
            HeadlineLabel(
                text = if (ev == EvChargingStatus.Open) {
                    "KwikCharge EV charging"
                } else {
                    "KwikCharge EV charging coming soon"
                },
                icon = StoreFeature.EvCharging.icon(),
                color = if (ev == EvChargingStatus.Open) Color(0xFF2E7D32) else Color(0xFFE65100),
            )
        }
        if (store.open24Hours) {
            HeadlineLabel(
                text = "Open 24 hours",
                icon = Icons.Filled.Schedule,
                color = Color(0xFF2E7D32),
            )
        }
        if (store.has(StoreFeature.BitcoinATM)) {
            HeadlineLabel(
                text = "Bitcoin ATM",
                icon = StoreFeature.BitcoinATM.icon(),
                color = Color(0xFFE65100),
            )
        }
    }
}

@Composable
private fun HeadlineLabel(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun Muted(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
