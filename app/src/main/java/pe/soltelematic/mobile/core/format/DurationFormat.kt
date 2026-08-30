package pe.soltelematic.mobile.core.format

private const val NO_DATA = "-" // mismo guion que ya usa el resto de la app para "sin dato"

/**
 * Duración compacta en horas y minutos, nunca segundos: el servidor manda duraciones como
 * "HH:MM:SS" (ver UnitStat.value en duration/drive_duration/stop_duration), y pintar el segundo
 * tal cual rompe la estética -- el texto es tan largo que se parte en dos líneas dentro de la
 * tarjeta. Los minutos se REDONDEAN, no se truncan: 3 min 50 s se muestra "4 min", no "3 min".
 * Por debajo de un minuto pero con actividad real (ej. 40 s) se muestra "<1 min" en vez de
 * "0 min", para que no parezca que no pasó nada. Cero real o valor no parseable cae al mismo
 * guion que ya usa el resto de la app (ver value ?: "-" en AssetBottomSheet/HistoryTimeline).
 *
 * Solo para DURACIONES (cuánto duró algo) -- nunca para timestamps/horas de reloj ("Último
 * reporte 20:44", "Últimos datos recibidos ..."), donde el segundo sí importa para diagnóstico y
 * no pasa por acá.
 */
fun formatDurationCompact(raw: String?): String {
    val totalSeconds = raw?.let(::parseDurationSeconds) ?: return NO_DATA
    if (totalSeconds <= 0) return NO_DATA
    if (totalSeconds < 60) return "<1 min"

    val totalMinutes = (totalSeconds + 30) / 60 // redondeo al minuto más cercano, no truncamiento
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "%dh %02d".format(hours, minutes) else "$minutes min"
}

/**
 * true si esta key de UnitStat es una duración ("duration", "drive_duration", "stop_duration",
 * y cualquier otra que el servidor agregue con el mismo sufijo -- los stats son dinámicos, ver
 * HistoryStatDto, así que esto se detecta por patrón y no por una lista cerrada). Mismo criterio
 * de heurística sobre key/title que ya usa statTileColor en SummaryTab.kt.
 */
fun isDurationStatKey(key: String?): Boolean =
    key == "duration" || key?.endsWith("_duration") == true

/**
 * "HH:MM:SS" (formato real del servidor) a segundos totales; tolera "MM:SS" por si alguna vez
 * omite la hora. null ante cualquier token no numérico -- return no local dentro de map (inline)
 * para no desalinear horas/minutos/segundos si un solo token falla.
 */
private fun parseDurationSeconds(raw: String): Long? {
    val tokens = raw.trim().split(":")
    val parsed = tokens.map { it.toLongOrNull() ?: return null }
    return when (parsed.size) {
        3 -> parsed[0] * 3600 + parsed[1] * 60 + parsed[2]
        2 -> parsed[0] * 60 + parsed[1]
        else -> null
    }
}
