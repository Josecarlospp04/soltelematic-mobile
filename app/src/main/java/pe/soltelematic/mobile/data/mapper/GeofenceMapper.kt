package pe.soltelematic.mobile.data.mapper

import pe.soltelematic.mobile.data.remote.dto.GeofenceDto
import pe.soltelematic.mobile.data.remote.dto.GeofencePointDto
import pe.soltelematic.mobile.domain.model.Geofence
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.GeofenceShape

private const val MIN_POLYGON_VERTICES = 3

// type explícito del servidor, mismo criterio que AlertEventType: un valor no reconocido cae en
// UNKNOWN y el mapper lo descarta, nunca lanza excepción.
private enum class GeofenceRawType(val serverKey: String) {
    POLYGON("polygon"),
    CIRCLE("circle"),
    UNKNOWN("");

    companion object {
        fun fromServerKey(key: String?): GeofenceRawType =
            entries.firstOrNull { it != UNKNOWN && it.serverKey == key } ?: UNKNOWN
    }
}

/**
 * Lo que no se puede pintar no llega al dominio -- mismo criterio que los legs de historial en 2B:
 * polígono con menos de 3 vértices, círculo sin center o con radius nulo o <= 0, y type desconocido
 * se descartan acá. active == false SÍ se conserva (ver Geofence).
 */
fun GeofenceDto.toDomain(): Geofence? {
    val shape = when (GeofenceRawType.fromServerKey(type)) {
        GeofenceRawType.POLYGON -> coordinates.toPolygonOrNull()
        GeofenceRawType.CIRCLE -> toCircleOrNull()
        GeofenceRawType.UNKNOWN -> null
    } ?: return null

    return Geofence(
        id = id,
        name = name ?: return null,
        colorHex = color ?: return null,
        active = active ?: return null,
        shape = shape
    )
}

private fun List<GeofencePointDto>?.toPolygonOrNull(): GeofenceShape.Polygon? {
    val vertices = this?.mapNotNull { it.toGeoPointOrNull() } ?: return null
    if (vertices.size < MIN_POLYGON_VERTICES) return null
    return GeofenceShape.Polygon(vertices)
}

private fun GeofenceDto.toCircleOrNull(): GeofenceShape.Circle? {
    val centerPoint = center?.toGeoPointOrNull() ?: return null
    val radiusValue = radius ?: return null
    if (radiusValue <= 0) return null
    return GeofenceShape.Circle(center = centerPoint, radiusMeters = radiusValue)
}

private fun GeofencePointDto.toGeoPointOrNull(): GeoPoint? {
    val latValue = lat ?: return null
    val lngValue = lng ?: return null
    return GeoPoint(latValue, lngValue)
}
