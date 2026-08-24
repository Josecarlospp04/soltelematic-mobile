package pe.soltelematic.mobile.ui.map.engine.google

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.ui.map.engine.MapCameraController
import pe.soltelematic.mobile.ui.map.engine.RouteMapEngine
import pe.soltelematic.mobile.ui.map.engine.RouteMarkerData
import pe.soltelematic.mobile.ui.map.engine.RouteMarkerRole
import pe.soltelematic.mobile.ui.map.engine.RoutePoint
import pe.soltelematic.mobile.ui.map.engine.RoutePolyline

private const val POLYLINE_WIDTH_PX = 10f
private const val SELECTED_POLYLINE_WIDTH_PX = 16f
private const val DEFAULT_POLYLINE_COLOR = "#757575" // gris neutro: positions.c ausente

// Un recorrido largo trae miles de posiciones (verificado con datos reales: 31,132 puntos en 7
// días de una sola unidad) -- ver investigación de la corrección post-2B. Dibujar un Polyline
// nativo por cada par de puntos crashea la app (OutOfMemoryError dentro de addPolyline, cada
// llamada es su propia transacción Binder). toRenderablePolylineRuns() ataca esto en dos capas:
// agrupar por color consecutivo (sin pérdida) + Douglas-Peucker (con pérdida geométrica, acotada).
//
// BASE conservador a propósito: en cuadras urbanas angostas, un tolerance alto puede "cortar
// esquina" y hacer parecer que la unidad atravesó una manzana -- confirmado visualmente contra el
// trazado real de calles en Tarapoto antes de fijar este valor (ver notas de verificación).
// ESCALATED es la red de seguridad: si el presupuesto de puntos (MAX_TOTAL_POLYLINE_POINTS) no
// alcanza ni con BASE, se re-simplifica una vez más agresivo antes de, como último recurso,
// truncar runs completos -- el mapa puede quedar incompleto, la app nunca debe volver a crashear
// por esto sin importar cuántos días abarque el rango elegido (hasta 31, ver HistoryDateRange).
private const val BASE_SIMPLIFY_TOLERANCE_METERS = 3.0
private const val ESCALATED_SIMPLIFY_TOLERANCE_METERS = 25.0
private const val MAX_TOTAL_POLYLINE_POINTS = 6_000

// Mismo verde que LastSeenFreshness.RECENT en AssetBottomSheet.kt: un solo "verde de la app", no
// dos tonos distintos para dos usos de "esto es lo que importa ahora mismo".
private val SELECTED_POLYLINE_COLOR = Color(0xFF2E7D32)

private const val HUE_ROUTE_START = BitmapDescriptorFactory.HUE_GREEN
private const val HUE_ROUTE_END = BitmapDescriptorFactory.HUE_RED
private const val HUE_STOP = BitmapDescriptorFactory.HUE_ORANGE
private const val HUE_SELECTED = BitmapDescriptorFactory.HUE_YELLOW

/**
 * Sin ClusterManager ni MarkerIconCache a propósito: a diferencia de GoogleMapEngine (200+
 * unidades, refrescos frecuentes por polling), acá se pinta una vez por apertura de pantalla y
 * el conteo de marcadores es chico (paradas de un día). Markers/Polyline declarativos de
 * maps-compose alcanzan sin el costo de mantenerlos -- no hay el problema de rendimiento que
 * motivó el enfoque imperativo del Bloque 7.
 *
 * Pines por hue (BitmapDescriptorFactory.defaultMarker), no bitmaps propios: a diferencia de los
 * marcadores de unidad (con icono real por modelo de equipo), acá no hay un asset visual que
 * resolver, así que un pin de color ya cumple "distinguible claramente" sin generar bitmaps.
 */
class GoogleRouteMapEngine : RouteMapEngine {

    @Composable
    override fun rememberCameraController(): MapCameraController {
        val cameraPositionState = rememberCameraPositionState()
        val scope = rememberCoroutineScope()
        return GoogleMapCameraController(cameraPositionState, scope)
    }

    @Composable
    override fun Content(
        modifier: Modifier,
        cameraController: MapCameraController,
        polylines: List<RoutePolyline>,
        markers: List<RouteMarkerData>,
        selectedLegIndex: Int?,
        onMarkerClick: (Int) -> Unit
    ) {
        // Casteo seguro: el único MapCameraController que existe hoy para este contrato es el
        // que devuelve rememberCameraController() de esta misma clase.
        val googleController = cameraController as GoogleMapCameraController

        GoogleMap(
            modifier = modifier,
            cameraPositionState = googleController.cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = false)
        ) {
            // Tramo seleccionado aparte, siempre a fidelidad completa (nunca pasa por el
            // presupuesto de puntos ni por simplify): es un solo Polyline, no miles, así que no
            // hay riesgo de memoria, y "resaltar el tramo completo" pierde sentido si se recorta.
            polylines.firstOrNull { it.legIndex == selectedLegIndex }?.let { selected ->
                Polyline(
                    points = selected.points.map { it.point.toLatLng() },
                    color = SELECTED_POLYLINE_COLOR,
                    width = SELECTED_POLYLINE_WIDTH_PX,
                    zIndex = 1f,
                    clickable = false
                )
            }

            // El resto del recorrido sí pasa por el presupuesto de puntos + simplify -- acá es
            // donde vivían los miles de Polyline que crasheaban la app.
            polylines.filter { it.legIndex != selectedLegIndex }
                .toRenderablePolylineRuns()
                .forEach { (colorHex, points) ->
                    Polyline(
                        points = points,
                        color = (colorHex ?: DEFAULT_POLYLINE_COLOR).toComposeColor(),
                        width = POLYLINE_WIDTH_PX,
                        clickable = false
                    )
                }

            markers.forEach { marker ->
                val isSelected = marker.legIndex == selectedLegIndex
                Marker(
                    state = rememberMarkerState(position = marker.position.toLatLng()),
                    icon = BitmapDescriptorFactory.defaultMarker(
                        if (isSelected) HUE_SELECTED else marker.role.toHue()
                    ),
                    zIndex = if (isSelected) 1f else 0f,
                    // Contrato del engine: nunca popup. onClick consumido (true) evita que el
                    // SDK abra el InfoWindow por defecto.
                    onClick = {
                        onMarkerClick(marker.legIndex)
                        true
                    }
                )
            }
        }
    }
}

