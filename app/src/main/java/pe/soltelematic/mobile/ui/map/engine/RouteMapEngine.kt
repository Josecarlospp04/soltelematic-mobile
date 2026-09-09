package pe.soltelematic.mobile.ui.map.engine

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pe.soltelematic.mobile.domain.model.GeoPoint

/**
 * Contrato separado de MapEngine (Sprint 1): esa abstracción es específica de clustering de
 * assets en vivo (ClusterManager, iconos por unidad) y no tiene sentido forzarle conceptos de
 * ruta (polyline, marcadores de parada/inicio/fin) que nunca usa. Misma filosofía de todos
 * modos -- aísla el SDK de mapas de la pantalla de Historial (ver GoogleRouteMapEngine, la única
 * implementación hoy).
 */

/** Un punto del polyline de un "drive". colorHex viene del servidor, calculado según velocidad. */
data class RoutePoint(val point: GeoPoint, val colorHex: String?)

/**
 * legIndex es la posición del tramo "drive" en HistoryRoute.legs -- permite al engine resaltar
 * en verde el polyline completo del tramo seleccionado (en vez de sus colores por velocidad)
 * cuando legIndex == selectedLegIndex, sin que el mapper tenga que reconstruir nada.
 */
data class RoutePolyline(val legIndex: Int, val points: List<RoutePoint>)

enum class RouteMarkerRole { ROUTE_START, ROUTE_END, STOP }

/**
 * legIndex referencia la posición del tramo (HistoryRoute.legs) que este marcador representa --
 * es lo que permite el vínculo bidireccional con la línea de tiempo (Bloque 3/4): tocar el
 * marcador selecciona esa fila, seleccionar la fila resalta este marcador.
 */
data class RouteMarkerData(
    val legIndex: Int,
    val position: GeoPoint,
    val role: RouteMarkerRole
)

interface RouteMapEngine {

    @Composable
    fun rememberCameraController(): MapCameraController

    @Composable
    fun Content(
        modifier: Modifier,
        cameraController: MapCameraController,
        polylines: List<RoutePolyline>,
        markers: List<RouteMarkerData>,
        selectedLegIndex: Int?,
        // Contrato: nunca abrir un InfoWindow/popup -- esos datos ya están en la línea de tiempo
        // (ver plan del Sprint 2B). El único efecto de tocar un marcador es reportar legIndex.
        onMarkerClick: (Int) -> Unit,
        // Posición actual del marcador de reproducción (ver HistoryViewModel.playback), null si no
        // hay reproducción en curso -- la ruta completa (polylines de arriba) se sigue dibujando
        // igual, este marcador solo se superpone encima, nunca la reemplaza ni la dibuja de a poco.
        //
        // Sin valor por defecto a propósito: un default en un miembro @Composable de una interfaz
        // no genera el bridge $default correctamente (AbstractMethodError en runtime al llamarlo a
        // través del tipo de interfaz -- confirmado en dispositivo). Todo caller, incluidos los
        // previews, pasa estos dos parámetros explícitos.
        playbackPoint: GeoPoint?,
        // Se dispara la primera vez que el usuario arrastra el mapa a mano (GESTURE, nunca por
        // centerOn/moveInstantly propios del engine) -- HistoryScreen lo usa para dejar de mover
        // la cámara detrás del marcador en cuanto el usuario le pelea el gesto.
        onCameraGesture: () -> Unit
    )
}
