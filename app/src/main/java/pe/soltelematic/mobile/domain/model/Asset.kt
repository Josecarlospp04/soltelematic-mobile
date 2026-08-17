package pe.soltelematic.mobile.domain.model

import java.time.Instant

/** Nombrado Asset, no Unit: Unit es un tipo reservado de Kotlin. Todavía sin pantalla que lo use. */
data class Asset(
    val id: Int,
    val groupId: Int?,
    val name: String?,
    val active: Boolean?,
    val speed: String?,
    val engineStatus: String?,
    val statusType: String?,
    val statusTitle: String?,
    val statusColor: String?,
    val lat: Double?,
    val lng: Double?,
    val lastSeenAt: Instant?,
    val lastSeenFormatted: String?
)
