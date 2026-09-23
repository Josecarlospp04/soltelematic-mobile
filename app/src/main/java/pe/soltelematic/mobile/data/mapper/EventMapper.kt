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
    type = resolveEventType(icon, alert?.name),
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
    // Se guarda el tipo CRUDO del servidor, no el resuelto: si mañana cambia la heurística de
    // nombres, los eventos ya cacheados se reinterpretan solos al leerse. Guardar el resultado
    // de resolve() congelaría la clasificación vieja en Room.
    type = icon.extractServerType().serverKey,
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
    type = AlertEventType.resolve(AlertEventType.fromServerKey(type), alertName),
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

/**
 * Ej. "http://127.0.0.1/assets/icons/events_ignition_off_l.svg" -> "ignition_off".
 *
 * El sufijo de tamaño es OPCIONAL: Event::getTypeIconAsset() lo omite en al menos un caso real
 * ("events_event.svg", para suscripción de dispositivo vencida y usuario vencido). El regex
 * anterior exigía "_[lms]" y esos iconos caían en UNKNOWN silenciosamente.
 *
 * El grupo es perezoso (\w+?) y el sufijo va anclado al final para que "ignition_off_l" no se
 * capture entero cuando el sufijo sí está presente.
 */
private val EVENT_TYPE_REGEX = Regex("""events_(\w+?)(?:_[lms])?\.svg""")

private fun String?.extractServerType(): AlertEventType {
    val key = this?.let { EVENT_TYPE_REGEX.find(it)?.groupValues?.get(1) }
        ?: return AlertEventType.UNKNOWN
    return AlertEventType.fromServerKey(key)
}

/**
 * El tipo del servidor manda cuando dice algo concreto. CUSTOM (el 70% de los eventos reales) y
 * UNKNOWN se afinan con el nombre de la alerta -- ver AlertEventType.fromAlertName.
 */
private fun resolveEventType(icon: String?, alertName: String?): AlertEventType =
    AlertEventType.resolve(icon.extractServerType(), alertName)