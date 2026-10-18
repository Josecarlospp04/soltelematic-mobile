package pe.soltelematic.mobile.core.time

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// time.formatted en todo el resto de la app: dd-MM-yyyy HH:mm:ss, no ISO 8601.
private val DEVICE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")

/**
 * Instante real de un time.formatted del servidor ("29-08-2026 13:59:05"), que viene ya en la hora
 * LOCAL de la cuenta y sin zona. null si falta o no cumple el patrón (mejor sin "hace X" que un
 * crash de parseo).
 *
 * zone es la zona en la que está ese texto. Hoy se asume la del dispositivo (la misma que la del
 * servidor, Perú): /user solo trae timezone_id, un id numérico de la tabla timezones del servidor,
 * sin nombre IANA ni offset, así que no hay de dónde sacar una zona de cuenta fiable. Cuando exista,
 * se pasa acá; el resto de la app no cambia.
 */
fun parseDeviceTime(formatted: String?, zone: ZoneId = ZoneId.systemDefault()): Instant? =
    formatted?.let {
        runCatching { LocalDateTime.parse(it, DEVICE_TIME_FORMAT).atZone(zone).toInstant() }.getOrNull()
    }

/**
 * Último reporte de una unidad de devices/map o devices/latest. El time.timestamp de esos
 * endpoints llega desplazado ~5 h (el servidor toma la hora local del formatted como si fuera UTC,
 * igual que ya pasaba en device/{id}), así que se prefiere parsear formatted; timestamp solo
 * sirve de respaldo si formatted falta o no se puede parsear.
 */
fun resolveLastSeenAt(formatted: String?, timestamp: Long?, zone: ZoneId = ZoneId.systemDefault()): Instant? =
    parseDeviceTime(formatted, zone) ?: timestamp?.let(Instant::ofEpochSecond)
