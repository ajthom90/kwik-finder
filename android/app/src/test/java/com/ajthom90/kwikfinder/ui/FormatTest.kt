package com.ajthom90.kwikfinder.ui

import com.ajthom90.kwikfinder.data.Store
import com.ajthom90.kwikfinder.data.StoreFeature
import com.ajthom90.kwikfinder.data.StoreSortOrder
import com.ajthom90.kwikfinder.data.brandedName
import com.ajthom90.kwikfinder.data.filterSearchAndSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {
    @Test
    fun distance_feetUnderPointTwoMiles() {
        // ~100 m ≈ 328 ft
        val text = Format.distance(100f)
        assertTrue(text.endsWith(" ft"))
        assertTrue(text.removeSuffix(" ft").toInt() in 320..340)
    }

    @Test
    fun distance_oneDecimalUnderTenMiles() {
        // 1 mile = 1609.344 m
        assertEquals("1.0 mi", Format.distance(1609.344f))
        assertEquals("5.0 mi", Format.distance(8046.72f))
    }

    @Test
    fun distance_wholeMilesAtTenPlus() {
        assertEquals("10 mi", Format.distance(16093.44f))
        assertEquals("25 mi", Format.distance(40233.6f))
    }

    @Test
    fun asOf_relativeStrings() {
        val now = 1_700_000_000_000L
        assertEquals("just now", Format.asOf(now - 10_000L, now))
        assertEquals("5 minutes ago", Format.asOf(now - 5 * 60_000L, now))
        assertEquals("2 hours ago", Format.asOf(now - 2 * 3_600_000L, now))
        assertEquals("3 days ago", Format.asOf(now - 3 * 86_400_000L, now))
    }

    @Test
    fun brandedName_titleCasesBrand() {
        val store = Store(id = 103, name = "KWIK TRIP #103")
        assertEquals("Kwik Trip #103", store.brandedName)
    }

    @Test
    fun filterSearchAndSort_favoritesFirst() {
        val a = Store(id = 1, name = "A", latitude = 44.0, longitude = -91.0)
        val b = Store(id = 2, name = "B", latitude = 44.1, longitude = -91.0)
        val sorted = listOf(a, b).filterSearchAndSort(
            selectedFeatures = emptySet(),
            searchQuery = "",
            sortOrder = StoreSortOrder.FavoritesFirst,
            favoriteIds = setOf(2),
            referenceLat = 44.0,
            referenceLon = -91.0,
        )
        assertEquals(listOf(2, 1), sorted.map { it.id })
    }

    @Test
    fun filterSearchAndSort_andFeatures() {
        val withDiesel = Store(
            id = 1,
            name = "Diesel Store",
            features = com.ajthom90.kwikfinder.data.StoreFeatures(diesel = true, wifi = true),
        )
        val wifiOnly = Store(
            id = 2,
            name = "Wifi Store",
            features = com.ajthom90.kwikfinder.data.StoreFeatures(diesel = false, wifi = true),
        )
        val result = listOf(withDiesel, wifiOnly).filterSearchAndSort(
            selectedFeatures = setOf(StoreFeature.Diesel, StoreFeature.Wifi),
            searchQuery = "",
            sortOrder = StoreSortOrder.Nearest,
            favoriteIds = emptySet(),
            referenceLat = 0.0,
            referenceLon = 0.0,
        )
        assertEquals(listOf(1), result.map { it.id })
    }

    @Test
    fun filterSearchAndSort_searchById() {
        val stores = listOf(
            Store(id = 42, name = "KWIK TRIP #42", city = "La Crosse"),
            Store(id = 99, name = "KWIK STAR #99", city = "Eau Claire"),
        )
        val result = stores.filterSearchAndSort(
            selectedFeatures = emptySet(),
            searchQuery = "42",
            sortOrder = StoreSortOrder.Nearest,
            favoriteIds = emptySet(),
            referenceLat = 0.0,
            referenceLon = 0.0,
        )
        assertEquals(listOf(42), result.map { it.id })
    }
}
