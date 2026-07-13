package com.ajthom90.kwikfinder.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coarse/fine location for map framing and nearest-store sort.
 * Uses [LocationManager] (no Play Services required).
 * Mirrors iOS `LocationService` fallback / effective location.
 */
class LocationRepository(
    private val context: Context,
) {
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val _hasPermission = MutableStateFlow(hasLocationPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _location.value = location
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit
    }

    private var updatesStarted = false

    /** Center of the Kwik Trip footprint until a real fix arrives. */
    val effectiveLatitude: Double
        get() = _location.value?.latitude ?: FALLBACK_LATITUDE

    val effectiveLongitude: Double
        get() = _location.value?.longitude ?: FALLBACK_LONGITUDE

    val usingActualLocation: Boolean
        get() = _location.value != null

    fun refreshPermissionState() {
        _hasPermission.value = hasLocationPermission()
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Start receiving updates if permission is granted.
     * Safe to call repeatedly (e.g. after the runtime permission dialog).
     */
    @SuppressLint("MissingPermission")
    fun startUpdates() {
        refreshPermissionState()
        if (!_hasPermission.value) return
        if (updatesStarted) {
            // Still try last-known in case we never got a callback.
            seedLastKnown()
            return
        }
        seedLastKnown()
        val provider = bestProvider() ?: return
        try {
            locationManager.requestLocationUpdates(
                provider,
                MIN_TIME_MS,
                MIN_DISTANCE_M,
                listener,
                Looper.getMainLooper(),
            )
            updatesStarted = true
        } catch (_: SecurityException) {
            updatesStarted = false
        } catch (_: IllegalArgumentException) {
            // Provider unavailable on this device/emulator.
            updatesStarted = false
        }
    }

    fun stopUpdates() {
        if (!updatesStarted) return
        try {
            locationManager.removeUpdates(listener)
        } catch (_: Exception) {
            // ignore
        }
        updatesStarted = false
    }

    @SuppressLint("MissingPermission")
    private fun seedLastKnown() {
        if (!_hasPermission.value) return
        val providers = buildList {
            bestProvider()?.let { add(it) }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }.distinct()
        var best: Location? = null
        for (provider in providers) {
            if (!locationManager.isProviderEnabled(provider) &&
                provider != LocationManager.PASSIVE_PROVIDER
            ) {
                continue
            }
            val last = try {
                locationManager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            } catch (_: Exception) {
                null
            } ?: continue
            if (best == null || last.time > best.time) {
                best = last
            }
        }
        if (best != null) {
            _location.value = best
        }
    }

    private fun bestProvider(): String? {
        return listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).firstOrNull { provider ->
            try {
                locationManager.isProviderEnabled(provider)
            } catch (_: Exception) {
                false
            }
        }
    }

    companion object {
        /** Western Wisconsin — same fallback as iOS `LocationService`. */
        const val FALLBACK_LATITUDE: Double = 44.25
        const val FALLBACK_LONGITUDE: Double = -91.5

        private const val MIN_TIME_MS: Long = 5_000L
        private const val MIN_DISTANCE_M: Float = 50f
    }
}
