package pe.soltelematic.mobile.ui.map.engine.google

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import pe.soltelematic.mobile.ui.map.engine.MapCameraController
import pe.soltelematic.mobile.ui.map.engine.MapEngine
import pe.soltelematic.mobile.ui.map.engine.MapMarkerData

/**
 * Implementación sobre Google Maps SDK (maps-compose + maps-compose-utils para clustering).
 * Es la única clase del proyecto que puede importar de com.google.android.gms.maps o
 * com.google.maps.android.compose -- si algún día se migra a MapLibre, esta clase se
 * reemplaza entera y ui/map/MapScreen.kt no cambia una línea.
 *
 * El clustering se maneja imperativo (ClusterManager + AssetClusterRenderer vía MapEffect), no
 * con el Clustering(clusterItemContent = {...}) de maps-compose-utils: ese overload renderiza
 * cada marcador/cluster componiendo una vista Compose y rasterizándola a bitmap en el hilo
 * principal en cada reclusterización, que es el costo real medido con 200+ unidades (ver
 * investigación del Bloque 7). AssetClusterRenderer, en cambio, reutiliza BitmapDescriptor
 * cacheados por icono (ver MarkerIconCache) y deja que la librería reparta/reutilice los Marker
 * nativos como ya está optimizada para hacerlo.
 */
class GoogleMapEngine(private val iconCache: MarkerIconCache) : MapEngine {

    @Composable
    override fun rememberCameraController(): MapCameraController {
        val cameraPositionState = rememberCameraPositionState()
        val scope = rememberCoroutineScope()
        return remember(cameraPositionState, scope) {
            GoogleMapCameraController(cameraPositionState, scope)
        }
    }

    @Composable
    override fun Content(
        modifier: Modifier,
        cameraController: MapCameraController,
        markers: List<MapMarkerData>,
        selectedMarkerId: Int?,
        myLocationEnabled: Boolean,
        onMarkerClick: (Int) -> Unit,
        onMapClick: () -> Unit
    ) {
        // Casteo seguro: el único MapCameraController que existe hoy es el que devuelve
        // rememberCameraController() de esta misma clase.
        val googleController = cameraController as GoogleMapCameraController
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val clusteringState = remember { ClusteringState() }

        GoogleMap(
            modifier = modifier,
            cameraPositionState = googleController.cameraPositionState,
            // myLocationEnabled solo debe llegar en true cuando quien llama ya confirmó el
            // permiso ACCESS_FINE_LOCATION; si no, el SDK de Google Maps lanza SecurityException.
            properties = MapProperties(isMyLocationEnabled = myLocationEnabled),
            uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
            onMapClick = { onMapClick() }
        ) {
            // Se dispara con cada refresco de Room (nueva lista de markers), nunca con cada
            // movimiento de cámara -- eso lo cubre el listener debounced de abajo, creado una
            // sola vez la primera vez que este efecto corre.
            MapEffect(markers) { googleMap ->
                val manager = clusteringState.manager ?: run {
                    val newManager = ClusterManager<AssetClusterItem>(context, googleMap)
                    newManager.renderer = AssetClusterRenderer(context, googleMap, newManager, iconCache)
                    googleMap.setOnMarkerClickListener(newManager)
                    // Opción A del Bloque 7: coalesce varios onCameraIdle seguidos (zoom in/out
                    // en ráfaga) en una sola reclusterización.
                    googleMap.setOnCameraIdleListener(
                        DebouncedCameraIdleListener(scope) { newManager.onCameraIdle() }
                    )
                    newManager.setOnClusterItemClickListener { item ->
                        onMarkerClick(item.markerData.id)
                        true
                    }
                    clusteringState.manager = newManager
                    newManager
                }

                // Se salta las claves ya cacheadas (ver MarkerIconCache), así que en un refresco
                // con las mismas unidades esto no vuelve a pedir red.
                iconCache.preload(markers)
                manager.clearItems()
                manager.addItems(markers.map { AssetClusterItem(it) })
                manager.cluster()
            }

            // Estado "seleccionado": un Marker de anillo superpuesto en vez de una variante de
            // icono por estado -- a lo sumo una unidad seleccionada a la vez, no vale la pena
            // duplicar el caché de bitmaps por eso.
            MapEffect(selectedMarkerId, markers) { googleMap ->
                clusteringState.selectionRing?.remove()
                clusteringState.selectionRing = null
                val selected = selectedMarkerId?.let { id -> markers.firstOrNull { it.id == id } }
                if (selected != null) {
                    clusteringState.selectionRing = googleMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(selected.position.lat, selected.position.lng))
                            .icon(iconCache.selectionRingDescriptor)
                            .anchor(0.5f, 0.5f)
                            .zIndex(1f)
                            .flat(true)
                    )
                }
            }
        }
    }
}

/** Sobrevive a recomposiciones (remember), no a cambios de configuración: no hace falta más. */
private class ClusteringState {
    var manager: ClusterManager<AssetClusterItem>? = null
    var selectionRing: Marker? = null
}
