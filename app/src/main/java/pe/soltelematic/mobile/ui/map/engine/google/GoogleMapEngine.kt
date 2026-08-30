package pe.soltelematic.mobile.ui.map.engine.google

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
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

// Centro aproximado de Perú, con zoom amplio para que se vea el país completo. Posición inicial
// de la cámara mientras no hay datos: sin esto, CameraPositionState arranca en (0,0) -- frente a
// África, la posición por defecto de Google Maps -- y el salto al encuadre real de la flota es
// enorme y visible. Con esto el salto es corto (Perú -> zona real de la flota).
private val PERU_CENTER = LatLng(-9.19, -75.0152)
private const val PERU_INITIAL_ZOOM = 5f

// Alto que ocupa la atribución de Google Maps (logo + "Google", obligatoria, no se puede
// ocultar) en la esquina inferior. La dibuja el propio SDK, no es un composable de la app, así
// que no se puede medir con onSizeChanged como el resto de los overlays (ver MapScreen.kt) --
// de ahí que sea una constante y no una medición. Con margen generoso a propósito: un marcador
// justo al ras del logo se ve tan mal como uno tapado por él.
private val GOOGLE_ATTRIBUTION_RESERVED_HEIGHT = 40.dp

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
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(PERU_CENTER, PERU_INITIAL_ZOOM)
        }
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
        onMapClick: () -> Unit,
        contentPadding: PaddingValues
    ) {
        // Casteo seguro: el único MapCameraController que existe hoy es el que devuelve
        // rememberCameraController() de esta misma clase.
        val googleController = cameraController as GoogleMapCameraController
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val clusteringState = remember { ClusteringState() }
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current

        // Fondo/tinta de la píldora del marcador, resueltos acá (Compose tiene el tema) para
        // pasarlos a MarkerIconCache/AssetClusterRenderer, que no son @Composable. Se leen en cada
        // recomposición de Content -- MapEffect(markers) de abajo los usa cada vez que corre.
        val pillSurfaceArgb = MaterialTheme.colorScheme.surface.toArgb()
        val pillInkArgb = MaterialTheme.colorScheme.onSurface.toArgb()

        // contentPadding solo trae lo que MapScreen puede medir (barra+chips arriba, columna de
        // FABs a la derecha, ver MapScreen.kt) -- abajo hace falta sumar lo que le corresponde
        // a este motor concreto: la barra de navegación del sistema (safeDrawing, insets reales,
        // no una constante) más la atribución de Google (GOOGLE_ATTRIBUTION_RESERVED_HEIGHT,
        // constante porque el SDK la dibuja él mismo). Otro motor (p. ej. MapLibre) tendría su
        // propio cálculo acá, no en MapScreen.
        val systemNavigationBarHeight = with(density) { WindowInsets.safeDrawing.getBottom(density).toDp() }
        val effectiveContentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top = contentPadding.calculateTopPadding(),
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding() + systemNavigationBarHeight + GOOGLE_ATTRIBUTION_RESERVED_HEIGHT
        )

        GoogleMap(
            modifier = modifier,
            cameraPositionState = googleController.cameraPositionState,
            // myLocationEnabled solo debe llegar en true cuando quien llama ya confirmó el
            // permiso ACCESS_FINE_LOCATION; si no, el SDK de Google Maps lanza SecurityException.
            properties = MapProperties(isMyLocationEnabled = myLocationEnabled),
            uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
            // No es solo estético: el SDK usa este padding también para calcular el bounding box
            // visible en newLatLngBounds (ver GoogleMapCameraController.fitAll), así que un
            // encuadre automático ya no deja marcadores debajo de la barra de búsqueda/chips/FABs.
            contentPadding = effectiveContentPadding,
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
                    val renderer = AssetClusterRenderer(context, googleMap, newManager, iconCache)
                    newManager.renderer = renderer
                    clusteringState.renderer = renderer
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

                // El renderer se crea una sola vez (arriba); sus colores de píldora se refrescan
                // en cada corrida de este efecto, así un cambio de tema no deja bitmaps viejos.
                // let (no apply): apply expondría "this" como receptor implícito, y sus
                // propiedades pillSurfaceArgb/pillInkArgb tapan a las locales del mismo nombre.
                clusteringState.renderer?.let { renderer ->
                    renderer.pillSurfaceArgb = pillSurfaceArgb
                    renderer.pillInkArgb = pillInkArgb
                }

                // Se salta las claves ya cacheadas (ver MarkerIconCache), así que en un refresco
                // con las mismas unidades esto no vuelve a pedir red.
                iconCache.preload(markers, pillSurfaceArgb, pillInkArgb)
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
    var renderer: AssetClusterRenderer? = null
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
