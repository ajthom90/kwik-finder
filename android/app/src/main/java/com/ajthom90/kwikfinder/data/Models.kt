package com.ajthom90.kwikfinder.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * High-level catalog data status for UI banners (mirrors iOS `LiveDataStatus`).
 */
sealed class LiveDataStatus {
    /** Disk cache (or empty cold start); no successful network refresh this session yet. */
    data object SnapshotOnly : LiveDataStatus()

    /** A catalog refresh is in flight. */
    data object Refreshing : LiveDataStatus()

    /** Catalog was fetched successfully at [atEpochMs]. */
    data class Live(val atEpochMs: Long) : LiveDataStatus()

    /** Last attempt failed; [lastSuccessEpochMs] is the last success if any. */
    data class Offline(
        val lastSuccessEpochMs: Long?,
        val message: String,
    ) : LiveDataStatus()
}

@Serializable
data class CatalogMeta(
    val dataVersion: String? = null,
    val storeCount: Int? = null,
    val listRefreshedAt: String? = null,
    val detailsRefreshedAt: String? = null,
    val pdfEnrichedAt: String? = null,
)

@Serializable
data class CatalogResponse(
    val meta: CatalogMeta,
    val stores: List<Store> = emptyList(),
)

@Serializable
data class StoreHours(
    val dayOfWeek: String,
    val openTime: String,
    val closeTime: String,
)

@Serializable
data class FuelOffering(
    val type: String,
    val description: String? = null,
    val price: Double? = null,
)

/**
 * Server-owned filter flags from the KwikFinder catalog API (`features` object).
 * Mirrors `StoreFeatures` in `docs/api/openapi.yaml`. Clients filter on these
 * instead of re-mapping Kwik Trip amenity/fuel strings.
 *
 * All flags are optional so partial payloads still decode cleanly.
 */
@Serializable
data class StoreFeatures(
    val diesel: Boolean? = null,
    val premiumDiesel: Boolean? = null,
    val def: Boolean? = null,
    val e85: Boolean? = null,
    val cng: Boolean? = null,
    val noEthanolGas: Boolean? = null,
    val unleaded88: Boolean? = null,
    val scale: Boolean? = null,
    val showers: Boolean? = null,
    val truckParking: Boolean? = null,
    val truckParkingStalls: Int? = null,
    val transFlo: Boolean? = null,
    val fleetCards: Boolean? = null,
    val truckFriendly: Boolean? = null,
    val carWash: Boolean? = null,
    val atm: Boolean? = null,
    val bitcoinATM: Boolean? = null,
    val wifi: Boolean? = null,
    val restaurant: Boolean? = null,
) {
    /** Resolves a filterable feature against these flags; null if not represented here. */
    fun flag(feature: StoreFeature): Boolean? = when (feature) {
        StoreFeature.FamilyRestroom,
        StoreFeature.EvCharging,
        StoreFeature.Open24Hours,
        -> null
        StoreFeature.Diesel -> diesel
        StoreFeature.PremiumDiesel -> premiumDiesel
        StoreFeature.Def -> def
        StoreFeature.E85 -> e85
        StoreFeature.Cng -> cng
        StoreFeature.NoEthanolGas -> noEthanolGas
        StoreFeature.Unleaded88 -> unleaded88
        StoreFeature.Scale -> scale
        StoreFeature.Showers -> showers
        StoreFeature.TruckParking -> truckParking
        StoreFeature.TransFlo -> transFlo
        StoreFeature.FleetCards -> fleetCards
        StoreFeature.TruckFriendly -> truckFriendly
        StoreFeature.CarWash -> carWash
        StoreFeature.Atm -> atm
        StoreFeature.BitcoinATM -> bitcoinATM
        StoreFeature.Wifi -> wifi
        StoreFeature.Restaurant -> restaurant
    }
}

@Serializable
enum class EvChargingStatus {
    @SerialName("open")
    Open,

    @SerialName("comingSoon")
    ComingSoon,
}

/**
 * Everything a store can be filtered by. Prefer server `features` flags;
 * fall back to fuels/amenities for legacy rows.
 */
enum class StoreFeature {
    // Headline features (top-level Store fields)
    FamilyRestroom,
    EvCharging,
    Open24Hours,

    // Fuel types
    Diesel,
    PremiumDiesel,
    Def,
    E85,
    Cng,
    NoEthanolGas,
    Unleaded88,

    // Truck / professional driver
    Scale,
    Showers,
    TruckParking,
    TransFlo,
    FleetCards,
    TruckFriendly,

    // Other amenities
    CarWash,
    Atm,
    BitcoinATM,
    Wifi,
    Restaurant,
    ;

    val label: String
        get() = when (this) {
            FamilyRestroom -> "Family Restroom"
            EvCharging -> "EV Charging"
            Open24Hours -> "Open 24 Hours"
            Diesel -> "Diesel"
            PremiumDiesel -> "Premium Diesel"
            Def -> "DEF at the Pump"
            E85 -> "E-85"
            Cng -> "CNG"
            NoEthanolGas -> "No-Ethanol Gas"
            Unleaded88 -> "Unleaded 88"
            Scale -> "CAT Scale"
            Showers -> "Showers"
            TruckParking -> "Truck Parking"
            TransFlo -> "TransFlo"
            FleetCards -> "Fleet Cards"
            TruckFriendly -> "Truck Friendly"
            CarWash -> "Car Wash"
            Atm -> "ATM"
            BitcoinATM -> "Bitcoin ATM"
            Wifi -> "Wi-Fi"
            Restaurant -> "Restaurant"
        }

