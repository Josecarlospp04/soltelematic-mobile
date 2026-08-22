package pe.soltelematic.mobile.data.repository

import pe.soltelematic.mobile.core.network.ApiCallExecutor
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.core.storage.TokenStorage
import pe.soltelematic.mobile.data.mapper.toDomain
import pe.soltelematic.mobile.data.remote.api.AuthApi
import pe.soltelematic.mobile.domain.model.ServerConfig
import pe.soltelematic.mobile.domain.model.User
import pe.soltelematic.mobile.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val tokenStorage: TokenStorage,
    private val apiCallExecutor: ApiCallExecutor
) : AuthRepository {

    override suspend fun login(email: String, password: String): ApiResult<Unit> =
        when (val result = apiCallExecutor.execute { authApi.login(email, password) }) {
            is ApiResult.Success -> {
                tokenStorage.saveTokens(result.data.accessToken, result.data.refreshToken)
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
        }

    override suspend fun getCurrentUser(): ApiResult<User> =
        when (val result = apiCallExecutor.execute { authApi.getUser() }) {
            is ApiResult.Success -> {
                val user = result.data.toDomain()
                user.id?.let { tokenStorage.saveUserId(it) }
                ApiResult.Success(user)
            }
            is ApiResult.Error -> result
        }

    override suspend fun getServerConfig(): ApiResult<ServerConfig> =
        when (val result = apiCallExecutor.execute { authApi.getServerConfig() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Error -> result
        }

    override fun hasStoredSession(): Boolean = tokenStorage.hasTokens()

    override fun getStoredUserId(): Int? = tokenStorage.getUserId()

    override fun logout() = tokenStorage.clearTokens()
}
