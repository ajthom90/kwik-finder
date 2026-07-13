package com.ajthom90.kwikfinder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CatalogCacheTest {

    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    @Test
    fun load_missingFile_returnsNull() {
        val cache = CatalogCache(tmp.root)
        assertNull(cache.load())
        assertTrue(!cache.exists())
    }

    @Test
    fun saveAndLoad_roundTrip() {
        val text = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("catalog_sample.json"),
        ).bufferedReader().readText()
        val original = APIConfig.json.decodeFromString(CatalogResponse.serializer(), text)

        val cache = CatalogCache(tmp.root)
        assertTrue(cache.save(original))
        assertTrue(File(tmp.root, CatalogCache.CACHE_FILE_NAME).exists())

        val loaded = cache.load()
        assertNotNull(loaded)
        assertEquals(original.meta.dataVersion, loaded!!.meta.dataVersion)
        assertEquals(original.stores.size, loaded.stores.size)
        assertEquals(original.stores.map { it.id }, loaded.stores.map { it.id })
        assertEquals(original.stores[0].features?.diesel, loaded.stores[0].features?.diesel)
    }
}
