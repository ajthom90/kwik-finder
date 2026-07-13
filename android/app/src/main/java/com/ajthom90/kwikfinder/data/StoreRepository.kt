package com.ajthom90.kwikfinder.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns catalog store data. Loads `filesDir/catalog.json` on init, then refreshes
 * from the KwikFinder server (`GET /v1/meta`, then `/v1/stores` when needed).
 * Clients never contact kwiktrip.com — only the KwikFinder catalog API.
 */
class StoreRepository(
    private val api: KwikFinderApi,
    private val cache: CatalogCache,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private val _stores = MutableStateFlow<List<Store>>(emptyList())
    val stores: StateFlow<List<Store>> = _stores.asStateFlow()

    private val _status = MutableStateFlow<LiveDataStatus>(LiveDataStatus.SnapshotOnly)
    val status: StateFlow<LiveDataStatus> = _status.asStateFlow()

    private val _catalogMeta = MutableStateFlow<CatalogMeta?>(null)
    val catalogMeta: StateFlow<CatalogMeta?> = _catalogMeta.asStateFlow()

    /** `dataVersion` from the last applied catalog (disk or network). */
    @Volatile
    var cachedDataVersion: String? = null
        private set

    @Volatile
    var lastSuccessfulRefreshEpochMs: Long? = null
        private set

    @Volatile
    var lastErrorMessage: String? = null
        private set

    @Volatile
    private var lastMetaCheckAtMs: Long? = null

    private val refreshMutex = Mutex()

    init {
        loadDiskCache()
    }

    private fun loadDiskCache() {
        val catalog = cache.load() ?: run {
            _status.value = LiveDataStatus.SnapshotOnly
            return
        }
        applyCatalog(catalog, fromNetwork = false)
        _status.value = LiveDataStatus.SnapshotOnly
    }

    private fun applyCatalog(catalog: CatalogResponse, fromNetwork: Boolean) {
        _stores.value = catalog.stores
        _catalogMeta.value = catalog.meta
        cachedDataVersion = catalog.meta.dataVersion
        if (fromNetwork) {
            if (!cache.save(catalog)) {
                lastErrorMessage = "Couldn't save catalog cache"
            }
        }
    }

    /**
     * Probe meta, then download full catalog when version differs, cache is empty, or forced.
     * @param force skip debounce and always re-download stores
     * @return true if the catalog is considered successfully refreshed (or debounce short-circuit with live data)
     */
    suspend fun refresh(force: Boolean = false): Boolean = refreshMutex.withLock {
        val now = clockMs()
        val lastMeta = lastMetaCheckAtMs
        if (
            !force &&
            lastMeta != null &&
            now - lastMeta < META_MIN_INTERVAL_MS &&
            _stores.value.isNotEmpty()
        ) {
            val lastSuccess = lastSuccessfulRefreshEpochMs
            if (lastSuccess != null) {
                _status.value = LiveDataStatus.Live(lastSuccess)
                return true
            }
            return false
        }

        lastMetaCheckAtMs = now
        _status.value = LiveDataStatus.Refreshing

        return try {
            val meta = api.fetchMeta()
            val remoteVersion = meta.dataVersion
            val needsDownload = force ||
                _stores.value.isEmpty() ||
                remoteVersion == null ||
                remoteVersion != cachedDataVersion

            if (needsDownload) {
                val catalog = api.fetchStores()
                applyCatalog(catalog, fromNetwork = true)
            } else {
                // Meta matches cache — keep stores, refresh meta timestamps in memory.
                _catalogMeta.value = meta
            }

            markLiveSuccess()
            true
        } catch (e: Exception) {
            val message = if (_stores.value.isEmpty()) {
                "Couldn't load stores. Connect once to download the catalog."
            } else {
                "Couldn't refresh the catalog. Showing saved data."
            }
            markLiveFailure(message = message, cause = e)
            false
        }
    }

    private fun markLiveSuccess() {
        val now = clockMs()
        lastSuccessfulRefreshEpochMs = now
        lastErrorMessage = null
        _status.value = LiveDataStatus.Live(now)
    }

    private fun markLiveFailure(message: String, cause: Exception? = null) {
        lastErrorMessage = cause?.message?.takeIf { it.isNotBlank() } ?: message
        _status.value = LiveDataStatus.Offline(
            lastSuccessEpochMs = lastSuccessfulRefreshEpochMs,
            message = message,
        )
    }

    /**
     * When fuel prices in the catalog were last confirmed (for detail footer).
     * [id] is unused — catalog is all-or-nothing; kept for iOS parity.
     */
    @Suppress("UNUSED_PARAMETER")
    fun pricesAsOfEpochMs(id: Int): Long? {
        lastSuccessfulRefreshEpochMs?.let { return it }
        _catalogMeta.value?.detailsRefreshedAt?.let { iso ->
            parseIso8601ToEpochMs(iso)?.let { return it }
        }
        return cache.lastModifiedEpochMs()
    }

    /** True after a successful network refresh this session (or when status is live). */
    @Suppress("UNUSED_PARAMETER")
    fun isLive(id: Int): Boolean {
        if (_status.value is LiveDataStatus.Live) return true
        return lastSuccessfulRefreshEpochMs != null
    }

    companion object {
        /** Mild debounce so launch + foreground don't stampede meta. */
        const val META_MIN_INTERVAL_MS: Long = 30_000L

        private fun parseIso8601ToEpochMs(value: String): Long? {
            return try {
                java.time.Instant.parse(value).toEpochMilli()
            } catch (_: Exception) {
                try {
                    java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
