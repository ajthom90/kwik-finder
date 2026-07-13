package com.ajthom90.kwikfinder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Feature filter uses AND semantics across selected features
 * (mirrors iOS `FilterState.matches` / `selected.allSatisfy { store.has($0) }`).
 */
class FeatureFilterTest {

    private lateinit var stores: List<Store>
    private lateinit var dieselTruckStop: Store
    private lateinit var carWashOnly: Store

    @Before
    fun setUp() {
        val text = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("catalog_sample.json"),
        ).bufferedReader().readText()
        stores = APIConfig.json.decodeFromString(CatalogResponse.serializer(), text).stores
        dieselTruckStop = stores.first { it.id == 123 }
        carWashOnly = stores.first { it.id == 456 }
    }

    @Test
    fun has_usesServerFeatureFlags() {
        assertTrue(dieselTruckStop.has(StoreFeature.Diesel))
        assertTrue(dieselTruckStop.has(StoreFeature.PremiumDiesel))
        assertTrue(dieselTruckStop.has(StoreFeature.Atm))
        assertTrue(dieselTruckStop.has(StoreFeature.Wifi))
        assertTrue(dieselTruckStop.has(StoreFeature.TruckParking))
        assertFalse(dieselTruckStop.has(StoreFeature.Showers))
        assertFalse(dieselTruckStop.has(StoreFeature.Scale))
        assertTrue(dieselTruckStop.has(StoreFeature.FamilyRestroom))
        assertTrue(dieselTruckStop.has(StoreFeature.EvCharging))
        assertTrue(dieselTruckStop.has(StoreFeature.Open24Hours))

        assertFalse(carWashOnly.has(StoreFeature.Diesel))
        assertTrue(carWashOnly.has(StoreFeature.CarWash))
        assertTrue(carWashOnly.has(StoreFeature.Atm))
        assertFalse(carWashOnly.has(StoreFeature.Wifi))
        assertFalse(carWashOnly.has(StoreFeature.Open24Hours))
        assertFalse(carWashOnly.has(StoreFeature.EvCharging))
    }

    @Test
    fun matchesAllFeatures_emptySelection_matchesEverything() {
        assertTrue(dieselTruckStop.matchesAllFeatures(emptySet()))
        assertTrue(carWashOnly.matchesAllFeatures(emptySet()))
        assertEquals(2, stores.filterByFeatures(emptySet()).size)
    }

    @Test
    fun matchesAllFeatures_singleFeature() {
        val dieselOnly = setOf(StoreFeature.Diesel)
        assertTrue(dieselTruckStop.matchesAllFeatures(dieselOnly))
        assertFalse(carWashOnly.matchesAllFeatures(dieselOnly))
        assertEquals(listOf(123), stores.filterByFeatures(dieselOnly).map { it.id })
    }

    @Test
    fun matchesAllFeatures_andAcrossFeatures() {
        // Store 123 has diesel + atm; store 456 has atm but not diesel.
        val dieselAndAtm = setOf(StoreFeature.Diesel, StoreFeature.Atm)
        assertTrue(dieselTruckStop.matchesAllFeatures(dieselAndAtm))
        assertFalse(carWashOnly.matchesAllFeatures(dieselAndAtm))
        assertEquals(listOf(123), stores.filterByFeatures(dieselAndAtm).map { it.id })

        // Both have ATM.
        val atmOnly = setOf(StoreFeature.Atm)
        assertEquals(setOf(123, 456), stores.filterByFeatures(atmOnly).map { it.id }.toSet())

        // AND that no store satisfies.
        val dieselAndCarWash = setOf(StoreFeature.Diesel, StoreFeature.CarWash)
        assertFalse(dieselTruckStop.matchesAllFeatures(dieselAndCarWash))
        assertFalse(carWashOnly.matchesAllFeatures(dieselAndCarWash))
        assertTrue(stores.filterByFeatures(dieselAndCarWash).isEmpty())
    }

    @Test
    fun matchesAllFeatures_headlineFeaturesAndedWithFuel() {
        val selected = setOf(
            StoreFeature.Open24Hours,
            StoreFeature.FamilyRestroom,
            StoreFeature.Diesel,
        )
        assertTrue(dieselTruckStop.matchesAllFeatures(selected))
        assertFalse(carWashOnly.matchesAllFeatures(selected))
    }

    @Test
    fun legacyHas_whenFeaturesMissing_usesFuelsAndAmenities() {
        val legacy = Store(
            id = 999,
            name = "LEGACY #999",
            fuels = listOf(
                FuelOffering(type = "DIESEL #2", description = null, price = 3.0),
                FuelOffering(type = "E-85", description = null, price = 2.5),
            ),
            amenities = listOf("ATM", "SHOWERS", "SCALE"),
            open24Hours = true,
            features = null,
        )
        assertTrue(legacy.has(StoreFeature.Diesel))
        assertTrue(legacy.has(StoreFeature.E85))
        assertTrue(legacy.has(StoreFeature.Atm))
        assertTrue(legacy.has(StoreFeature.Showers))
        assertTrue(legacy.has(StoreFeature.Scale))
        assertFalse(legacy.has(StoreFeature.Wifi))
        assertTrue(legacy.matchesAllFeatures(setOf(StoreFeature.Diesel, StoreFeature.Showers)))
        assertFalse(legacy.matchesAllFeatures(setOf(StoreFeature.Diesel, StoreFeature.Wifi)))
    }
}
