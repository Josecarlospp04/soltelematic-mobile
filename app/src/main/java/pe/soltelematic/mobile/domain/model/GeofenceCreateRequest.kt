package pe.soltelematic.mobile.domain.model

/**
 * Reutiliza GeofenceShape (Polygon/Circle) tal cual -- no hay un tipo paralelo para "forma a
 * crear". Quien arma este request (ver GeofenceCreationState.canConfirmShape en MapUiState) ya
 * garantizó >=3 vértices o un radius>0 antes de que exista.
 */
data class GeofenceCreateRequest(
    val name: String,
    val shape: GeofenceShape,
    val colorHex: String,
    val speedLimit: Int? = null
)
