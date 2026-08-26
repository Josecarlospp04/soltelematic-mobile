package pe.soltelematic.mobile.data.mapper

import pe.soltelematic.mobile.data.local.entity.EventEntity
import pe.soltelematic.mobile.data.remote.dto.EventDto
import pe.soltelematic.mobile.domain.model.AlertEvent
import pe.soltelematic.mobile.domain.model.AlertEventType
import pe.soltelematic.mobile.domain.model.GeoPoint
import java.time.Instant

// Vía red directa (EventsRepository.searchEvents): no pasa por Room, así que no hay que ir y
// volver por EventEntity para llegar al dominio.
fun EventDto.toDomain(): AlertEvent = AlertEvent(
    id = id,
    type = icon.extractEventType(),
    alertId = alert?.id,
    alertName = alert?.name,
    deviceId = device?.id,
    deviceName = device?.name,
    name = name,
    detail = detail,
    speedText = speed?.human,
    position = if (coordinates?.lat != null && coordinates.lng != null) {
        GeoPoint(coordinates.lat, coordinates.lng)
    } else {
        null
    },
    occurredAt = time?.timestamp?.let(Instant::ofEpochSecond),
    occurredFormatted = time?.formatted
)

fun EventDto.toEntity(): EventEntity = EventEntity(
    id = id,
    type = icon.extractEventType().serverKey,
    alertId = alert?.id,
    alertName = alert?.name,
    deviceId = device?.id,
    deviceName = device?.name,
    name = name,
    detail = detail,
    speedValue = speed?.value,
    speedUnit = speed?.unit,
    speedHuman = speed?.human,
    lat = coordinates?.lat,
    lng = coordinates?.lng,
    occurredAt = time?.timestamp?.let(Instant::ofEpochSecond),
    occurredFormatted = time?.formatted
)

fun EventEntity.toDomain(): AlertEvent = AlertEvent(
    id = id,
    type = AlertEventType.fromServerKey(type),
    alertId = alertId,
    alertName = alertName,
    deviceId = deviceId,
    deviceName = deviceName,
    name = name,
    detail = detail,
    speedText = speedHuman,
    position = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
    occurredAt = occurredAt,
    occurredFormatted = occurredFormatted
)

// Ej. "http://127.0.0.1/assets/icons/events_ignition_off_l.svg" -> "ignition_off". El sufijo
// final es el tamaño del icono -- se ancla a los tres tamaños vistos ("_l"/"_m"/"_s") en vez de
// a "_\w+" genérico: así un icono con un sufijo de tamaño que no se ha visto todavía no se
// interpreta silenciosamente como parte del tipo, sino que no matchea y cae en UNKNOWN. Ver
// AlertEventType sobre qué tan verificado está cada valor.
private val EVENT_TYPE_REGEX = Regex("""events_(\w+)_[lms]\.svg""")

private fun String?.extractEventType(): AlertEventType {
    val key = this?.let { EVENT_TYPE_REGEX.find(it)?.groupValues?.get(1) } ?: return AlertEventType.UNKNOWN
    return AlertEventType.fromServerKey(key)
}
