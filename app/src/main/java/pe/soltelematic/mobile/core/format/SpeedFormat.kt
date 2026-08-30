package pe.soltelematic.mobile.core.format

private const val KM_PER_HOUR_LABEL = "KM/H"

// Cualquier variante que el servidor mande para "kilómetros por hora" -- comparado en minúsculas,
// sin importar cómo llegó capitalizado.
private val KM_PER_HOUR_ALIASES = setOf("kph", "km/h", "kmh")

/**
 * Unidad de velocidad tal como se debe escribir en toda la app: cualquier variante de
 * kilómetros por hora ("kph", "KPH", "km/h", sin importar mayúsculas) se normaliza a "KM/H".
 * speed.unit existe en el servidor precisamente porque no siempre es kph (podría venir en mph,
 * nudos, etc.) -- cualquier otra unidad se respeta tal cual la mandó el servidor, nunca se fuerza
 * a KM/H. null o vacío (no visto en datos reales salvo cuando sí es kph) cae a "KM/H" por
 * default, no a una unidad vacía.
 */
fun normalizeSpeedUnit(unit: String?): String {
    val trimmed = unit?.trim()
    return if (trimmed.isNullOrEmpty() || trimmed.lowercase() in KM_PER_HOUR_ALIASES) {
        KM_PER_HOUR_LABEL
    } else {
        trimmed
    }
}

/**
 * Para texto que el servidor ya compone con la unidad pegada al número (el umbral de una alerta,
 * ej. "5 kph", o un stat de historial como "speed_max" -- ahí no hay value/unit por separado que
 * pasarle a [normalizeSpeedUnit], ver AlertEvent.detail / UnitStat.value). Reemplaza el sufijo de
 * unidad si es alguna variante de kph; cualquier otro sufijo (otra unidad, o texto que no termina
 * en una unidad de velocidad) se deja intacto.
 */
fun normalizeSpeedUnitSuffix(text: String): String {
    val trimmedEnd = text.trimEnd()
    val alias = KM_PER_HOUR_ALIASES.firstOrNull { trimmedEnd.endsWith(it, ignoreCase = true) } ?: return text
    return trimmedEnd.dropLast(alias.length) + KM_PER_HOUR_LABEL
}
