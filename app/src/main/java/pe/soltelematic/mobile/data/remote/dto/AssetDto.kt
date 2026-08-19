package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * /devices/map, forma verificada contra el servidor real (Sprint 1, Paso 0). Todo salvo id es
 * nullable: GPSWOX omite campos según la configuración de cada unidad.
 */
@Serializable
data class AssetDto(
    val id: Int,
    @SerialName("group_id") val groupId: Int? = null,
    val name: String? = null,
    val active: Boolean? = null,
    val time: AssetTimeDto? = null,
    val speed: AssetSpeedDto? = null,
    // null = sin sensor de ignición · false = apagado · true = encendido. Tres estados, no dos.
    @SerialName("engine_status") val engineStatus: Boolean? = null,
    val status: AssetStatusDto? = null,
    val icon: AssetIconDto? = null,
    val coordinates: AssetCoordinatesDto? = null,
    val tail: AssetTailDto? = null
)

@Serializable
data class AssetTimeDto(
    val timestamp: Long? = null,
    val formatted: String? = null // dd-MM-yyyy HH:mm:ss, no ISO 8601
)

/** value es el número para lógica; human ya viene formateado por el servidor, solo para mostrar. */
@Serializable
data class AssetSpeedDto(
    val value: Double? = null,
    val unit: String? = null,
    val human: String? = null
)

@Serializable
data class AssetStatusDto(
    val type: String? = null, // offline | online | ack | engine | blocked
    val title: String? = null, // ya traducido por el servidor, se muestra tal cual
    val color: String? = null // hex, la app no define su propia paleta de estados
)

/**
 * course = rumbo en grados, para rotar el marcador al dibujarlo (no para hornear variantes
 * del bitmap). url puede venir con host localhost -- ver IconUrlResolver -- o null cuando la
 * unidad no tiene PNG propio y solo trae color, caso en el que se dibuja un marcador plano.
 */
@Serializable
data class AssetIconDto(
    val width: Int? = null,
    val height: Int? = null,
    val url: String? = null,
    val color: String? = null,
    val course: Float? = null
)

// lat/lng llegan como cadenas, no números; se convierten en el mapper con toDoubleOrNull().
@Serializable
data class AssetCoordinatesDto(
    val lat: String? = null,
    val lng: String? = null
)

@Serializable
data class AssetTailDto(
    val coordinates: List<AssetCoordinatesDto> = emptyList(),
    val color: String? = null
)

/**
 * Envoltorio de paginación por cursor, formato estándar de Laravel (verificado). Con la flota
 * real (15 unidades) next_cursor nunca aparece, pero el repositorio pagina mientras no sea null.
 */
@Serializable
data class AssetsPageDto(
    val data: List<AssetDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null
)
