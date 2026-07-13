package com.ajthom90.kwikfinder.data

import java.io.File

/**
 * Disk cache for the full catalog at `[filesDir]/catalog.json`.
 * Load failures are treated as a cold start (empty cache).
 * Write failures are non-fatal for the caller (in-memory catalog still works).
 */
class CatalogCache(
    filesDir: File,
    private val json: kotlinx.serialization.json.Json = APIConfig.json,
) {
    private val cacheFile: File = File(filesDir, CACHE_FILE_NAME)

    fun load(): CatalogResponse? {
        if (!cacheFile.exists()) return null
        return try {
            val text = cacheFile.readText(Charsets.UTF_8)
            json.decodeFromString(CatalogResponse.serializer(), text)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Atomically write [catalog] to disk.
     * @return true if the write succeeded
     */
    fun save(catalog: CatalogResponse): Boolean {
        return try {
            val text = json.encodeToString(CatalogResponse.serializer(), catalog)
            val tmp = File(cacheFile.parentFile, "$CACHE_FILE_NAME.tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(cacheFile)) {
                // Fallback when rename fails across filesystems / existing target.
                tmp.copyTo(cacheFile, overwrite = true)
                tmp.delete()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun exists(): Boolean = cacheFile.exists()

    fun lastModifiedEpochMs(): Long? =
        if (cacheFile.exists()) cacheFile.lastModified().takeIf { it > 0L } else null

    companion object {
        const val CACHE_FILE_NAME: String = "catalog.json"
    }
}
