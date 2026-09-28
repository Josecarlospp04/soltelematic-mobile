package pe.soltelematic.mobile.domain.model

import java.time.Instant

/**
 * Serie de un parámetro para las gráficas del Historial (parche 8, GET clientlite/parameters).
 * Los puntos ya vienen en Instant, no en epoch crudo (conversión en ParameterMapper) -- y son
 * valores reales medidos: el servidor ya submuestreó con LTTB a máximo 500 puntos, la app no
 * vuelve a reducir nada.
 *
 * Solo existen las series que la unidad SÍ reporta -- getParameters nunca devuelve una
 * ParameterSeries con points vacío para un parámetro ausente, esa serie directamente no aparece
 * en la lista (ver ParametersResponseDto).
 */
data class ParameterSeries(
    val key: ParameterKey,
    val points: List<ParameterPoint>
)

data class ParameterPoint(
    val timestamp: Instant,
    val value: Double
)

/**
 * Las seis claves confirmadas contra el servidor real (parche 8) y su unidad para las etiquetas de
 * la gráfica. rssi y satellites no traen unidad real (señal sin unidad y conteo, respectivamente);
 * fuel depende del sensor de combustible propio de cada unidad, no tiene una unidad fija del lado
 * del servidor.
 *
 * UNKNOWN es la misma red de seguridad que AlertEventType.UNKNOWN: si el servidor agrega una clave
 * nueva algún día, la serie no se pierde ni rompe el mapper, simplemente no tiene unidad hasta que
 * se le dé de alta acá.
 */
enum class ParameterKey(val serverKey: String, val unit: String?) {
    VOLTAGE("voltage", "V"),
    BATTERY("battery", "%"),
    RSSI("rssi", null),
    SATELLITES("satellites", null),
    SPEED("speed", "km/h"),
    FUEL("fuel", null),
    UNKNOWN("", null);

    companion object {
        fun fromServerKey(key: String): ParameterKey =
            entries.firstOrNull { it != UNKNOWN && it.serverKey == key } ?: UNKNOWN
    }
}
