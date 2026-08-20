package pe.soltelematic.mobile.core.time

import java.time.Duration
import java.time.Instant

/**
 * Solo colorea el texto "hace X" en el bottom sheet. El servidor ya decide si una unidad
 * está "sin reportar" (status.type == offline); esto es un matiz aparte sobre el reloj del
 * dispositivo, no una fuente de verdad sobre conectividad.
 */
enum class LastSeenFreshness { RECENT, STALE, VERY_STALE }

private val RECENT_THRESHOLD: Duration = Duration.ofHours(1)
private val STALE_THRESHOLD: Duration = Duration.ofHours(24)

fun lastSeenFreshness(lastSeenAt: Instant?, now: Instant = Instant.now()): LastSeenFreshness {
    if (lastSeenAt == null) return LastSeenFreshness.VERY_STALE
    val elapsed = Duration.between(lastSeenAt, now)
    return when {
        elapsed <= RECENT_THRESHOLD -> LastSeenFreshness.RECENT
        elapsed <= STALE_THRESHOLD -> LastSeenFreshness.STALE
        else -> LastSeenFreshness.VERY_STALE
    }
}
