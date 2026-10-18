package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
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

/**
 * GET alerts/form. Todo lo que no sea un título simple llega como JsonElement y se interpreta en
 * AlertFormMapper: los ids de options son número O texto, los campos nulos se omiten, y una forma
 * inesperada en un solo tipo no debe tumbar la lectura de todo el formulario (ni los títulos que
 * usa la lista de alertas).
 */
@Serializable
data class AlertFormResponseDto(
    val devices: JsonElement? = null,
    val types: List<AlertFormTypeDto> = emptyList(),
    val notifications: JsonElement? = null
)

@Serializable
data class AlertFormTypeDto(
    val type: String? = null,
    val title: String? = null,
    val attributes: JsonElement? = null
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
