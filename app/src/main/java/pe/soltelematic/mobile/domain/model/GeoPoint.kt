package pe.soltelematic.mobile.domain.model

/** Punto propio, no LatLng de Google: el dominio no depende de un proveedor de mapas concreto. */
data class GeoPoint(val lat: Double, val lng: Double)
