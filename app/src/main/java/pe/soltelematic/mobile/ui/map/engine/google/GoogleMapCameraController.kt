package pe.soltelematic.mobile.ui.map.engine.google

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.tan
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.ui.map.engine.MapCameraController

// Respiro extra en px alrededor del bounding box, ADEMÁS del contentPadding que GoogleMapEngine.Content
// ya le pasa a GoogleMap (ver ese archivo): ese reserva el área tapada por los overlays de la
// pantalla, esto es solo para que las unidades de los bordes no queden pegadas al filo del área
// que sí queda libre.
private const val FIT_ALL_PADDING_PX = 120

// Fracción del área visible que ocupa la cola al encuadrar: el resto es margen.
private const val FIT_FRACTION = 0.8

// Los sondeos llegan cada ~7 s: una animación corta se nota como deslizamiento y termina mucho
// antes del siguiente.
private const val FOLLOW_ANIMATION_MS = 600

/**
 * cameraPositionState queda accesible (no privado) porque GoogleMapEngine.Content necesita
 * pasárselo al composable GoogleMap: es el mismo objeto, no una copia.
 */
class GoogleMapCameraController(
    val cameraPositionState: CameraPositionState,
    private val scope: CoroutineScope
) : MapCameraController {

    override fun centerOn(point: GeoPoint, zoomLevel: Float) {
        scope.launch {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(point.lat, point.lng), zoomLevel)
            )
        }
    }

    // Escribe cameraPositionState.position directo (map.moveCamera por debajo, ver
    // CameraPositionState.position setter) en vez de animate()/CameraUpdateFactory.newLatLngZoom:
    // un salto sin animación no tiene duración que pisar con el siguiente tick.
    override fun moveInstantly(point: GeoPoint) {
        val current = cameraPositionState.position
        cameraPositionState.position = CameraPosition(LatLng(point.lat, point.lng), current.zoom, current.tilt, current.bearing)
    }

    // newLatLng conserva zoom/tilt/bearing y centra en el área con padding. El padding se vuelve a
    // fijar antes de animar (mismo motivo que frame()): la hoja pudo abrirse o cerrarse sin que la
    // recomposición lo haya aplicado todavía. Sin mapa creado no hay nada que seguir.
    override fun followTo(point: GeoPoint) {
        val map = googleMap ?: return
        scope.launch {
            paddingPx?.let { (l, t, r, bt) -> map.setPadding(l, t, r, bt) }
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLng(LatLng(point.lat, point.lng)),
                FOLLOW_ANIMATION_MS
            )
        }
    }

    // Lo asigna GoogleMapEngine.Content (MapEffect) al crearse el mapa; null antes de eso.
    var googleMap: GoogleMap? = null

    // Padding efectivo (px: izquierda, arriba, derecha, abajo) que GoogleMapEngine.Content le pasa al
    // mapa. frame() lo vuelve a fijar justo antes de animar, así el encuadre no depende de si la
    // recomposición ya lo aplicó al SDK.
    var paddingPx: IntArray? = null

    override fun frame(points: List<GeoPoint>) {
        if (points.isEmpty()) return
        val map = googleMap
        if (points.size == 1 || map == null) {
            centerOn(points.last())
            return
        }
        val bounds = LatLngBounds.Builder().apply {
            points.forEach { include(LatLng(it.lat, it.lng)) }
        }.build()
        scope.launch {
            paddingPx?.let { (l, t, r, bt) -> map.setPadding(l, t, r, bt) }
            // Zoom calculado a mano (no newLatLngBounds + setMaxZoomPreference: con el zoom recortado
            // el SDK dejaba los límites descentrados). newLatLngZoom centra en el área con padding.
            val visible = cameraPositionState.projection?.visibleRegion?.latLngBounds
            val zoom = if (visible == null) {
                MapCameraController.DEFAULT_ZOOM
            } else {
                minOf(MapCameraController.DEFAULT_ZOOM, fitZoom(bounds, visible, cameraPositionState.position.zoom))
            }
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(bounds.center, zoom))
        }
    }

    /**
     * Zoom al que [bounds] ocupa FIT_FRACTION del área visible (la del mapa ya con padding,
     * [visible] = visibleRegion a [currentZoom]). Cada zoom de menos duplica lo que cabe, así que
     * basta la razón de tamaños; la latitud se compara en proyección Mercator.
     */
    private fun fitZoom(bounds: LatLngBounds, visible: LatLngBounds, currentZoom: Float): Float {
        fun mercatorY(latDeg: Double): Double {
            val lat = Math.toRadians(latDeg.coerceIn(-85.0, 85.0))
            return ln(tan(Math.PI / 4 + lat / 2))
        }
        val boundsW = Math.toRadians(bounds.northeast.longitude - bounds.southwest.longitude)
        val boundsH = mercatorY(bounds.northeast.latitude) - mercatorY(bounds.southwest.latitude)
        val visibleW = Math.toRadians(visible.northeast.longitude - visible.southwest.longitude)
        val visibleH = mercatorY(visible.northeast.latitude) - mercatorY(visible.southwest.latitude)
        val ratio = maxOf(boundsW / (visibleW * FIT_FRACTION), boundsH / (visibleH * FIT_FRACTION))
        // Límites sin extensión (puntos casi idénticos): no hay nada que encuadrar, vale el tope.
        if (ratio <= 0.0 || ratio.isNaN()) return MapCameraController.DEFAULT_ZOOM
        return (currentZoom - ln(ratio) / ln(2.0)).toFloat()
    }

    override fun fitAll(points: List<GeoPoint>) {
        if (points.isEmpty()) return
        if (points.size == 1) {
            centerOn(points.first())
            return
        }
        val bounds = LatLngBounds.Builder().apply {
            points.forEach { include(LatLng(it.lat, it.lng)) }
        }.build()
        scope.launch {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, FIT_ALL_PADDING_PX))
        }
    }
}
