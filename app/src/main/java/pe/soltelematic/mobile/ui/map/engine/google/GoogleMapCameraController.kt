package pe.soltelematic.mobile.ui.map.engine.google

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.ui.map.engine.MapCameraController

// Respiro extra en px alrededor del bounding box, ADEMÁS del contentPadding que GoogleMapEngine.Content
// ya le pasa a GoogleMap (ver ese archivo): ese reserva el área tapada por los overlays de la
// pantalla, esto es solo para que las unidades de los bordes no queden pegadas al filo del área
// que sí queda libre.
private const val FIT_ALL_PADDING_PX = 120

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
