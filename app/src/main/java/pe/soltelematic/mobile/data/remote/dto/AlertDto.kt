package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * GET alerts. Cada alerta se deja como JsonObject, NO como data class: el servidor mezcla tipos
 * en un mismo campo (active, schedule, command.active y notifications.*.active llegan como int,
 * string "0"/"1" o bool; zone como int o string) y cada tipo trae sus propias claves dinámicas.
 * El mapeo tolerante vive en AlertMapper (ver looseBoolean/looseInt).
 */
@Serializable
data class AlertsListResponseDto(
    val status: Int? = null,
    val alerts: List<JsonObject> = emptyList()
)

/** GET alerts/form: de momento solo se leen los títulos de types (attributes se ignora). */
@Serializable
data class AlertFormResponseDto(
    val types: List<AlertFormTypeDto> = emptyList()
)

@Serializable
data class AlertFormTypeDto(
    val type: String? = null,
    val title: String? = null
)

/** Body de POST alerts/active: id es lista aunque sea una sola alerta. */
@Serializable
data class AlertActiveRequestDto(
    val id: List<Int>,
    val active: Boolean
)

@Serializable
data class AlertStatusResponseDto(
    val status: Int? = null
)
