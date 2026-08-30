package pe.soltelematic.mobile.data.mapper

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.soltelematic.mobile.core.network.IconUrlResolver
import pe.soltelematic.mobile.data.remote.dto.AssetCoordinatesDto
import pe.soltelematic.mobile.data.remote.dto.AssetDetailDto
import pe.soltelematic.mobile.data.remote.dto.AssetSensorDto
import pe.soltelematic.mobile.data.remote.dto.HistoryStatDto
import pe.soltelematic.mobile.domain.model.AssetDetail
import pe.soltelematic.mobile.domain.model.AssetDetailField
import pe.soltelematic.mobile.domain.model.AssetSensor
import pe.soltelematic.mobile.domain.model.AssetStatus
import pe.soltelematic.mobile.domain.model.AssetStatusType
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.Ignition
import pe.soltelematic.mobile.domain.model.UnitStat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Mismo patrón que time.formatted en todo el resto de la app: dd-MM-yyyy HH:mm:ss, no ISO 8601.
private val DEVICE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")

fun AssetDetailDto.toDomain(iconUrlResolver: IconUrlResolver): AssetDetail = AssetDetail(
    id = id,
    name = name,
    status = AssetStatus(
        type = status?.type.toAssetStatusType(),
        title = status?.title,
        colorHex = status?.color
    ),
    speedValue = speed?.value,
    speedUnit = speed?.unit,
    ignition = when (engineStatus) {
        null -> Ignition.NO_SENSOR
        false -> Ignition.OFF
        true -> Ignition.ON
    },
    // lat/lng llegan como cadena en device/{id} (igual que en devices/map) -- distinto de
    // history, donde llegan como número. Cada DTO respeta la forma real de su propio endpoint;
    // acá es donde se normaliza a un GeoPoint común, no antes.
    position = coordinates?.toGeoPointOrNull(),
    // OJO: acá NO se usa time.timestamp (a diferencia de AssetMapper.kt, el de devices/map).
    // Confirmado en producción que el time.timestamp de device/{id} viene desfasado (~5h en
    // Perú, igual al offset UTC-5) mientras que time.formatted es correcto -- probablemente el
    // backend arma ese timestamp interpretando la hora local del formatted como si fuera UTC.
    // "hace X" y el color verde/ámbar/rojo dependen de este dato, así que se recalcula
    // parseando formatted en vez de confiar en el timestamp del servidor para este endpoint.
    lastSeenAt = time?.formatted?.toInstantOrNull(),
    lastSeenFormatted = time?.formatted,
    sensors = sensors.map { it.toDomain(iconUrlResolver) },
    services = services.map { it.toGenericFields() },
    driver = driver?.toGenericFields()
)

// Asume que el dispositivo está en la misma zona horaria que el servidor (Perú) -- ninguna otra
// parte de la app maneja usuarios en zonas horarias distintas hoy, así que no es una suposición
// nueva. runCatching por si el servidor cambia el patrón sin avisar: mejor sin "hace X" que un
// crash de parseo.
private fun String.toInstantOrNull(): Instant? =
    runCatching { LocalDateTime.parse(this, DEVICE_TIME_FORMAT).atZone(ZoneId.systemDefault()).toInstant() }
        .getOrNull()

private fun AssetCoordinatesDto.toGeoPointOrNull(): GeoPoint? {
    val latValue = lat?.toDoubleOrNull() ?: return null
    val lngValue = lng?.toDoubleOrNull() ?: return null
    return GeoPoint(latValue, lngValue)
}

private fun AssetSensorDto.toDomain(iconUrlResolver: IconUrlResolver): AssetSensor = AssetSensor(
    id = id,
    type = type,
    name = name,
    value = value?.trim(),
    iconUrl = iconUrlResolver.resolve(icon)
)

fun HistoryStatDto.toDomain(): UnitStat = UnitStat(key = key, title = title, value = value)

/**
 * driver/services no tienen forma conocida todavía (nunca se vio un payload con contenido real
 * para ninguno de los dos) -- se listan clave/valor tal cual lleguen en vez de asumir campos
 * fijos que podrían no existir. Si el elemento no es un objeto (primitivo o array), se muestra
 * como una única fila sin título en vez de descartarlo.
 */
private fun JsonElement.toGenericFields(): List<AssetDetailField> =
    (this as? JsonObject)?.entries?.map { (key, value) -> AssetDetailField(title = key, value = value.toDisplayValue()) }
        ?: listOf(AssetDetailField(title = null, value = toDisplayValue()))

private fun JsonElement.toDisplayValue(): String = (this as? JsonPrimitive)?.content ?: toString()

// Duplicado deliberado de la función homónima privada en AssetMapper.kt: ambas son privadas a
// nivel de archivo (no hay colisión), y tres líneas de mapeo no justifican compartir una función
// entre los dos mappers.
private fun String?.toAssetStatusType(): AssetStatusType = when (this) {
    "offline" -> AssetStatusType.OFFLINE
    "online" -> AssetStatusType.ONLINE
    "ack" -> AssetStatusType.ACK
    "engine" -> AssetStatusType.ENGINE
    "blocked" -> AssetStatusType.BLOCKED
    else -> AssetStatusType.UNKNOWN
}
