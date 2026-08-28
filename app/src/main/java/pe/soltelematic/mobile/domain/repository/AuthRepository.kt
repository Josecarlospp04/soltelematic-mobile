package pe.soltelematic.mobile.domain.repository

import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.ServerConfig
import pe.soltelematic.mobile.domain.model.User

interface AuthRepository {
    suspend fun login(email: String, password: String): ApiResult<Unit>
    suspend fun getCurrentUser(): ApiResult<User>
    suspend fun getServerConfig(): ApiResult<ServerConfig>

    /**
     * status 0 (correo no encontrado) y 1 (éxito) se tratan igual: ApiResult.Success(Unit) en
     * ambos casos, incluyendo cuando el servidor los envuelve en un HTTP 422 (ver
     * AuthRepositoryImpl -- confirmado con curl -i que el 422 trae el mismo body {status,
     * message}). Solo se propaga ApiResult.Error si de verdad no se sabe qué pasó con la
     * petición (red/timeout/5xx/422 con body ilegible), nunca por el contenido del body -- así
     * el caller no puede, ni por accidente, mostrar algo distinto según si el correo existe.
     */
    suspend fun forgotPassword(email: String): ApiResult<Unit>
    fun hasStoredSession(): Boolean

    /** Id persistido junto con la sesión (ver AuthRepositoryImpl.getCurrentUser); null si /user nunca respondió con éxito. */
    fun getStoredUserId(): Int?
    fun logout()
}
