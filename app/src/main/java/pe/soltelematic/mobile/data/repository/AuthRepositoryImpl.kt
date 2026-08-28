package pe.soltelematic.mobile.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import pe.soltelematic.mobile.core.network.ApiCallExecutor
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.core.storage.TokenStorage
import pe.soltelematic.mobile.data.mapper.toDomain
import pe.soltelematic.mobile.data.remote.api.AuthApi
import pe.soltelematic.mobile.data.remote.dto.ForgotPasswordResponseDto
import pe.soltelematic.mobile.debug.logForgotPasswordResult
import pe.soltelematic.mobile.domain.model.ServerConfig
import pe.soltelematic.mobile.domain.model.User
import pe.soltelematic.mobile.domain.repository.AuthRepository
import retrofit2.HttpException

class AuthRepositoryImpl(
    private val authApi: AuthApi,
    // Cliente "pelado" (qualifier REFRESH, ver NetworkModule): forgotPassword se llama sin
    // sesión, nunca debe intentar mandar un Bearer inexistente.
    private val unauthenticatedAuthApi: AuthApi,
    private val tokenStorage: TokenStorage,
    private val apiCallExecutor: ApiCallExecutor,
    private val json: Json
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

    // No pasa por apiCallExecutor.execute en el camino feliz: el servidor devuelve 422 (no 200)
    // tanto para "correo no encontrado" como, según lo observado en producción, en otros casos
    // con el mismo body {status,message} -- ver bug reportado con curl -i. apiCallExecutor
    // trataría cualquier 422 como error y perdería el body real (lo parsea como ErrorEnvelopeDto,
    // que ni siquiera tiene el campo "status"). Por eso se captura la HttpException aquí y se
    // reintenta leer el body como ForgotPasswordResponseDto antes de decidir. Los demás códigos/
    // excepciones (401/5xx/timeout/sin conexión/422 con body ilegible) sí deben seguir siendo
    // error, y para esos se reusa el mapeo general pasando la MISMA excepción ya capturada a
    // apiCallExecutor (nunca se repite la llamada de red).
    override suspend fun forgotPassword(email: String): ApiResult<Unit> =
        try {
            val response = unauthenticatedAuthApi.forgotPassword(email)
            logForgotPasswordResult(response.status, response.message)
            ApiResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 422) {
                val forgotResponse = decodeForgotPasswordError(e)
                if (forgotResponse != null) {
                    logForgotPasswordResult(forgotResponse.status, forgotResponse.message)
                    ApiResult.Success(Unit)
                } else {
                    ApiResult.Error(ApiError.Http(422, null))
                }
            } else {
                apiCallExecutor.execute<Unit> { throw e }
            }
        } catch (e: Exception) {
            apiCallExecutor.execute<Unit> { throw e }
        }

    private fun decodeForgotPasswordError(e: HttpException): ForgotPasswordResponseDto? {
        val raw = e.response()?.errorBody()?.string() ?: return null
        return runCatching { json.decodeFromString(ForgotPasswordResponseDto.serializer(), raw) }.getOrNull()
    }

    override fun hasStoredSession(): Boolean = tokenStorage.hasTokens()

    override fun getStoredUserId(): Int? = tokenStorage.getUserId()

    override fun logout() = tokenStorage.clearTokens()
}
