package pe.soltelematic.mobile.domain.model

/** active == false: la geocerca existe pero no vigila -- no se filtra, la pantalla la dibuja atenuada. */
data class Geofence(
    val id: Int,
    val name: String,
    val colorHex: String,
    val active: Boolean,
    val shape: GeofenceShape
)
