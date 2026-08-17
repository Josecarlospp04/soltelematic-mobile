package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/** No vi la forma completa de /user; solo id es esencial (es la identidad), el resto tolera ausencia. */
@Serializable
data class UserDto(
    val id: Int,
    val name: String? = null,
    val email: String? = null
)
