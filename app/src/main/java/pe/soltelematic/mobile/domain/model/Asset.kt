package pe.soltelematic.mobile.domain.model

import java.time.Instant

/** Nombrado Asset, no Unit: Unit es un tipo reservado de Kotlin. */
data class Asset(
    val id: Int,
    val groupId: Int?,
    val name: String?,
    val active: Boolean?,
    val position: GeoPoint?,
    val speedKph: Double?, // para lógica (p. ej. bloqueo de comandos por velocidad)
    val speedText: String?, // ya formateado por el servidor, para mostrar
    val ignition: Ignition,
    val status: AssetStatus,
    val icon: AssetIcon,
    val tail: List<GeoPoint>,
    val tailColorHex: String?,
    val lastSeenAt: Instant?,
    val lastSeenFormatted: String?
)

/** null/false/true del servidor son tres estados distintos: sin sensor, apagado, encendido. */
enum class Ignition { NO_SENSOR, OFF, ON }

data class AssetStatus(
    val type: AssetStatusType,
    val title: String?, // ya traducido por el servidor, nunca se traduce en la app
    val colorHex: String? // hex del servidor, la app no define su propia paleta
)

/**
 * engine (ralentí: motor encendido sin movimiento) se deja separado de ack aunque hoy ambos
 * caigan en el chip "Detenidas" (ver AssetFilter): si más adelante se quiere un chip propio de
 * "En ralentí", es un cambio de mapeo de chip, no de este enum.
 */
enum class AssetStatusType { OFFLINE, ONLINE, ACK, ENGINE, BLOCKED, UNKNOWN }

data class AssetIcon(
    val url: String?, // ya reescrito sobre el host real, ver IconUrlResolver
    val colorHex: String?, // presente cuando la unidad no tiene PNG propio
    val courseDegrees: Float
)
