package com.ajthom90.kwikfinder.data

import android.location.Location
import java.util.Locale
import kotlin.math.roundToInt

/** How the store list is ordered (mirrors iOS `StoreSortOrder`). */
enum class StoreSortOrder {
    Nearest,
    FavoritesFirst,
    ;

    val label: String
        get() = when (this) {
            Nearest -> "Nearest"
            FavoritesFirst -> "Favorites first"
        }
}

private val fuelDisplayNames: Map<String, String> = mapOf(
    "UNLEADED 87 (10% ETH)" to "Unleaded 87",
    "UNLEADED PREMIUM" to "Premium Unleaded",
    "UNLEADED 87 (0% ETH)" to "Unleaded 87 (No Ethanol)",
    "UNLEADED 88" to "Unleaded 88",
    "UNLEADED PLUS" to "Unleaded Plus",
    "DIESEL #1" to "Diesel #1",
    "DIESEL #2" to "Diesel #2",
    "PREMIUM DIESEL" to "Premium Diesel",
    "DIESEL #2 OFF-ROAD" to "Off-Road Diesel",
    "DIESEL EXHAUST FLUID" to "DEF",
    "E-85" to "E-85",
    "COMPRESSED NATURAL GAS" to "CNG",
    "RACING FUEL" to "Racing Fuel",
)

val FuelOffering.displayName: String
    get() = fuelDisplayNames[type]
        ?: type.lowercase(Locale.US).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
        }

val FuelOffering.formattedPrice: String?
    get() = price?.let { String.format(Locale.US, "$%.3f", it) }

/** "05:00:00" -> "5:00 AM"; midnight close reads as "Midnight". */
private fun friendlyTime(time: String): String? {
    val parts = time.split(":").mapNotNull { it.toIntOrNull() }
    if (parts.size < 2) return null
    val hour = parts[0]
    val minute = parts[1]
    if (hour == 0 && minute == 0) return "Midnight"
    val suffix = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour % 12 == 0 -> 12
        else -> hour % 12
    }
    return String.format(Locale.US, "%d:%02d %s", displayHour, minute, suffix)
}

val StoreHours.display: String
    get() {
        val open = friendlyTime(openTime) ?: return "Hours unavailable"
        val close = friendlyTime(closeTime) ?: return "Hours unavailable"
        return "$open – $close"
    }

/**
 * "KWIK TRIP #103" -> "Kwik Trip #103", "KWIK STAR #1918" -> "Kwik Star #1918"
 */
val Store.brandedName: String
    get() = name.split(" ")
        .joinToString(" ") { token ->
            if (token.all { it.isDigit() } || token.startsWith("#")) {
                token
            } else {
                token.lowercase(Locale.US).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
                }
            }
        }

val Store.shortAddress: String
    get() {
        val street = address1?.localizedCapitalizeWords().orEmpty()
        val cityPart = city?.localizedCapitalizeWords().orEmpty()
        val statePart = state.orEmpty()
        return listOf(street, cityPart, statePart)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { "Address unavailable" }
    }

val Store.fullAddress: String
    get() {
        val street = address1?.localizedCapitalizeWords().orEmpty()
        val cityPart = city?.localizedCapitalizeWords().orEmpty()
        val stateZip = listOfNotNull(state, zip).filter { it.isNotBlank() }.joinToString(" ")
        return listOf(street, cityPart, stateZip)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { "Address unavailable" }
    }

/** Features worth surfacing as badges on list rows, in display order. */
val Store.badgeFeatures: List<StoreFeature>
    get() = StoreFeature.badgeOrder.filter { has(it) }

/** Straight-line distance in meters (WGS84). */
fun Store.distanceMeters(fromLat: Double, fromLon: Double): Float {
    val results = FloatArray(1)
    Location.distanceBetween(fromLat, fromLon, latitude, longitude, results)
    return results[0]
}

fun EvChargingStatus.label(): String = when (this) {
    EvChargingStatus.Open -> "EV Charging"
    EvChargingStatus.ComingSoon -> "EV Coming Soon"
}

private fun String.localizedCapitalizeWords(): String =
    lowercase(Locale.US)
        .split(" ")
        .joinToString(" ") { word ->
            word.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
            }
        }

/**
 * Filter + search + sort for the main list (mirrors iOS `ContentView.filteredStores`).
 */
fun List<Store>.filterSearchAndSort(
    selectedFeatures: Set<StoreFeature>,
    searchQuery: String,
    sortOrder: StoreSortOrder,
    favoriteIds: Set<Int>,
    referenceLat: Double,
    referenceLon: Double,
): List<Store> {
    val query = searchQuery.trim()
    val matches = asSequence()
        .filter { it.matchesAllFeatures(selectedFeatures) }
        .filter { query.isEmpty() || it.matchesSearch(query) }
        .toList()

    return when (sortOrder) {
        StoreSortOrder.Nearest -> matches.sortedBy {
            it.distanceMeters(referenceLat, referenceLon)
        }
        StoreSortOrder.FavoritesFirst -> matches.sortedWith { lhs, rhs ->
            val leftFav = favoriteIds.contains(lhs.id)
            val rightFav = favoriteIds.contains(rhs.id)
            when {
                leftFav != rightFav -> if (leftFav) -1 else 1
                else -> lhs.distanceMeters(referenceLat, referenceLon)
                    .compareTo(rhs.distanceMeters(referenceLat, referenceLon))
            }
        }
    }
}

/** Map markers prefer geographic nearest regardless of list sort. */
fun List<Store>.mapMarkersNearest(
    centerLat: Double,
    centerLon: Double,
    limit: Int = 120,
): List<Store> {
    if (size <= limit) return this
    return sortedBy { it.distanceMeters(centerLat, centerLon) }.take(limit)
}

/** Digits-only phone for `tel:` intents. */
fun phoneDigits(phone: String?): String? {
    if (phone.isNullOrBlank()) return null
    val digits = phone.filter { it.isDigit() }
    return digits.ifEmpty { null }
}
