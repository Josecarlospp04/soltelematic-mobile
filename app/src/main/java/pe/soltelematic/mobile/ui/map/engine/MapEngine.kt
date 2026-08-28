package pe.soltelematic.mobile.ui.map.engine

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pe.soltelematic.mobile.domain.model.Geofence
import pe.soltelematic.mobile.domain.model.GeoPoint

/**
 * Vista mínima de un Asset para el motor de mapas. Deliberadamente no lleva velocidad,
 * ignición ni el resto de datos del bottom sheet: el motor solo necesita lo que hace falta
 * para dibujar y rotar un marcador.
 */
data class MapMarkerData(
    val id: Int,
    val position: GeoPoint,
    val title: String,
    val iconUrl: String?,
    val iconColorHex: String?,
    val courseDegrees: Float,
    /** true cuando status.type == OFFLINE: el marcador se dibuja atenuado. */
    val dimmed: Boolean
)

/**
 * Puente imperativo hacia la cámara del motor concreto. Los botones flotantes del mapa
 * (centrar en mi ubicación, ajustar zoom a todas) no son composables, así que necesitan un
 * objeto al que llamar desde un onClick normal en vez de una función @Composable.
 */
interface MapCameraController {
    fun centerOn(point: GeoPoint, zoomLevel: Float = DEFAULT_ZOOM)
    fun fitAll(points: List<GeoPoint>)

    companion object {
        const val DEFAULT_ZOOM = 16f
    }
}

/**
 * Todo lo que una pantalla puede pedirle a un mapa, sin nombrar Google Maps ni ningún otro
 * proveedor. Migrar a MapLibre es escribir una implementación nueva de esta interfaz, no
 * tocar ui/map/MapScreen.kt.
 */
interface MapEngine {

    @Composable
    fun rememberCameraController(): MapCameraController

    @Composable
    fun Content(
        modifier: Modifier,
        cameraController: MapCameraController,
        markers: List<MapMarkerData>,
        selectedMarkerId: Int?,
        // Contrato: solo pasar true cuando ACCESS_FINE_LOCATION ya está concedido. El motor no
        // pide el permiso, confía en que quien llama (MapScreen) ya lo hizo.
        myLocationEnabled: Boolean,
        // Se reutiliza el modelo de dominio tal cual (como GeoPoint): ya es agnóstico de
        // proveedor de mapas y trae exactamente lo necesario para dibujar, sin recortar campos
        // como sí hace MapMarkerData respecto de Asset. Lista ya vacía si el interruptor de la
        // pantalla está apagado -- el motor no conoce esa preferencia, solo dibuja lo que recibe.
        // Nunca clicables (ver GoogleMapEngine): competirían con el toque para seleccionar unidades.
        geofences: List<Geofence>,
        onMarkerClick: (Int) -> Unit,
        onMapClick: () -> Unit
    )
}
