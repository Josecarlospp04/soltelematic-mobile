package pe.soltelematic.mobile.domain.model

import java.time.Instant

/**
 * Ficha de una unidad (Sprint 2A). Distinto de Asset (Asset.kt): Asset es la forma resumida que
 * alimenta el mapa (devices/map + devices/latest); AssetDetail es la forma completa de
 * device/{id}, con sensores y campos que el mapa no necesita.
 */
data class AssetDetail(
    val id: Int,
    val name: String?,
    val status: AssetStatus, // reutiliza el mismo tipo que Asset: mismo servidor, mismo criterio de color/título
    val speedText: String?,
    val ignition: Ignition,
    val position: GeoPoint?,
    val lastSeenAt: Instant?,
    val lastSeenFormatted: String?,
    val sensors: List<AssetSensor>,
    // Cada entrada de services es su propia lista clave/valor (una tarjeta por servicio).
    val services: List<List<AssetDetailField>>,
    val driver: List<AssetDetailField>?
)

data class AssetSensor(
    val id: Int,
    val type: String?, // ej. "fuel_tank" -- permite agrupar o dar tratamiento visual por tipo
    val name: String?,
    val value: String?, // ya limpio (trim), sin unidad
    val iconUrl: String? // ya resuelto sobre el host real, ver IconUrlResolver
)

/**
 * Clave/valor genérico para driver y services: ninguno de los dos tiene una forma conocida
 * todavía (nunca se vio un payload con contenido real para ellos), así que se listan tal cual
 * lleguen en vez de asumir campos fijos que podrían no existir. title es null cuando el valor
 * crudo no era un objeto (primitivo o array).
 */
data class AssetDetailField(
    val title: String?,
    val value: String?
)

/** stats de GET history -- key es dinámico (p. ej. fuel_consumption_{sensorId}), se pinta tal cual llega. */
data class UnitStat(
    val key: String?,
    val title: String?,
    val value: String?
)
