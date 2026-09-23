package pe.soltelematic.mobile.data.mapper

import pe.soltelematic.mobile.data.remote.dto.SharingDto
import pe.soltelematic.mobile.domain.model.ShareLink
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// "yyyy-MM-dd HH:mm:ss", no ISO 8601 -- forma de expiration_date en GET clientlite/sharing (ver
// comentario de SharingDto.expirationDate sobre los DOS formatos reales, confirmados vía curl).
// Mismo patrón que HISTORY_DATE_FORMAT en AssetDetailRepositoryImpl.
private val SHARING_LOCAL_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private const val SHARING_LOCAL_DATE_LENGTH = 19 // "yyyy-MM-dd HH:mm:ss" son exactamente 19 caracteres.

// Sufijo de zona al final del valor (Z, o un offset +hh:mm/-hhmm): así se distingue el instante
// UTC explícito de POST (ISO 8601 con Z) de la hora local sin zona de GET (ver toInstantOrNull).
private val UTC_ZONE_SUFFIX = Regex("(Z|[+-]\\d{2}:?\\d{2})$")

/**
 * serverRootUrl es la raíz ya resuelta vía core/network/ServerRootUrl.kt (BuildConfig.BASE_URL),
 * la misma derivación que usa el enlace a la plataforma web en Cuenta -- NUNCA el campo url del
 * propio SharingDto (ver comentario ahí sobre por qué se ignora). Si serverRootUrl es null
 * (BASE_URL inválida) o faltan id/hash, no hay forma de construir un enlace usable y se descarta,
 * mismo criterio que GeofenceMapper.
 */
fun SharingDto.toDomain(serverRootUrl: String?): ShareLink? {
    val idValue = id ?: return null
    val hashValue = hash ?: return null
    val rootUrl = serverRootUrl ?: return null
    return ShareLink(
        id = idValue,
        hash = hashValue,
        url = rootUrl + "sharing/$hashValue",
        expiresAt = expirationDate?.toInstantOrNull()
    )
}

/**
 * expiration_date llega en DOS formatos según el endpoint (ver SharingDto.expirationDate):
 * - CON zona (POST clientlite/sharing, ISO 8601 con microsegundos y "Z", ej.
 *   "2026-09-18T14:04:04.056566Z"): ya es un instante UTC real -- se parsea tal cual, JAMÁS se
 *   vuelve a anclar a la zona del dispositivo (eso lo correría ~5h, el mismo error que
 *   time.timestamp en device/{id} -- ver cabecera de EventDto.kt -- pero al revés).
 * - SIN zona (GET clientlite/sharing, "yyyy-MM-dd HH:mm:ss", ej. "2026-09-19 04:02:20"): es hora
 *   LOCAL del servidor (Perú) sin marcar, mismo criterio que time.formatted en device/{id} (ver
 *   AssetDetailMapper.toInstantOrNull): se ancla a ZoneId.systemDefault(), asumiendo que el
 *   dispositivo está en esa misma zona (única asunción ya vigente en el resto de la app).
 *
 * Casos reales verificados vía curl (ver PATCH 5): "2026-09-18T14:04:04.056566Z" creado a las
 * 08:03 hora de Perú con duración de 1h debe mostrarse como 18-09-2026 09:04; "2026-09-19
 * 04:02:20" debe mostrarse como 19-09-2026 04:02 (ver SharingMapperTest).
 */
private fun String.toInstantOrNull(): Instant? =
    if (UTC_ZONE_SUFFIX.containsMatchIn(this)) toUtcInstantOrNull() else toLocalServerInstantOrNull()

private fun String.toUtcInstantOrNull(): Instant? =
    try {
        Instant.parse(this)
    } catch (e: DateTimeParseException) {
        // Instant.parse (ISO_INSTANT) solo acepta el sufijo "Z", no un offset explícito
        // (+05:00/-0500) -- si el servidor alguna vez manda uno, OffsetDateTime sí lo entiende.
        runCatching { OffsetDateTime.parse(this).toInstant() }.getOrNull()
    }

private fun String.toLocalServerInstantOrNull(): Instant? =
    runCatching {
        LocalDateTime.parse(this.take(SHARING_LOCAL_DATE_LENGTH), SHARING_LOCAL_DATE_FORMAT)
            .atZone(ZoneId.systemDefault())
            .toInstant()
    }.getOrNull()
