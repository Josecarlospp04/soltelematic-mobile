package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * GET device/{id}, forma verificada contra el servidor real (Sprint 2A, Paso 0). Envuelto en
 * "data", a diferencia de /user o /server_config que no lo están -- cada endpoint de este backend
 * se verifica por separado, no se asume el mismo envoltorio en todos.
 *
 * Reutiliza AssetTimeDto/AssetSpeedDto/AssetStatusDto/AssetCoordinatesDto de AssetDto.kt: es
 * exactamente la misma forma que en /devices/map (lat/lng como cadena acá también). No es lo
 * mismo que /history, donde lat/lng llegan como número -- cada DTO respeta la forma real de su
 * propio endpoint, la normalización a un GeoPoint común pasa por el mapper, no por unificar DTOs.
 *
 * stats de este endpoint no se modela: son pobres (3 métricas fijas, sin claves) y no se usan --
 * el bloque "HOY" de la ficha sale de /history, que sí trae las métricas completas.
 */
@Serializable
data class AssetDetailResponseDto(
    val data: AssetDetailDto
)

@Serializable
data class AssetDetailDto(
    val id: Int,
    val name: String? = null,
    val time: AssetTimeDto? = null,
    val speed: AssetSpeedDto? = null,
    // null = sin sensor de ignición · false = apagado · true = encendido. Tres estados, no dos.
    @SerialName("engine_status") val engineStatus: Boolean? = null,
    val status: AssetStatusDto? = null,
    val coordinates: AssetCoordinatesDto? = null,
    val sensors: List<AssetSensorDto> = emptyList(),
    // driver no aparece en absoluto cuando no hay conductor asignado (ni como null) -- default
    // null alcanza. Ni driver ni una entrada no vacía de services se han visto con contenido
    // real todavía, así que se modelan como JSON crudo (ver AssetDetailMapper.toGenericFields)
    // en vez de asumir campos fijos que podrían no existir.
    val services: List<JsonElement> = emptyList(),
    val driver: JsonElement? = null
)

/**
 * value llega con espacio al final (ej. "292 ") y sin unidad -- se limpia con trim() en el
 * mapper, no acá (el DTO refleja la respuesta cruda tal cual). icon llega con host localhost,
 * igual que AssetIconDto -- se resuelve con el mismo IconUrlResolver, que ya es genérico.
 */
@Serializable
data class AssetSensorDto(
    val id: Int,
    val type: String? = null,
    val name: String? = null,
    val value: String? = null,
    val icon: String? = null
)
