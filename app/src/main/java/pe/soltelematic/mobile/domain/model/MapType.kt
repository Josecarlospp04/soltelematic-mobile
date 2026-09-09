package pe.soltelematic.mobile.domain.model

/**
 * Tipo de mapa base para el mapa en vivo (ver MapScreen). El SDK de mapas concreto (hoy Google
 * Maps, ver GoogleMapEngine) lo soporta nativamente -- no requiere datos propios ni llamada de
 * red, a diferencia de geocercas o assets.
 */
enum class MapType {
    NORMAL,
    SATELLITE,
    HYBRID,
    TERRAIN
}
