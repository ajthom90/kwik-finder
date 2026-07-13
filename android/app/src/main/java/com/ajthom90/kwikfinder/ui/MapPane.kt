package com.ajthom90.kwikfinder.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ajthom90.kwikfinder.data.EvChargingStatus
import com.ajthom90.kwikfinder.data.Store
import com.ajthom90.kwikfinder.data.brandedName
import com.ajthom90.kwikfinder.location.LocationRepository
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * osmdroid [MapView] hosted in Compose via [AndroidView].
 * Markers limited by caller (typically nearest ~120).
 */
@Composable
fun MapPane(
    stores: List<Store>,
    centerLat: Double,
    centerLon: Double,
    followUserOnce: Boolean,
    onFollowedUser: () -> Unit,
    onStoreClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var markersReady by remember { mutableStateOf(false) }

    // Defer markers briefly so basemap tiles can paint first (iOS showMarkers pattern).
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(350)
        markersReady = true
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(DEFAULT_ZOOM)
                controller.setCenter(
                    GeoPoint(
                        LocationRepository.FALLBACK_LATITUDE,
                        LocationRepository.FALLBACK_LONGITUDE,
                    ),
                )
                mapViewRef = this
            }
        },
        update = { mapView ->
            if (!markersReady) return@AndroidView

            val existing = mapView.overlays.filterIsInstance<Marker>()
            val desiredIds = stores.map { it.id }.toSet()

            // Remove stale markers
            existing.filter { it.id?.toIntOrNull() !in desiredIds }.forEach { marker ->
                mapView.overlays.remove(marker)
            }

            // Add / refresh markers
            for (store in stores) {
                val idStr = store.id.toString()
                val marker = existing.firstOrNull { it.id == idStr }
                    ?: Marker(mapView).also { m ->
                        m.id = idStr
                        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        m.setOnMarkerClickListener { _, _ ->
                            onStoreClick(store.id)
                            true
                        }
                        mapView.overlays.add(m)
                    }
                marker.position = GeoPoint(store.latitude, store.longitude)
                marker.title = store.brandedName
                when (store.evCharging) {
                    EvChargingStatus.Open -> marker.snippet = "EV open"
                    EvChargingStatus.ComingSoon -> marker.snippet = "EV coming soon"
                    null -> marker.snippet = null
                }
            }
            mapView.invalidate()
        },
    )

    // Center on user once when location becomes available.
    LaunchedEffect(followUserOnce, centerLat, centerLon) {
        val map = mapViewRef ?: return@LaunchedEffect
        if (!followUserOnce) return@LaunchedEffect
        map.controller.animateTo(GeoPoint(centerLat, centerLon))
        map.controller.setZoom(DEFAULT_ZOOM)
        onFollowedUser()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val map = mapViewRef ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef?.onDetach()
            mapViewRef = null
        }
    }
}

private const val DEFAULT_ZOOM = 9.0
