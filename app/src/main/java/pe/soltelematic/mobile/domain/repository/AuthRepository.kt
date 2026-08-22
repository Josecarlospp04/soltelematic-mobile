package pe.soltelematic.mobile.domain.repository

import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.ServerConfig
import pe.soltelematic.mobile.domain.model.User

interface AuthRepository {
    suspend fun login(email: String, password: String): ApiResult<Unit>
    suspend fun getCurrentUser(): ApiResult<User>
    suspend fun getServerConfig(): ApiResult<ServerConfig>
    fun hasStoredSession(): Boolean

    /** Id persistido junto con la sesión (ver AuthRepositoryImpl.getCurrentUser); null si /user nunca respondió con éxito. */
    fun getStoredUserId(): Int?
    fun logout()
}
