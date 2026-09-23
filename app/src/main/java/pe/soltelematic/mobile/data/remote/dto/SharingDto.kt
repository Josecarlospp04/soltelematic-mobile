package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body de POST clientlite/sharing, forma verificada contra el servidor real vía curl (parche 5).
 * expiration_by siempre "duration" desde la app -- es la única opción que implementa la app,
 * aunque el servidor podría aceptar otras. duration es un ENTERO EN MINUTOS, no segundos.
 */
@Serializable
data class SharingCreateRequestDto(
    val devices: List<Int>,
    @SerialName("expiration_by") val expirationBy: String,
    val duration: Int
)

/**
 * Forma de un enlace de ubicación compartida, verificada contra el servidor real vía curl
 * (parche 5) -- misma forma plana en la respuesta de POST clientlite/sharing y en cada elemento
 * de SharingListResponseDto.data (GET clientlite/sharing). El campo "url" SE IGNORA A PROPÓSITO
 * al mapear a dominio: el servidor lo arma con url() de Laravel a partir del host de la propia
 * petición y puede devolver "localhost" -- la app reconstruye el enlace ella misma con
 * ServerRootUrl + hash (ver SharingMapper.toDomain). Se declara igual acá solo para documentar
 * la forma real del payload, nunca se lee.
 *
 * expirationDate llega en DOS formatos DISTINTOS según el endpoint, confirmado vía curl:
 * - POST clientlite/sharing (crear): ISO 8601 con microsegundos y "Z", ej.
 *   "2026-09-18T14:04:04.056566Z" -- instante UTC real.
 * - GET clientlite/sharing (listar): "yyyy-MM-dd HH:mm:ss", ej. "2026-09-19 04:02:20" -- sin
 *   zona, hora LOCAL del servidor (Perú).
 * El mapper (ver SharingMapper.toInstantOrNull) detecta cuál es cuál por la presencia de un
 * sufijo de zona y parsea cada uno con el criterio que le corresponde -- NO asumas un solo
 * formato acá ni "arregles" uno de los dos casos sin el otro.
 */
@Serializable
data class SharingDto(
    val status: Int? = null,
    val id: Int? = null,
    val name: String? = null,
    val hash: String? = null,
    val url: String? = null,
    val active: Boolean? = null,
    @SerialName("expiration_date") val expirationDate: String? = null,
    val devices: List<Int>? = null
)

/** GET clientlite/sharing, forma verificada contra el servidor real vía curl (parche 5). */
@Serializable
data class SharingListResponseDto(
    val status: Int? = null,
    val data: List<SharingDto> = emptyList()
)

/**
 * Respuesta de DELETE clientlite/sharing/{id}, confirmada contra el servidor real vía curl:
 * {"status":1,"id":5} -- mismo criterio que DeleteGeofenceResponseDto: ningún campo se usa, el
 * HTTP 200 ya es la señal de éxito, se declara el DTO solo porque el body no está vacío y el
 * conversor de Retrofit necesita deserializarlo a algo.
 */
@Serializable
data class SharingDeleteResponseDto(
    val status: Int? = null,
    val id: Int? = null
)