    companion object {
        val headline: List<StoreFeature> = listOf(FamilyRestroom, EvCharging, Open24Hours)
        val fuel: List<StoreFeature> = listOf(
            Diesel, PremiumDiesel, Def, E85, Cng, NoEthanolGas, Unleaded88,
        )
        val truck: List<StoreFeature> = listOf(
            Scale, Showers, TruckParking, TransFlo, FleetCards, TruckFriendly,
        )
        val amenity: List<StoreFeature> = listOf(CarWash, Atm, BitcoinATM, Wifi, Restaurant)
        val badgeOrder: List<StoreFeature> = listOf(
            FamilyRestroom, EvCharging, Diesel, Def, E85, Cng,
            Scale, Showers, TruckParking, CarWash, BitcoinATM, Open24Hours,
        )
    }
}

/**
 * One Kwik Trip / Kwik Star store from `GET /v1/stores`.
 * Nullable OpenAPI string/coord fields default so sparse rows still decode.
 */
@Serializable
data class Store(
    val id: Int,
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address1: String? = null,
    val city: String? = null,
    val county: String? = null,
    val state: String? = null,
    val zip: String? = null,
    val phone: String? = null,
    val open24Hours: Boolean = false,
    val hours: List<StoreHours> = emptyList(),
    val fuels: List<FuelOffering> = emptyList(),
    val amenities: List<String> = emptyList(),
    val truckParkingSpaces: Int = 0,
    val familyRestroom: Boolean = false,
    val evCharging: EvChargingStatus? = null,
    val features: StoreFeatures? = null,
) {
    /**
     * Prefer server `features` flags when present; otherwise map fuels/amenities.
     * Headlines always use top-level store fields.
     */
    fun has(feature: StoreFeature): Boolean {
        when (feature) {
            StoreFeature.FamilyRestroom -> return familyRestroom
            StoreFeature.EvCharging -> return evCharging != null
            StoreFeature.Open24Hours -> return open24Hours
            else -> Unit
        }
        features?.flag(feature)?.let { return it }
        return legacyHas(feature)
    }

    private fun legacyHas(feature: StoreFeature): Boolean = when (feature) {
        StoreFeature.FamilyRestroom -> familyRestroom
        StoreFeature.EvCharging -> evCharging != null
        StoreFeature.Open24Hours -> open24Hours
        StoreFeature.Diesel ->
            fuels.any { it.type.startsWith("DIESEL #") || it.type == "PREMIUM DIESEL" }
        StoreFeature.PremiumDiesel -> fuels.any { it.type == "PREMIUM DIESEL" }
        StoreFeature.Def -> fuels.any { it.type == "DIESEL EXHAUST FLUID" }
        StoreFeature.E85 -> fuels.any { it.type == "E-85" }
        StoreFeature.Cng -> fuels.any { it.type == "COMPRESSED NATURAL GAS" }
        StoreFeature.NoEthanolGas -> fuels.any { it.type == "UNLEADED 87 (0% ETH)" }
        StoreFeature.Unleaded88 -> fuels.any { it.type == "UNLEADED 88" }
        StoreFeature.Scale -> amenities.contains("SCALE")
        StoreFeature.Showers -> amenities.contains("SHOWERS")
        StoreFeature.TruckParking -> amenities.contains("TRUCK-PARKING")
        StoreFeature.TransFlo -> amenities.contains("TRANS-FLO")
        StoreFeature.FleetCards -> amenities.contains("TRENDAR")
        StoreFeature.TruckFriendly -> amenities.contains("TRUCK-FRIENDLY")
        StoreFeature.CarWash -> amenities.contains("CAR-WASH")
        StoreFeature.Atm -> amenities.contains("ATM")
        StoreFeature.BitcoinATM -> amenities.contains("BITCOIN ATM")
        StoreFeature.Wifi -> amenities.contains("WI-FI")
        StoreFeature.Restaurant -> amenities.contains("RESTAURANT")
    }

    /** Loose text match for list search (name, city, address, store #). */
    fun matchesSearch(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        q.toIntOrNull()?.let { if (it == id) return true }
        val haystack = listOfNotNull(
            name, city, address1, state, zip, "#$id", "$id",
        ).joinToString(" ")
        return haystack.contains(q, ignoreCase = true)
    }
}

/**
 * Feature filter with AND semantics: a store must have every selected feature.
 * Mirrors iOS `FilterState.matches`.
 */
fun Store.matchesAllFeatures(selected: Set<StoreFeature>): Boolean =
    selected.all { has(it) }

fun List<Store>.filterByFeatures(selected: Set<StoreFeature>): List<Store> =
    if (selected.isEmpty()) this else filter { it.matchesAllFeatures(selected) }
