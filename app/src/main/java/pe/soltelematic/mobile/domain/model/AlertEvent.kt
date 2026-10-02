package pe.soltelematic.mobile.domain.model

import java.time.Instant

/**
 * Un evento de la bandeja de alertas (GET events, Sprint 3A). Sin campo "seen": ese estado
 * depende de SeenEventsStore, que vive fuera de este modelo y puede cambiar sin releer de red
 * -- lo calcula la capa de UI comparando id contra el último visto, no esta capa de datos.
 */
data class AlertEvent(
    val id: Int,
    val type: AlertEventType,
    val alertId: Int?,
    val alertName: String?,
    val deviceId: Int?,
    val deviceName: String?,
    val name: String?, // nombre del evento, ya traducido por el servidor
    // Su significado depende del tipo: overspeed = umbral ("5 kph"); fuel_fill/theft = "sensor, cantidad"
    // ("PRINCIPAL, 22"); geocerca = nombre de la geocerca; conductor = nombre; duraciones = "N minutos".
    val detail: String?,
    val speedText: String?, // speed.human; solo significativo en OVERSPEED (en otros suele ser "0 kph")
    val position: GeoPoint?,
    val occurredAt: Instant?,
    val occurredFormatted: String?
)
