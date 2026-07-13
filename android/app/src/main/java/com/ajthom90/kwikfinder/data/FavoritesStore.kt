package com.ajthom90.kwikfinder.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.favoritesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "kwikfinder_favorites",
)

/**
 * Persisted set of favorited store IDs (DataStore Preferences).
 * Mirrors iOS `FavoritesStore` (Set of Int store IDs).
 */
class FavoritesStore(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.applicationContext.favoritesDataStore)

    val ids: Flow<Set<Int>> = dataStore.data.map { prefs ->
        prefs[KEY_FAVORITE_IDS]
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    suspend fun contains(id: Int): Boolean =
        ids.first().contains(id)

    suspend fun toggle(id: Int) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITE_IDS]?.toMutableSet() ?: mutableSetOf()
            val key = id.toString()
            if (!current.add(key)) {
                current.remove(key)
            }
            prefs[KEY_FAVORITE_IDS] = current
        }
    }

    suspend fun add(id: Int) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITE_IDS]?.toMutableSet() ?: mutableSetOf()
            current.add(id.toString())
            prefs[KEY_FAVORITE_IDS] = current
        }
    }

    suspend fun remove(id: Int) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITE_IDS]?.toMutableSet() ?: mutableSetOf()
            current.remove(id.toString())
            prefs[KEY_FAVORITE_IDS] = current
        }
    }

    companion object {
        val KEY_FAVORITE_IDS = stringSetPreferencesKey("favoriteStoreIDs")
    }
}
