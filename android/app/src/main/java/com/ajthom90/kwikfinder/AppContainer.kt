package com.ajthom90.kwikfinder

import android.content.Context
import com.ajthom90.kwikfinder.data.CatalogCache
import com.ajthom90.kwikfinder.data.FavoritesStore
import com.ajthom90.kwikfinder.data.KwikFinderApiFactory
import com.ajthom90.kwikfinder.data.StoreRepository
import com.ajthom90.kwikfinder.location.LocationRepository

/**
 * Process-wide dependencies for the KwikFinder Android client.
 * Constructed once from [KwikFinderApplication].
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val api = KwikFinderApiFactory.create()
    val catalogCache = CatalogCache(appContext.filesDir)
    val storeRepository = StoreRepository(api = api, cache = catalogCache)
    val favoritesStore = FavoritesStore(appContext)
    val locationRepository = LocationRepository(appContext)
}
