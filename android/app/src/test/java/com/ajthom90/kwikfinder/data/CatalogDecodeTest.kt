package com.ajthom90.kwikfinder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JSON decode from OpenAPI-shaped catalog fixture (camelCase from server).
 */
class CatalogDecodeTest {

    private val json = APIConfig.json

    @Test
    fun decodeCatalogSample_matchesOpenApiShape() {
        val text = readResource("catalog_sample.json")
        val catalog = json.decodeFromString(CatalogResponse.serializer(), text)

        assertEquals("2026-07-13T12:00:00+00:00", catalog.meta.dataVersion)
        assertEquals(2, catalog.meta.storeCount)
        assertEquals("2026-07-13T12:00:00+00:00", catalog.meta.listRefreshedAt)
        assertEquals(2, catalog.stores.size)

        val store = catalog.stores.first { it.id == 123 }
        assertEquals("KWIK TRIP #123", store.name)
        assertEquals(44.9, store.latitude, 0.0001)
        assertEquals(-93.2, store.longitude, 0.0001)
        assertEquals("La Crosse", store.city)
        assertEquals("WI", store.state)
        assertTrue(store.open24Hours)
        assertTrue(store.familyRestroom)
        assertEquals(EvChargingStatus.Open, store.evCharging)
        assertEquals(12, store.truckParkingSpaces)
        assertEquals(1, store.hours.size)
        assertEquals("Monday", store.hours[0].dayOfWeek)
        assertEquals(2, store.fuels.size)
        assertEquals("DIESEL #2", store.fuels[0].type)
        assertEquals(3.299, store.fuels[0].price!!, 0.0001)
        assertNull(store.fuels[0].description)
        assertNotNull(store.features)
        assertTrue(store.features!!.diesel == true)
        assertTrue(store.features!!.premiumDiesel == true)
        assertTrue(store.features!!.atm == true)
        assertEquals(12, store.features!!.truckParkingStalls)

        val second = catalog.stores.first { it.id == 456 }
        assertNull(second.evCharging)
        assertFalse(second.open24Hours)
        assertTrue(second.features!!.carWash == true)
        assertTrue(second.features!!.diesel == false)
    }

    @Test
    fun decodeCatalogMeta_alone() {
        val text = """
            {
              "dataVersion": null,
              "storeCount": 0,
              "listRefreshedAt": null,
              "detailsRefreshedAt": null,
              "pdfEnrichedAt": null
            }
        """.trimIndent()
        val meta = json.decodeFromString(CatalogMeta.serializer(), text)
        assertNull(meta.dataVersion)
        assertEquals(0, meta.storeCount)
    }

    @Test
    fun decodeEvCharging_comingSoon() {
        val text = """
            {
              "id": 1,
              "name": "Test",
              "latitude": 0.0,
              "longitude": 0.0,
              "open24Hours": false,
              "hours": [],
              "fuels": [],
              "amenities": [],
              "truckParkingSpaces": 0,
              "familyRestroom": false,
              "evCharging": "comingSoon",
              "features": {}
            }
        """.trimIndent()
        val store = json.decodeFromString(Store.serializer(), text)
        assertEquals(EvChargingStatus.ComingSoon, store.evCharging)
    }

    private fun readResource(name: String): String {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "Missing test resource: $name"
        }
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
