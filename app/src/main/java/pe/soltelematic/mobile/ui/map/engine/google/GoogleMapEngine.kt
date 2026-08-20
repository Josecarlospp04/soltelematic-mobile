package pe.soltelematic.mobile.ui.map.engine.google

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState
import pe.soltelematic.mobile.ui.map.engine.MapCameraController
import pe.soltelematic.mobile.ui.map.engine.MapEngine
import pe.soltelematic.mobile.ui.map.engine.MapMarkerData

private const val MARKER_SIZE_DP = 32
private const val SELECTED_MARKER_SIZE_DP = 44

/**
 * Implementación sobre Google Maps SDK (maps-compose + maps-compose-utils para clustering).
 * Es la única clase del proyecto que puede importar de com.google.android.gms.maps o
 * com.google.maps.android.compose -- si algún día se migra a MapLibre, esta clase se
 * reemplaza entera y ui/map/MapScreen.kt no cambia una línea.
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

        // Se salta las claves ya cacheadas (ver MarkerIconCache), así que en un refresco con
        // las mismas unidades esto no vuelve a pedir red.
        LaunchedEffect(markers) {
            iconCache.preload(markers)
        }

        val clusterItems = remember(markers) { markers.map { AssetClusterItem(it) } }

        GoogleMap(
            modifier = modifier,
            cameraPositionState = googleController.cameraPositionState,
            // myLocationEnabled solo debe llegar en true cuando quien llama ya confirmó el
            // permiso ACCESS_FINE_LOCATION; si no, el SDK de Google Maps lanza SecurityException.
            properties = MapProperties(isMyLocationEnabled = myLocationEnabled),
            uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
            onMapClick = { onMapClick() }
        ) {
            Clustering(
                items = clusterItems,
                onClusterItemClick = { item ->
                    onMarkerClick(item.markerData.id)
                    true
                },
                clusterItemContent = { item ->
                    val key = iconCache.keyFor(item.markerData.iconUrl, item.markerData.iconColorHex)
                    val bitmap = iconCache.entries[key]
                    // Si el bitmap todavía no llegó (primer frame tras un refresco grande) no se
                    // dibuja nada; en el siguiente recompose de ESA clave aparece.
                    if (bitmap != null) {
                        val size = if (item.markerData.id == selectedMarkerId) {
                            SELECTED_MARKER_SIZE_DP
                        } else {
                            MARKER_SIZE_DP
                        }
                        Image(
                            bitmap = bitmap,
                            contentDescription = item.markerData.title,
                            modifier = Modifier
                                .size(size.dp)
                                .graphicsLayer { rotationZ = item.markerData.courseDegrees }
                                .alpha(if (item.markerData.dimmed) 0.45f else 1f)
                        )
                    }
                }
            )
        }
    }
}
