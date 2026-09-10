package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * GET commands?connection=gprs&device_id={id}, forma verificada contra el servidor real. attributes
 * llega vacío u omitido para los comandos sin parámetros (ej. "alarmArm", "getVersion") -- el
 * default cubre ambos casos.
 */
@Serializable
data class CommandsResponseDto(
    val data: List<CommandDto> = emptyList()
)

@Serializable
data class CommandDto(
    val type: String,
    val title: String? = null,
    val connection: String? = null,
    val attributes: List<CommandAttributeDto> = emptyList()
)

/**
 * default y options[].id no siempre son texto (ej. default numérico en un atributo integer, id
 * numérico en las opciones de un select) -- se modelan como JsonElement y se resuelven a String en
 * CommandMapper, mismo criterio que AssetDetailDto.driver/services para formas no fijas del
 * servidor. type es String, no un enum: el servidor puede sumar tipos de campo nuevos sin avisar
 * (ver Tobuli\InputFields), el mapeo a CommandFieldType cae a TEXT para cualquiera no reconocido.
 */
@Serializable
data class CommandAttributeDto(
    val name: String,
    val title: String? = null,
    val type: String? = null,
    val default: JsonElement? = null,
    val description: String? = null,
    val required: Boolean = false,
    val options: List<CommandAttributeOptionDto>? = null,
    val max: Int? = null
)

@Serializable
data class CommandAttributeOptionDto(
    val id: JsonElement? = null,
    val title: String? = null
)

/**
 * POST commands. status=1 y status=0 son ambos 200 OK -- status=0 es un rechazo de negocio (unidad
 * sin conexión, sin permisos, límite de velocidad, etc.), no un error HTTP, así que no pasa por
 * ApiError/ApiCallExecutor. errors ya trae el nombre de la unidad incluido en cada mensaje (ver
 * SendCommandController.store en el servidor).
 */
@Serializable
data class SendCommandResponseDto(
    val status: Int,
    val message: String? = null,
    val errors: List<String>? = null
)
