package pe.soltelematic.mobile.ui.history

import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.HistoryDriveLeg
import pe.soltelematic.mobile.domain.model.HistoryLeg
import pe.soltelematic.mobile.domain.model.HistoryRoute
import pe.soltelematic.mobile.domain.model.HistoryStopLeg
import pe.soltelematic.mobile.ui.map.engine.RouteMarkerData
import pe.soltelematic.mobile.ui.map.engine.RouteMarkerRole
import pe.soltelematic.mobile.ui.map.engine.RoutePoint
import pe.soltelematic.mobile.ui.map.engine.RoutePolyline
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RouteMapData(
    val polylines: List<RoutePolyline>,
    val markers: List<RouteMarkerData>
)

// Umbral para considerar "el mismo sitio" el punto de partida y el de llegada de un día ida y
// vuelta. Mayor que la deriva típica de GPS estando parado (unos metros, ver nota del Bloque 1),
// suficiente para no confundir dos sitios genuinamente distintos que quedan cerca.
private const val SAME_LOCATION_THRESHOLD_METERS = 100.0

/**
 * legIndex de cada marcador es la posición del tramo en HistoryRoute.legs -- lo que permite el
 * vínculo bidireccional con la línea de tiempo (Bloque 3/4).
 *
 * El marcador de parada usa siempre start.point de la propia parada, nunca un promedio con end:
 * la deriva del GPS estando detenido no es movimiento real, y promediar inventaría un punto donde
 * la unidad nunca estuvo (confirmado con el usuario). Todas las paradas se listan, sin filtrar
 * por duración.
 *
 * ROUTE_START/ROUTE_END solo se agregan como marcador aparte cuando el primer/último tramo del
 * día es un "drive": si el día empieza o termina en una parada, esa parada ya ancla ese extremo
 * de la ruta -- un pin superpuesto en el mismo punto sería redundante.
 *
 * Excepción confirmada con el usuario: si el día empieza Y termina en una parada en el mismo
 * sitio (ida y vuelta), esas dos paradas son indistinguibles como dos pines STOP idénticos en la
 * misma posición -- en ese caso puntual, la primera se pinta como ROUTE_START y la última como
 * ROUTE_END en vez de STOP, para que el usuario distinga partida de llegada. Siguen siendo
 * paradas en la línea de tiempo -- esto solo cambia el rol visual del pin en el mapa.
 */
fun HistoryRoute.toRouteMapData(): RouteMapData {
    val firstLeg = legs.firstOrNull()
    val lastLeg = legs.lastOrNull()
    val roundTrip = isRoundTripAtSameStop(firstLeg, lastLeg)

    val stopMarkers = legs.withIndex().mapNotNull { (index, leg) ->
        if (leg !is HistoryStopLeg) return@mapNotNull null
        val point = leg.start.point ?: return@mapNotNull null
        val role = when {
            roundTrip && index == 0 -> RouteMarkerRole.ROUTE_START
            roundTrip && index == legs.lastIndex -> RouteMarkerRole.ROUTE_END
            else -> RouteMarkerRole.STOP
        }
        RouteMarkerData(legIndex = index, position = point, role = role)
    }

    val startMarker = if (!roundTrip) {
        (firstLeg as? HistoryDriveLeg)?.start?.point?.let { point ->
            RouteMarkerData(legIndex = 0, position = point, role = RouteMarkerRole.ROUTE_START)
        }
    } else null

    val endMarker = if (!roundTrip) {
        (lastLeg as? HistoryDriveLeg)?.end?.point?.let { point ->
            RouteMarkerData(legIndex = legs.lastIndex, position = point, role = RouteMarkerRole.ROUTE_END)
        }
    } else null

    val polylines = legs.withIndex().mapNotNull { (index, leg) ->
        if (leg !is HistoryDriveLeg || leg.positions.size < 2) return@mapNotNull null
        RoutePolyline(legIndex = index, points = leg.positions.map { RoutePoint(it.point, it.colorHex) })
    }

    return RouteMapData(
        polylines = polylines,
        markers = stopMarkers + listOfNotNull(startMarker, endMarker)
    )
}

/**
 * A dónde mover la cámara al seleccionar legIndex (marcador tocado en el mapa o fila tocada en
 * la línea de tiempo). Point para una parada: centrar y hacer zoom en su marcador ya alcanza para
 * "ver dónde se detuvo" -- prioriza el marcador ya calculado (markers) sobre start.point a mano
 * porque un tramo de borde (ROUTE_START/ROUTE_END, ver arriba) puede tener su pin en end.point,
 * y hay que centrar en el mismo punto que ve el pin, no en otro. Bounds para un viaje: un solo
 * punto no sirve para "ver el recorrido completo", así que se encuadra sobre todo el polyline del
 * tramo (confirmado con el usuario) -- sale directo de leg.positions, no de markers (los drives
 * interiores del día no tienen marcador propio).
 */
sealed interface CameraTarget {
    data class Point(val point: GeoPoint) : CameraTarget
    data class Bounds(val points: List<GeoPoint>) : CameraTarget
}

fun RouteMapData.cameraTargetFor(route: HistoryRoute, legIndex: Int): CameraTarget? {
    val leg = route.legs.getOrNull(legIndex) ?: return null
    if (leg is HistoryDriveLeg && leg.positions.size >= 2) {
        return CameraTarget.Bounds(leg.positions.map { it.point })
    }
    val point = markers.firstOrNull { it.legIndex == legIndex }?.position ?: leg.start.point
    return point?.let { CameraTarget.Point(it) }
}

private fun isRoundTripAtSameStop(firstLeg: HistoryLeg?, lastLeg: HistoryLeg?): Boolean {
    if (firstLeg !is HistoryStopLeg || lastLeg !is HistoryStopLeg || firstLeg === lastLeg) return false
    val start = firstLeg.start.point ?: return false
    val end = lastLeg.start.point ?: return false
    return distanceMeters(start, end) <= SAME_LOCATION_THRESHOLD_METERS
}

private fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val earthRadiusMeters = 6_371_000.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val h = sin(dLat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(dLng / 2).let { it * it }
    return 2 * earthRadiusMeters * asin(sqrt(h))
}
