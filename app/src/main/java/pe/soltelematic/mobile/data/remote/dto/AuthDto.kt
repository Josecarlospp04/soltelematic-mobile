package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Estos 4 campos son toda la razón de ser de la respuesta: si falta alguno, el login está roto. */
@Serializable
data class TokenResponseDto(
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String
)

/**
 * status=1 éxito, status=0 correo no encontrado -- la UI trata ambos igual (ver
 * AuthRepositoryImpl.forgotPassword) para no revelar si un correo está registrado. message solo
 * se usa para el log de debug.
 */
@Serializable
data class ForgotPasswordResponseDto(
    val status: Int,
    val message: String? = null
)
