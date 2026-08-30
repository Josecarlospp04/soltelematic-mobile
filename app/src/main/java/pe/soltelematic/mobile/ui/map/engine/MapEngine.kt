package pe.soltelematic.mobile.ui.map.engine

import androidx.compose.foundation.layout.PaddingValues
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
    /** PNG propio de la unidad (vehículo, maquinaria, candado...); null = sin ícono, ver iconColorHex. */
    val iconUrl: String?,
    // Color de estado ya resuelto (statusMoving/Idle/Alert/Offline según AssetStatusType, ver
    // MapScreen.kt) como ARGB de Android, no el colorHex crudo del servidor: la píldora usa el
    // mismo mapeo de 4 colores que el resto de la app, no la paleta libre del backend. Resuelto
    // en MapScreen.kt (tiene LocalSoltelematicColors) para que este archivo siga sin depender de
    // ningún proveedor de mapas.
    val statusColorArgb: Int,
    /** true cuando status.type == OFFLINE: la píldora completa (fondo, ícono y texto) se dibuja atenuada. */
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
        onMapClick: () -> Unit,
        // Espacio real ocupado por los overlays de MapScreen (barra de búsqueda + chips arriba,
        // columna de FABs a la derecha), medido en runtime, no un margen fijo. El motor lo usa
        // para que ni sus controles propios ni el encuadre (fitAll) queden debajo de esos overlays.
        contentPadding: PaddingValues
    )
}
