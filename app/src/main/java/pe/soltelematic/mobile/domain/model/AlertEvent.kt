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
    val detail: String?, // umbral configurado en la alerta, ej. "5 kph"
    val speedText: String?, // speed.human, valor real medido -- null si el evento no es de velocidad
    val position: GeoPoint?,
    val occurredAt: Instant?,
    val occurredFormatted: String?
)
