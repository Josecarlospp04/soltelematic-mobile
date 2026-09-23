package pe.soltelematic.mobile.domain.model

import java.text.Normalizer
import java.util.Locale

/**
 * El servidor no expone el tipo de evento en el payload de /events aunque SÍ lo tiene en la base
 * de datos (columna `events.type`). Se infiere del nombre de archivo del icono SVG que arma
 * Event::getTypeIconAsset(): "events_{type}_l.svg" en el caso general, con dos excepciones que no
 * llevan sufijo de tamaño ("events_event.svg") o usan un nombre fijo ("events_clock_l.svg").
 *
 * Tipos reales verificados contra la base de datos del servidor (SELECT type, COUNT(*) FROM
 * events): custom (70% del total), overspeed, ignition_on, ignition_off, zone_out, zone_in,
 * fuel_fill.
 *
 * ⚠️ CUSTOM son alertas configuradas por el instalador y son la mayoría de los eventos. Su `type`
 * no dice nada del contenido -- el significado está en el nombre de la alerta ("IGNICION
 * APAGADA", "EXCESO DE VELOCIDAD"). Por eso existe fromAlertName(): clasifica por palabras clave
 * cuando el tipo no es concluyente. Es heurística y depende de cómo nombre sus alertas cada
 * cliente; ante la duda cae en CUSTOM, que tiene su propio icono de campana y nunca se ve roto.
 *
 * Nota histórica: GEOFENCE_IN/GEOFENCE_OUT existían con serverKey "geofence_in"/"geofence_out" y
 * nunca llegaron a matchear -- los tipos reales son "zone_in"/"zone_out".
 */
enum class AlertEventType(val serverKey: String) {
    OVERSPEED("overspeed"),
    IGNITION_ON("ignition_on"),
    IGNITION_OFF("ignition_off"),
    GEOFENCE_IN("zone_in"),
    GEOFENCE_OUT("zone_out"),
    FUEL_FILL("fuel_fill"),
    FUEL_THEFT("fuel_theft"),
    SOS("sos"),
    POWER_CUT("power_cut"),
    LOW_BATTERY("low_battery"),
    CUSTOM("custom"),
    UNKNOWN("");

    companion object {
        fun fromServerKey(key: String): AlertEventType =
            entries.firstOrNull { it != UNKNOWN && it.serverKey == key } ?: UNKNOWN

        /**
         * Clasifica por el nombre de la alerta. Se usa cuando el tipo del servidor no dice nada
         * del contenido (CUSTOM) o no se pudo extraer (UNKNOWN).
         *
         * El texto se normaliza quitando acentos y pasando a minúsculas con Locale.ROOT: sin
         * Locale explícito, lowercase() usa el locale del dispositivo y en turco la I mayúscula
         * baja a "ı" (i sin punto), con lo que "IGNICION" no coincidiría con "ignicion".
         */
        fun fromAlertName(name: String?): AlertEventType {
            val text = name?.normalizeForMatch() ?: return CUSTOM

            return when {
                text.containsAny("ignicion on", "ignicion encendida", "encendido", "motor on") -> IGNITION_ON
                text.containsAny("ignicion off", "ignicion apagada", "apagado", "motor off") -> IGNITION_OFF
                text.containsAny("ignicion") -> IGNITION_ON
                text.containsAny("velocidad", "exceso", "overspeed") -> OVERSPEED
                text.containsAny("entrada", "ingreso", "entra", "zone in") -> GEOFENCE_IN
                text.containsAny("salida", "sale", "zone out") -> GEOFENCE_OUT
                text.containsAny("geocerca", "zona", "cerca") -> GEOFENCE_IN
                text.containsAny("robo", "hurto", "theft") -> FUEL_THEFT
                text.containsAny("combustible", "tanque", "fuel") -> FUEL_FILL
                text.containsAny("sos", "panico", "emergencia", "boton") -> SOS
                text.containsAny("corte", "desconexion", "desconectado", "power") -> POWER_CUT
                text.containsAny("bateria", "battery") -> LOW_BATTERY
                else -> CUSTOM
            }
        }

        /**
         * Resuelve el tipo final combinando lo que dijo el servidor con el nombre de la alerta.
         * Un tipo concreto del servidor siempre gana; solo CUSTOM y UNKNOWN se intentan afinar.
         */
        fun resolve(fromServer: AlertEventType, alertName: String?): AlertEventType =
            if (fromServer == CUSTOM || fromServer == UNKNOWN) {
                fromAlertName(alertName)
            } else {
                fromServer
            }

        private fun String.normalizeForMatch(): String =
            Normalizer.normalize(this, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase(Locale.ROOT)
                .trim()

        private fun String.containsAny(vararg needles: String): Boolean =
            needles.any { this.contains(it) }
    }
}