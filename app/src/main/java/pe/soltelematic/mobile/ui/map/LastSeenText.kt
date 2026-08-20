package pe.soltelematic.mobile.ui.map

import java.time.Duration
import java.time.Instant

/**
 * "hace X" en español sin RelativeDateTimeFormatter: la app hoy es solo en español (ver
 * strings.xml), así que una función simple es más clara que arrastrar un formateador de i18n
 * para un solo idioma.
 */
fun relativeLastSeenText(lastSeenAt: Instant?, now: Instant = Instant.now()): String {
    if (lastSeenAt == null) return "sin datos"
    val elapsed = Duration.between(lastSeenAt, now)
    return when {
        elapsed.toMinutes() < 1 -> "hace instantes"
        elapsed.toHours() < 1 -> "hace ${elapsed.toMinutes()} min"
        elapsed.toDays() < 1 -> "hace ${elapsed.toHours()} h"
        else -> "hace ${elapsed.toDays()} d"
    }
}
