package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * /devices/map. GPSWOX omite campos según la configuración de cada unidad, así que todo
 * lo que no sea la identidad (id) es nullable con default. `icon` y `tail` no se modelan:
 * su forma real no se conoce y el Json global tiene ignoreUnknownKeys, así que se ignoran solos.
 */
@Serializable
data class AssetDto(
    val id: Int,
    @SerialName("group_id") val groupId: Int? = null,
    val name: String? = null,
    val active: Boolean? = null,
    val time: AssetTimeDto? = null,
    val speed: String? = null, // "42 km/h", cadena formateada por el servidor, no numérica
    @SerialName("engine_status") val engineStatus: String? = null,
    val status: AssetStatusDto? = null,
    val coordinates: AssetCoordinatesDto? = null // null si el equipo nunca reportó
)

@Serializable
data class AssetTimeDto(
    val timestamp: Long? = null,
    val formatted: String? = null
)

@Serializable
data class AssetStatusDto(
    val type: String? = null, // offline | online | ack | engine | blocked
    val title: String? = null, // ya traducido por el servidor, se muestra tal cual
    val color: String? = null // hex, la app no define su propia paleta de estados
)

@Serializable
data class AssetCoordinatesDto(
    val lat: Double? = null,
    val lng: Double? = null
)

/**
 * Envoltorio de paginación por cursor. El contrato no muestra este sobre, así que se asume
 * el formato estándar de Laravel (backend del proyecto). Verificar contra la respuesta real
 * antes de usarlo en el sprint del mapa.
 */
@Serializable
data class AssetsPageDto(
    val data: List<AssetDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("prev_cursor") val prevCursor: String? = null,
    @SerialName("per_page") val perPage: Int? = null
)
