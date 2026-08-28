package pe.soltelematic.mobile.ui.map.engine.google

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PatternItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.cos
import kotlin.math.hypot
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.Geofence
import pe.soltelematic.mobile.domain.model.GeofenceShape
import pe.soltelematic.mobile.ui.map.engine.MapCameraController
import pe.soltelematic.mobile.ui.map.engine.MapEngine
import pe.soltelematic.mobile.ui.map.engine.MapMarkerData

private const val GEOFENCE_STROKE_WIDTH_PX = 4f
private const val GEOFENCE_FILL_ALPHA_ACTIVE = 0.15f
private const val GEOFENCE_FILL_ALPHA_INACTIVE = 0.08f
private const val GEOFENCE_STROKE_ALPHA_ACTIVE = 1f
private const val GEOFENCE_STROKE_ALPHA_INACTIVE = 0.6f
private const val FALLBACK_GEOFENCE_COLOR = "#9E9E9E" // mismo gris neutro que MarkerIconCache
private val INACTIVE_GEOFENCE_STROKE_PATTERN: List<PatternItem> = listOf(Dash(20f), Gap(12f))

// Metros por grado de latitud, constante en toda la Tierra (a diferencia de longitud, que
// depende de la latitud -- ver approxFootprintMeters). Suficiente para una estimación de tamaño,
// no para geometría real: solo se usa para decidir qué geocerca dibujar encima de cuál.
private const val METERS_PER_DEGREE_LATITUDE = 111_320.0

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
        geofences: List<Geofence>,
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
            // Los marcadores de unidad se dibujan siempre por encima de los overlays de suelo
            // (polígonos, círculos) sin importar el zIndex -- son capas distintas en el SDK de
            // Google Maps. El zIndex de cada forma (ver approxFootprintMeters) solo ordena las
            // geocercas entre sí, para que una pequeña dentro de una grande no quede tapada.
            geofences.forEach { geofence ->
                geofence.Draw()
            }

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

/**
 * Nunca clicable (contrato de MapEngine): competiría con el toque para seleccionar unidades.
 * Sin etiqueta de nombre (decisión de producto): con varias geocercas superpuestas en pantalla
 * chica se amontonan. Inactiva (active == false) se dibuja atenuada -- relleno y contorno con
 * menos alpha, contorno punteado -- para que siga siendo referencia visual sin parecer que vigila.
 */
@Composable
@GoogleMapComposable
private fun Geofence.Draw() {
    val fillAlpha = if (active) GEOFENCE_FILL_ALPHA_ACTIVE else GEOFENCE_FILL_ALPHA_INACTIVE
    val strokeAlpha = if (active) GEOFENCE_STROKE_ALPHA_ACTIVE else GEOFENCE_STROKE_ALPHA_INACTIVE
    val strokePattern = if (active) null else INACTIVE_GEOFENCE_STROKE_PATTERN
    // Negativo: cuanto más grande la forma, más al fondo. Así una geocerca chica dentro de una
    // grande (radios reales de 1315.95 m y 563.48 m, ver Sprint 5) no queda tapada por el relleno
    // de la que la contiene.
    val zIndex = -shape.approxFootprintMeters().toFloat()

    when (val shape = shape) {
        is GeofenceShape.Polygon -> Polygon(
            points = shape.vertices.map { it.toLatLng() },
            clickable = false,
            fillColor = colorHex.toGeofenceColor(fillAlpha),
            strokeColor = colorHex.toGeofenceColor(strokeAlpha),
            strokePattern = strokePattern,
            strokeWidth = GEOFENCE_STROKE_WIDTH_PX,
            zIndex = zIndex
        )
        is GeofenceShape.Circle -> Circle(
            center = shape.center.toLatLng(),
            radius = shape.radiusMeters,
            clickable = false,
            fillColor = colorHex.toGeofenceColor(fillAlpha),
            strokeColor = colorHex.toGeofenceColor(strokeAlpha),
            strokePattern = strokePattern,
            strokeWidth = GEOFENCE_STROKE_WIDTH_PX,
            zIndex = zIndex
        )
    }
}

/**
 * Estimación de tamaño en metros, NO geometría real -- solo para ordenar zIndex (ver Draw()).
 * Circle ya trae su radio; Polygon usa la diagonal de su bounding box / 2, con longitud corregida
 * por coseno de la latitud (un grado de longitud encoge hacia los polos, uno de latitud no).
 */
private fun GeofenceShape.approxFootprintMeters(): Double = when (this) {
    is GeofenceShape.Circle -> radiusMeters
    is GeofenceShape.Polygon -> {
        val lats = vertices.map { it.lat }
        val lngs = vertices.map { it.lng }
        val latSpanMeters = (lats.max() - lats.min()) * METERS_PER_DEGREE_LATITUDE
        val lngSpanMeters = (lngs.max() - lngs.min()) * METERS_PER_DEGREE_LATITUDE * cos(Math.toRadians(lats.average()))
        hypot(latSpanMeters, lngSpanMeters) / 2
    }
}

private fun GeoPoint.toLatLng(): LatLng = LatLng(lat, lng)

private fun String.toGeofenceColor(alpha: Float): Color {
    val argb = runCatching { AndroidColor.parseColor(this) }
        .getOrDefault(AndroidColor.parseColor(FALLBACK_GEOFENCE_COLOR))
    return Color(argb).copy(alpha = alpha)
}
