package pe.soltelematic.mobile.domain.model

/**
 * Sealed en vez de type + campos nullable: un círculo sin radio o un polígono sin vértices no
 * puede existir -- el compilador lo impide, la pantalla no necesita volver a validarlo al dibujar.
 */
sealed interface GeofenceShape {
    data class Polygon(val vertices: List<GeoPoint>) : GeofenceShape
    data class Circle(val center: GeoPoint, val radiusMeters: Double) : GeofenceShape
}
