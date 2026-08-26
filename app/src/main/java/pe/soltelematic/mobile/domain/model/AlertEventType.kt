package pe.soltelematic.mobile.domain.model

/**
 * El servidor no devuelve el tipo de evento (aunque existe en su base de datos) -- se infiere
 * del nombre de archivo del icono SVG, ej. "events_overspeed_l.svg" -> "overspeed",
 * "events_ignition_off_l.svg" -> "ignition_off" (ver EventMapper.extractEventType). serverKey se
 * conserva para poder usarlo como parámetro `type` en /events si algún día se expone ese filtro.
 *
 * ⚠️ Verificados contra payload real (Sprint 3A): OVERSPEED, IGNITION_ON, IGNITION_OFF.
 * GEOFENCE_IN/GEOFENCE_OUT siguen siendo hipótesis a partir de los tipos de alerta conocidos de
 * GPSWOX -- su serverKey puede no coincidir con el nombre real del icono, se confirman cuando
 * llegue un evento real de cada uno. UNKNOWN es el respaldo: un tipo no reconocido nunca debe
 * lanzar excepción.
 */
enum class AlertEventType(val serverKey: String) {
    OVERSPEED("overspeed"),
    IGNITION_ON("ignition_on"),
    IGNITION_OFF("ignition_off"),
    GEOFENCE_IN("geofence_in"),
    GEOFENCE_OUT("geofence_out"),
    UNKNOWN("");

    companion object {
        fun fromServerKey(key: String): AlertEventType =
            entries.firstOrNull { it != UNKNOWN && it.serverKey == key } ?: UNKNOWN
    }
}