private fun RouteMarkerRole.toHue(): Float = when (this) {
    RouteMarkerRole.ROUTE_START -> HUE_ROUTE_START
    RouteMarkerRole.ROUTE_END -> HUE_ROUTE_END
    RouteMarkerRole.STOP -> HUE_STOP
}

/**
 * Todo el recorrido no seleccionado, listo para dibujar: agrupado por color consecutivo y
 * simplificado, con presupuesto de puntos global (no por tramo -- lo que importa para memoria es
 * el total de objetos Polyline de la pantalla, no cuántos le tocan a cada viaje).
 *
 * Tres pasadas, cada una solo si la anterior no alcanzó el presupuesto:
 * 1. BASE_SIMPLIFY_TOLERANCE_METERS (conservador, ver nota arriba).
 * 2. ESCALATED_SIMPLIFY_TOLERANCE_METERS (más agresivo, para rangos largos con muchos puntos --
 *    p. ej. hasta 31 días de una unidad muy activa).
 * 3. Si ni así entra en el presupuesto (caso extremo, no visto con datos reales hasta hoy):
 *    truncar runs completos hasta caber. El mapa queda incompleto -- la lista de abajo (Bloque 3)
 *    sigue mostrando el día completo igual, esto solo recorta el dibujo del mapa -- pero la app
 *    nunca vuelve a crashear por esto.
 */
private fun List<RoutePolyline>.toRenderablePolylineRuns(): List<Pair<String?, List<LatLng>>> {
    val baseRuns = toColorRuns(BASE_SIMPLIFY_TOLERANCE_METERS)
    if (baseRuns.sumOf { it.second.size } <= MAX_TOTAL_POLYLINE_POINTS) return baseRuns

    val escalatedRuns = toColorRuns(ESCALATED_SIMPLIFY_TOLERANCE_METERS)
    if (escalatedRuns.sumOf { it.second.size } <= MAX_TOTAL_POLYLINE_POINTS) return escalatedRuns

    val truncated = mutableListOf<Pair<String?, List<LatLng>>>()
    var budget = MAX_TOTAL_POLYLINE_POINTS
    for (run in escalatedRuns) {
        if (budget < 2) break
        val (colorHex, points) = run
        if (points.size <= budget) {
            truncated.add(run)
            budget -= points.size
        } else {
            truncated.add(colorHex to points.take(budget))
            budget = 0
        }
    }
    return truncated
}

private fun List<RoutePolyline>.toColorRuns(toleranceMeters: Double): List<Pair<String?, List<LatLng>>> =
    flatMap { it.points.toSimplifiedColorRuns(toleranceMeters) }

/**
 * Agrupa puntos consecutivos del mismo color (positions[].c) en tramos y simplifica la geometría
 * de cada uno con Douglas-Peucker (PolyUtil.simplify, de android-maps-utils -- ya dependencia del
 * proyecto), devolviendo un color + lista de LatLng por tramo, listo para un solo Polyline().
 *
 * Cada tramo nuevo (salvo el primero) arranca repitiendo el último punto del tramo anterior:
 * Douglas-Peucker siempre conserva los extremos de la lista que recibe, así que ese punto
 * compartido sobrevive la simplificación en ambos tramos y la línea no queda con un hueco visible
 * donde cambia el color. Un tramo que queda en menos de 2 puntos tras esto se descarta -- no es
 * geometría válida para un Polyline, y el punto ya quedó representado como extremo del tramo
 * vecino.
 */
private fun List<RoutePoint>.toSimplifiedColorRuns(toleranceMeters: Double): List<Pair<String?, List<LatLng>>> {
    if (size < 2) return emptyList()

    val runs = mutableListOf<MutableList<RoutePoint>>()
    for (point in this) {
        val previousRun = runs.lastOrNull()
        if (previousRun != null && previousRun.last().colorHex == point.colorHex) {
            previousRun.add(point)
        } else {
            runs.add(mutableListOf<RoutePoint>().apply {
                if (previousRun != null) add(previousRun.last())
                add(point)
            })
        }
    }

    return runs.mapNotNull { run ->
        if (run.size < 2) return@mapNotNull null
        val simplified = PolyUtil.simplify(run.map { it.point.toLatLng() }, toleranceMeters)
        run.first().colorHex to simplified
    }
}

private fun GeoPoint.toLatLng(): LatLng = LatLng(lat, lng)

private fun String.toComposeColor(): Color =
    runCatching { Color(AndroidColor.parseColor(this)) }.getOrDefault(Color(AndroidColor.parseColor(DEFAULT_POLYLINE_COLOR)))
